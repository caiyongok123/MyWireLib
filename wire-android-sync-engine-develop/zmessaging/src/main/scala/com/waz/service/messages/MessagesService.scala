/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service.messages

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Message.{Status, Type}
import com.waz.api.{ErrorResponse, Message}
import com.waz.content.{EditHistoryStorage, MessagesStorage}
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GenericContent._
import com.waz.model.{MessageId, _}
import com.waz.service.ZMessaging.clock
import com.waz.service._
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.otr.VerificationStateUpdater.{ClientUnverified, MemberAdded, VerificationChange}
import com.waz.sync.SyncServiceHandle
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.RichFuture.traverseSequential
import com.waz.utils._
import com.waz.utils.events.EventContext
import org.threeten.bp.Instant.now
import org.threeten.bp.{Duration, Instant}

import scala.collection.breakOut
import scala.concurrent.Future
import scala.concurrent.Future.{successful, traverse}
import scala.util.Success

trait MessagesService {
  def addTextMessage(convId: ConvId, content: String, mentions: Map[UserId, String] = Map.empty): Future[MessageData]
  def addKnockMessage(convId: ConvId, selfUserId: UserId): Future[MessageData]
  def addAssetMessage(convId: ConvId, asset: AssetData): Future[MessageData]
  def addLocationMessage(convId: ConvId, content: Location): Future[MessageData]

  def addMissedCallMessage(rConvId: RConvId, from: UserId, time: Instant): Future[Option[MessageData]]
  def addMissedCallMessage(convId: ConvId, from: UserId, time: Instant): Future[Option[MessageData]]
  def addSuccessfulCallMessage(convId: ConvId, from: UserId, time: Instant, duration: Duration): Future[Option[MessageData]]

  def addConnectRequestMessage(convId: ConvId, fromUser: UserId, toUser: UserId, message: String, name: String, fromSync: Boolean = false): Future[MessageData]
  def addConversationStartMessage(convId: ConvId, creator: UserId, users: Set[UserId], name: Option[String]): Future[MessageData]
  def addMemberJoinMessage(convId: ConvId, creator: UserId, users: Set[UserId], firstMessage: Boolean = false): Future[Option[MessageData]]
  def addMemberLeaveMessage(convId: ConvId, selfUserId: UserId, user: UserId): Future[Any]
  def addRenameConversationMessage(convId: ConvId, selfUserId: UserId, name: String, needsSyncing: Boolean = true): Future[Option[MessageData]]
  def addHistoryLostMessages(cs: Seq[ConversationData], selfUserId: UserId): Future[Set[MessageData]]

  def addDeviceStartMessages(convs: Seq[ConversationData], selfUserId: UserId): Future[Set[MessageData]]
  def addOtrVerifiedMessage(convId: ConvId): Future[Option[MessageData]]
  def addOtrUnverifiedMessage(convId: ConvId, users: Seq[UserId], change: VerificationChange): Future[Option[MessageData]]

  def retryMessageSending(conv: ConvId, msgId: MessageId): Future[Option[SyncId]]
  def updateMessageState(convId: ConvId, messageId: MessageId, state: Message.Status): Future[Option[MessageData]]

  def recallMessage(convId: ConvId, msgId: MessageId, userId: UserId, systemMsgId: MessageId = MessageId(), time: Instant = now(clock), state: Message.Status = Message.Status.PENDING): Future[Option[MessageData]]
  def applyMessageEdit(convId: ConvId, userId: UserId, time: Instant, gm: GenericMessage): Future[Option[MessageData]]

  def removeLocalMemberJoinMessage(convId: ConvId, users: Set[UserId]): Future[Any]

  def messageSent(convId: ConvId, msg: MessageData): Future[Option[MessageData]]
  def messageDeliveryFailed(convId: ConvId, msg: MessageData, error: ErrorResponse): Future[Option[MessageData]]
}

