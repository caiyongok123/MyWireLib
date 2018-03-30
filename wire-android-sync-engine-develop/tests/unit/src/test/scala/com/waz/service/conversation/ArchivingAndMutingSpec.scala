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
package com.waz.service.conversation

import java.util.Date

import com.waz._
import com.waz.model.AssetMetaData.Image
import com.waz.model.AssetMetaData.Image.Tag.Preview
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GenericContent.{ImageAsset, Knock}
import com.waz.model.GenericMessage.TextMessage
import com.waz.model._
import com.waz.testutils._
import com.waz.threading.CancellableFuture
import com.waz.threading.CancellableFuture.CancelException
import com.waz.znet.ZNetClient.EmptyClient
import com.waz.znet.{Request, Response}
import org.scalatest._
import org.scalatest.matchers.Matcher
import org.threeten.bp.Instant

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


@Ignore class ArchivingAndMutingSpec extends FeatureSpec with Matchers with BeforeAndAfter with OptionValues with RobolectricTests with RobolectricUtils { test =>

  lazy val selfUser = UserData("self user")
  lazy val user1 = UserData("user 1")
  
  lazy val conv = ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group, lastEventTime = Instant.now)

  var service: MockZMessaging = _
  var messageSync = None: Option[MessageId]

  before {
    messageSync = None

    val account = new MockAccountManager() {
      override lazy val netClient = new EmptyClient {
        override def apply[A](r: Request[A]): CancellableFuture[Response] = {
          CancellableFuture.failed(new CancelException(""))
        }
      }
    }

    service = new MockZMessaging(account, selfUserId = selfUser.id) {

      override lazy val sync = new EmptySyncService {
        override def postMessage(id: MessageId, conv: ConvId, time: Instant) = {
          messageSync = Some(id)
          super.postMessage(id, conv, time)
        }
      }

      insertUsers(Seq(selfUser, user1))
      insertConv(conv)
    }
  }

  def getConv(id: ConvId) = Await.result(service.convsContent.convById(id), 1.second)

  feature("Archiving a conversation") {
    scenario("Straightforward archiving and unarchiving a conversation") {
      conv should not(beArchived)
      setArchived(archived = true) should beArchived
      withConvFromStore { _ should beArchived }

      setArchived(archived = false) should not(beArchived)
      withConvFromStore { _ should not(beArchived) }
    }
  }

  feature("Muting a conversation") {
    scenario("Straightforward muting and unmuting a conversation") {
      conv should not(beMuted)
      setMuted(muted = true) should beMuted
      withConvFromStore { _ should beMuted }

      setMuted(muted = false) should not(beMuted)
      withConvFromStore { _ should not(beMuted) }
    }
  }

  feature("Archiving conversations that self left") {
    scenario("If self leaves, an unmuted conversation becomes archived and stays archived") {
      archive()
      memberLeave(user = selfUser.id)
      delay()
      withConvFromStore { _ should beArchived }
    }
  }

  feature("Automatic unarchiving of unmuted conversations") {
    scenario("On receiving a text message, an unmuted archived conversation is unarchived") {
      archive()
      sendTextMessage()
      withConvFromStore { _ should not(beArchived) }
    }

    scenario("On receiving a generic text message, an unmuted archived conversation is unarchived") {
      archive()
      sendGenericTextMessage()
      withConvFromStore { _ should not(beArchived) }
    }

    scenario("On receiving an image message, an unmuted archived conversation is unarchived") {
      archive()
      sendImageMessage()
      withConvFromStore { _ should not(beArchived) }
    }

    scenario("On receiving a generic image message, an unmuted archived conversation is unarchived") {
      archive()
      sendGenericImageMessage()
      withConvFromStore { _ should not(beArchived) }
    }

    scenario("If a member joins, an archived conversation is unarchived") {
      archive()
      memberJoin()
      withConvFromStore { _ should not(beArchived) }
    }

    scenario("If a member leaves, an archived conversation is unarchived") {
      archive()
      memberLeave()
      withConvFromStore { _ should not(beArchived) }
    }

    scenario("On renaming a conversation, it is unarchived") {
      archive()
      renameConv()
      withConvFromStore { _ should not(beArchived) }
    }

    scenario("On receiving a knock, an archived conversation is unarchived") {
      archive()
      knock()
      withConvFromStore { _ should not(beArchived) }
    }

    scenario("On member updates, an archived conversation stays archived") {
      archive()
      memberUpdate()
      delay()
      withConvFromStore { _ should beArchived }
    }
  }

  feature("Automatic unarchiving of muted conversations") {
    scenario("On receiving a text message, a muted archived conversation stays archived") {
      archiveAndMute()
      sendTextMessage()
      delay()
      withConvFromStore { _ should beArchived }
    }

    scenario("On receiving an image message, a muted archived conversation stays archived") {
      archiveAndMute()
      sendImageMessage()
      delay()
      withConvFromStore { _ should beArchived }
    }

    scenario("If a member joins, a muted archived conversation stays archived") {
      archiveAndMute()
      memberJoin()
      delay()
      withConvFromStore { _ should beArchived }
    }

    scenario("If a member leaves, a muted archived conversation stays archived") {
      archiveAndMute()
      memberLeave()
      delay()
      withConvFromStore { _ should beArchived }
    }

    scenario("On renaming a muted conversation, it stays archived") {
      archiveAndMute()
      renameConv()
      delay()
      withConvFromStore { _ should beArchived }
    }

    scenario("On receiving a knock, a muted archived conversation is unarchived") {
      archiveAndMute()
      knock()
      withConvFromStore { _ should not(beArchived) }
    }

    scenario("On receiving a generic knock, a muted archived conversation is unarchived") {
      archiveAndMute()
      genericKnock()
      withConvFromStore { _ should not(beArchived) }
    }

  }

  feature("Not unarchiving on old events") {
    scenario("On receiving an old text message, an unmuted archived conversation stays archived") {
      archive()
      sendTextMessage(old = true)
      delay()
      withConvFromStore { _ should beArchived }
    }

    scenario("On receiving an old image message, an unmuted archived conversation stays archived") {
      archive()
      sendImageMessage(old = true)
      delay()
      withConvFromStore { _ should beArchived }
    }

    scenario("If a member joined before archiving, an unmuted archived conversation stays archived") {
      archive()
      memberJoin(old = true)
      delay()
      withConvFromStore { _ should beArchived }
    }

    scenario("If a member left before archiving, an unmuted archived conversation stays archived") {
      archive()
      memberLeave(old = true)
      delay()
      withConvFromStore { _ should beArchived }
    }

    scenario("On receiving an old knock, an unmuted archived conversation stays archived") {
      archive()
      knock(old = true)
      delay()
      withConvFromStore { _ should beArchived }
    }

    scenario("If an unmuted conversation was renamed before archiving, it stays archived") {
      archive()
      renameConv(old = true)
      delay()
      withConvFromStore { _ should beArchived }
    }

  }

  def archiveAndMute(): ConversationData = {
    archive()
    setMuted(muted = true)
  }
  def archive(): ConversationData = setArchived(archived = true)

  def delay(): Unit = awaitUi(50.millis)

  def setArchived(archived: Boolean): ConversationData = awaitSuccess(service.convsUi.setConversationArchived(conv.id, archived = archived))
  def setMuted(muted: Boolean): ConversationData = awaitSuccess(service.convsUi.setConversationMuted(conv.id, muted = muted))

  def awaitSuccess[T](op: => Future[Option[T]]): T = Await.result(op, 1.second).value
  def withConvFromStore(op: ConversationData => Unit): Unit = withDelay { op(getConv(conv.id).value) }

  def memberJoin(old: Boolean = false): Unit = service.dispatchEvent(MemberJoinEvent(conv.remoteId, date(old), user1.id, Seq()))
  def memberLeave(old: Boolean = false, user: UserId = user1.id): Unit = service.dispatchEvent(MemberLeaveEvent(conv.remoteId, date(old), user, Seq(user)))
  def memberUpdate(): Unit = service.dispatchEvent(MemberUpdateEvent(conv.remoteId, new Date, selfUser.id, ConversationState()))

  def renameConv(old: Boolean = false): Unit = service.dispatchEvent(RenameConversationEvent(conv.remoteId, date(old), user1.id, ""))

  def knock(old: Boolean = false): Unit = service.dispatchEvent(GenericMessageEvent(conv.remoteId, date(old), user1.id, GenericMessage(Uid(), Knock())))
  def genericKnock[T](old: Boolean = false): Unit = service.dispatchEvent(GenericMessageEvent(conv.remoteId, if (old) date(old) else date(old), user1.id, GenericMessage(Uid(), Knock())))

  def sendTextMessage[T](old: Boolean = false): Unit = service.dispatchEvent(textMessageEvent(Uid(), conv.remoteId, if (old) date(old) else date(old), user1.id, "hello"))
  def sendGenericTextMessage[T](old: Boolean = false): Unit = service.dispatchEvent(GenericMessageEvent(conv.remoteId, if (old) date(old) else date(old), user1.id, TextMessage("hello", Map.empty)))

  def sendImageMessage[T](old: Boolean = false): Unit =
    service.dispatchEvent(GenericAssetEvent(conv.remoteId, date(old), selfUser.id, GenericMessage(Uid(), ImageAsset(AssetData(metaData = Some(Image(Dim2(0, 0), Preview))))), RAssetId(), None))

  def sendGenericImageMessage[T](old: Boolean = false): Unit =
    service.dispatchEvent(GenericMessageEvent(conv.remoteId, if (old) date(old) else date(old), user1.id, GenericMessage(Uid(), ImageAsset(AssetData(metaData = Some(Image(Dim2(0, 0), Preview)))))))

  def date(old: Boolean): Date = if (old) new Date(System.currentTimeMillis() - 100000) else new Date

  def beArchived: Matcher[ConversationData] = be(true) compose (_.archived)
  def beMuted: Matcher[ConversationData] = be(true) compose (_.muted)

  implicit val timeout = 500.millis
}