class MessagesServiceImpl(selfUserId: UserId,
                          storage:    MessagesStorage,
                          updater:    MessagesContentUpdater,
                          edits:      EditHistoryStorage,
                          convs:      ConversationsContentUpdater,
                          network:    NetworkModeService,
                          sync:       SyncServiceHandle) extends MessagesService {
  import Threading.Implicits.Background
  private implicit val ec = EventContext.Global

  override def recallMessage(convId: ConvId, msgId: MessageId, userId: UserId, systemMsgId: MessageId = MessageId(), time: Instant = now(clock), state: Message.Status = Message.Status.PENDING) =
    updater.getMessage(msgId) flatMap {
      case Some(msg) if msg.convId != convId =>
        error(s"can not recall message belonging to other conversation: $msg, requested by $userId")
        Future successful None
      case Some(msg) if msg.canRecall(convId, userId) =>
        updater.deleteOnUserRequest(Seq(msgId)) flatMap { _ =>
          val recall = MessageData(systemMsgId, convId, Message.Type.RECALLED, time = msg.time, editTime = time max msg.time, userId = userId, state = state, protos = Seq(GenericMessage(systemMsgId.uid, MsgRecall(msgId))))
          if (userId == selfUserId) Future successful Some(recall) // don't save system message for self user
          else updater.addMessage(recall)
        }
      case Some(msg) if msg.isEphemeral =>
        // ephemeral message expired on other device, or on receiver side
        updater.deleteOnUserRequest(Seq(msgId)) map { _ => None }
      case msg =>
        warn(s"can not recall $msg, requested by $userId")
        Future successful None
    }

  override def applyMessageEdit(convId: ConvId, userId: UserId, time: Instant, gm: GenericMessage) = Serialized.future("applyMessageEdit", convId) {

    def findLatestUpdate(id: MessageId): Future[Option[MessageData]] =
      updater.getMessage(id) flatMap {
        case Some(msg) => Future successful Some(msg)
        case None =>
          edits.get(id) flatMap {
            case Some(EditHistory(_, updated, _)) => findLatestUpdate(updated)
            case None => Future successful None
          }
      }

    gm match {
      case GenericMessage(id, MsgEdit(msgId, Text(text, mentions, links))) =>

        def applyEdit(msg: MessageData) = for {
            _ <- edits.insert(EditHistory(msg.id, MessageId(id.str), time))
            (tpe, ct) = MessageData.messageContent(text, mentions, links, weblinkEnabled = true)
            res <- updater.addMessage(MessageData(MessageId(id.str), convId, tpe, userId, ct, Seq(gm), time = msg.time, localTime = msg.localTime, editTime = time))
            _ <- updater.deleteOnUserRequest(Seq(msg.id))
        } yield res

        updater.getMessage(msgId) flatMap {
          case Some(msg) if msg.userId == userId && msg.convId == convId =>
            verbose(s"got edit event for msg: $msg")
            applyEdit(msg)
          case _ =>
            // original message was already deleted, let's check if it was already updated
            edits.get(msgId) flatMap {
              case Some(EditHistory(_, updated, editTime)) if editTime <= time =>
                verbose(s"message $msgId has already been updated, discarding later update")
                Future successful None

              case Some(EditHistory(_, updated, editTime)) =>
                // this happens if message has already been edited locally,
                // but that edit is actually newer than currently received one, so we should revert it
                // we always use only the oldest edit for given message (as each update changes the message id)
                verbose(s"message $msgId has already been updated, will overwrite new message")
                findLatestUpdate(updated) flatMap {
                  case Some(msg) => applyEdit(msg)
                  case None =>
                    error(s"Previously updated message was not found for: $gm")
                    Future successful None
                }

              case None =>
                verbose(s"didn't find the original message for edit: $gm")
                Future successful None
            }
        }
      case _ =>
        error(s"invalid message for applyMessageEdit: $gm")
        Future successful None
    }
  }

  override def addTextMessage(convId: ConvId, content: String, mentions: Map[UserId, String] = Map.empty) = {
    verbose(s"addTextMessage($convId, ${content.take(4)}, $mentions)")
    val (tpe, ct) = MessageData.messageContent(content, mentions, weblinkEnabled = true)
    verbose(s"parsed content: $ct")
    val id = MessageId()
    updater.addLocalMessage(MessageData(id, convId, tpe, selfUserId, ct, protos = Seq(GenericMessage(id.uid, Text(content, mentions, Nil))))) // FIXME: links
  }

  override def addLocationMessage(convId: ConvId, content: Location) = {
    verbose(s"addLocationMessage($convId, $content)")
    val id = MessageId()
    updater.addLocalMessage(MessageData(id, convId, Type.LOCATION, selfUserId, protos = Seq(GenericMessage(id.uid, content))))
  }

  override def addAssetMessage(convId: ConvId, asset: AssetData) = {
    val tpe = asset match {
      case AssetData.IsImage() => Message.Type.ASSET
      case AssetData.IsVideo() => Message.Type.VIDEO_ASSET
      case AssetData.IsAudio() => Message.Type.AUDIO_ASSET
      case _                   => Message.Type.ANY_ASSET
    }
    val mid = MessageId(asset.id.str)
    updater.addLocalMessage(MessageData(mid, convId, tpe, selfUserId, protos = Seq(GenericMessage(mid.uid, Asset(asset)))))
  }

  override def addRenameConversationMessage(convId: ConvId, from: UserId, name: String, needsSyncing: Boolean = true) = {
    def update(msg: MessageData) = msg.copy(name = Some(name))
    def create = MessageData(MessageId(), convId, Message.Type.RENAME, from, name = Some(name))
    if (needsSyncing) updater.updateOrCreateLocalMessage(convId, Message.Type.RENAME, update, create)
    else updater.addLocalMessage(create, state = Message.Status.DELIVERED).map(Some(_))
  }

  override def addConnectRequestMessage(convId: ConvId, fromUser: UserId, toUser: UserId, message: String, name: String, fromSync: Boolean = false) = {
    val msg = MessageData(
      MessageId(), convId, Message.Type.CONNECT_REQUEST, fromUser, content = MessageData.textContent(message), name = Some(name), recipient = Some(toUser),
      time = if (fromSync) MessageData.UnknownInstant else now(clock))

    if (fromSync) storage.insert(msg) else updater.addLocalMessage(msg)
  }

  override def addKnockMessage(convId: ConvId, selfUserId: UserId) = {
    debug(s"addKnockMessage($convId, $selfUserId)")
    updater.addLocalMessage(MessageData(MessageId(), convId, Message.Type.KNOCK, selfUserId))
  }

  override def addDeviceStartMessages(convs: Seq[ConversationData], selfUserId: UserId): Future[Set[MessageData]] =
    Serialized.future('addDeviceStartMessages)(traverse(convs filter isGroupOrOneToOne) { conv =>
      storage.getLastMessage(conv.id) map {
        case None =>    Some(MessageData(MessageId(), conv.id, Message.Type.STARTED_USING_DEVICE, selfUserId, time = Instant.EPOCH))
        case Some(_) => None
      }
    } flatMap { msgs =>
      storage.insertAll(msgs.flatten)
    })

  private def isGroupOrOneToOne(conv: ConversationData) = conv.convType == ConversationType.Group || conv.convType == ConversationType.OneToOne

  def addHistoryLostMessages(cs: Seq[ConversationData], selfUserId: UserId): Future[Set[MessageData]] = {
    // TODO: those messages should include information about what was actually changed
    traverseSequential(cs) { conv =>
      storage.getLastMessage(conv.id) map {
        case Some(msg) if msg.msgType != Message.Type.STARTED_USING_DEVICE =>
          Some(MessageData(MessageId(), conv.id, Message.Type.HISTORY_LOST, selfUserId, time = msg.time.plusMillis(1)))
        case _ =>
          // conversation has no messages or has STARTED_USING_DEVICE msg,
          // it means that conv was just created and we don't need to add history lost msg
          None
      }
    } flatMap { msgs =>
      storage.insertAll(msgs.flatten) flatMap { added =>
        // mark messages read if there is no other unread messages
        val times: Map[ConvId, Instant] = added.map(m => m.convId -> m.time) (breakOut)
        convs.storage.updateAll2(times.keys, { c =>
          val t = times(c.id)
          if (c.lastRead.toEpochMilli == t.toEpochMilli - 1) c.copy(lastRead = t) else c
        }) map { _ => added }
      }
    }
  }

  def addConversationStartMessage(convId: ConvId, creator: UserId, users: Set[UserId], name: Option[String]) = {
    updater.addLocalSentMessage(MessageData(MessageId(), convId, Message.Type.MEMBER_JOIN, creator, name = name, members = users, firstMessage = true))
  }

  override def addMemberJoinMessage(convId: ConvId, creator: UserId, users: Set[UserId], firstMessage: Boolean = false) = {
    verbose(s"addMemberJoinMessage($convId, $creator, $users)")

    def updateOrCreate(added: Set[UserId]) = {
      def update(msg: MessageData) = msg.copy(members = msg.members ++ added)
      def create = MessageData(MessageId(), convId, Message.Type.MEMBER_JOIN, creator, members = added, firstMessage = firstMessage)
      updater.updateOrCreateLocalMessage(convId, Message.Type.MEMBER_JOIN, update, create)
    }

    // check if we have local leave message with same users
    storage.lastLocalMessage(convId, Message.Type.MEMBER_LEAVE) flatMap {
      case Some(msg) if users.exists(msg.members) =>
        val toRemove = msg.members -- users
        val toAdd = users -- msg.members
        if (toRemove.isEmpty) updater.deleteMessage(msg) // FIXME: race condition
        else updater.updateMessage(msg.id)(_.copy(members = toRemove)) // FIXME: race condition

        if (toAdd.isEmpty) successful(None) else updateOrCreate(toAdd)
      case _ =>
        updateOrCreate(users)
    }
  }

  def removeLocalMemberJoinMessage(convId: ConvId, users: Set[UserId]): Future[Any] = {
    storage.lastLocalMessage(convId, Message.Type.MEMBER_JOIN) flatMap {
      case Some(msg) =>
        val members = msg.members -- users
        if (members.isEmpty) {
          updater.deleteMessage(msg)
        } else {
          updater.updateMessage(msg.id) { _.copy(members = members) } // FIXME: possible race condition with addMemberJoinMessage or sync
        }
      case _ =>
        warn("removeLocalMemberJoinMessage: no local join message found")
        CancellableFuture.successful(())
    }
  }

  override def addMemberLeaveMessage(convId: ConvId, selfUserId: UserId, user: UserId) = {
    // check if we have local join message with this user and just remove him from the list
    storage.lastLocalMessage(convId, Message.Type.MEMBER_JOIN) flatMap {
      case Some(msg) if msg.members == Set(user) => updater.deleteMessage(msg) // FIXME: race condition
      case Some(msg) if msg.members(user) => updater.updateMessage(msg.id)(_.copy(members = msg.members - user)) // FIXME: race condition
      case _ =>
        // check for local MemberLeave message before creating new one
        def newMessage = MessageData(MessageId(), convId, Message.Type.MEMBER_LEAVE, selfUserId, members = Set(user))
        def update(msg: MessageData) = msg.copy(members = msg.members + user)
        updater.updateOrCreateLocalMessage(convId, Message.Type.MEMBER_LEAVE, update, newMessage)
    }
  }

  override def addOtrVerifiedMessage(convId: ConvId) =
    storage.getLastMessage(convId) flatMap {
      case Some(msg) if msg.msgType == Message.Type.OTR_UNVERIFIED || msg.msgType == Message.Type.OTR_DEVICE_ADDED ||  msg.msgType == Message.Type.OTR_MEMBER_ADDED =>
        verbose(s"addOtrVerifiedMessage, removing previous message: $msg")
        storage.delete(msg.id) map { _ => None }
      case _ =>
        updater.addLocalMessage(MessageData(MessageId(), convId, Message.Type.OTR_VERIFIED, selfUserId), Status.SENT) map { Some(_) }
    }

  override def addOtrUnverifiedMessage(convId: ConvId, users: Seq[UserId], change: VerificationChange): Future[Option[MessageData]] = {
    val msgType = change match {
      case ClientUnverified => Message.Type.OTR_UNVERIFIED
      case MemberAdded => Message.Type.OTR_MEMBER_ADDED
      case _ => Message.Type.OTR_DEVICE_ADDED
    }
    verbose(s"addOtrUnverifiedMessage($convId, $users, $change), msgType is $msgType")
    updater.addLocalSentMessage(MessageData(MessageId(), convId, msgType, selfUserId, members = users.toSet)) map { Some(_) }
  }

  override def retryMessageSending(conv: ConvId, msgId: MessageId) =
    updater.updateMessage(msgId) { msg =>
      if (msg.state == Status.SENT || msg.state == Status.PENDING) msg
      else msg.copy(state = Status.PENDING)
    } .flatMap {
      case Some(msg) => sync.postMessage(msg.id, conv, msg.editTime) map (Some(_))
      case _ => successful(None)
    }

  def messageSent(convId: ConvId, msg: MessageData): Future[Option[MessageData]] = {
    updater.updateMessage(msg.id) { m => m.copy(state = Message.Status.SENT, expiryTime = m.ephemeral.expiryFromNow()) } andThen {
      case Success(Some(m)) => storage.onMessageSent ! m
    }
  }

  override def addMissedCallMessage(rConvId: RConvId, from: UserId, time: Instant): Future[Option[MessageData]] =
    convs.convByRemoteId(rConvId).flatMap {
      case Some(conv) => addMissedCallMessage(conv.id, from, time)
      case None =>
        warn(s"No conversation found for remote id: $rConvId")
        Future.successful(None)
    }

  override def addMissedCallMessage(convId: ConvId, from: UserId, time: Instant): Future[Option[MessageData]] =
    updater.addMessage(MessageData(MessageId(), convId, Message.Type.MISSED_CALL, from, time = time))

  override def addSuccessfulCallMessage(convId: ConvId, from: UserId, time: Instant, duration: Duration) =
    updater.addMessage(MessageData(MessageId(), convId, Message.Type.SUCCESSFUL_CALL, from, time = time, duration = duration))

  def messageDeliveryFailed(convId: ConvId, msg: MessageData, error: ErrorResponse): Future[Option[MessageData]] =
    updateMessageState(convId, msg.id, Message.Status.FAILED) andThen {
      case Success(Some(m)) => storage.onMessageFailed ! (m, error)
    }

  override def updateMessageState(convId: ConvId, messageId: MessageId, state: Message.Status) =
    updater.updateMessage(messageId) { _.copy(state = state) }

  def markMessageRead(convId: ConvId, id: MessageId) =
    if (!network.isOnlineMode) CancellableFuture.successful(None)
    else
      updater.updateMessage(id) { msg =>
        if (msg.state == Status.FAILED) msg.copy(state = Status.FAILED_READ)
        else msg
      }
}
