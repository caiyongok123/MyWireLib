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
package com.waz.mocked

import java.util.concurrent.atomic.AtomicLong

import com.waz.api.ApiSpec
import com.waz.api.impl.ErrorResponse
import com.waz.mocked.MockBackend._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GenericContent.{Cleared, LastRead, Reaction}
import com.waz.model.GenericMessage.TextMessage
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.model.otr.{Client, ClientId}
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.sync.client.ConversationsClient.ConversationResponse.ConversationsResult
import com.waz.sync.client.MessagesClient.OtrMessage
import com.waz.sync.client.OtrClient.{ClientMismatch, MessageResponse}
import com.waz.sync.client.PushNotification
import com.waz.sync.client.UserSearchClient.UserSearchEntry
import com.waz.threading.CancellableFuture
import com.waz.utils._
import com.waz.znet.AuthenticationManager._
import com.waz.znet.ZNetClient._
import com.wire.cryptobox.PreKey
import org.threeten.bp.Instant

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

trait MockBackend extends MockedClient with MockedWebSocket with MockedGcm with MockNetwork with MockOrlop { self: ApiSpec =>
  lazy val selfUserId = UserId()
  val users = new mutable.HashMap[UserId, UserInfo]

  val connections = new mutable.HashMap[UserId, UserConnectionEvent]

  val members = new mutable.HashMap[RConvId, Seq[ConversationMemberData]]
  val conversations = new mutable.HashMap[RConvId, ConversationData]
  val events = new mutable.HashMap[RConvId, Seq[ConversationEvent]]
  val searchResults = new mutable.HashMap[SearchQuery, Seq[UserSearchEntry]].withDefaultValue(Seq.empty)
  val convTimeCounter = new mutable.HashMap[RConvId, AtomicLong]

  val otrClients = new mutable.HashMap[ClientId, Client]

  val clientDelay = 100.millis

  object DefaultPushBehaviour {
    implicit def Implicit: PushBehaviour = PushBehaviour.Push
  }

  def resetMockedBackend(): Unit = {
    Seq(users, connections, members, conversations, events, searchResults, convTimeCounter) foreach (_.clear())
    members(RConvId(selfUserId.str)) = Seq(ConversationMemberData(selfUserId, ConvId(selfUserId.str)), ConversationMemberData(selfUserId, ConvId(selfUserId.str)))
    notifications = Vector.empty
  }

  conversations(RConvId(selfUserId.str)) = ConversationData(ConvId(selfUserId.str), RConvId(selfUserId.str), None, selfUserId, ConversationType.Self)
  members(RConvId(selfUserId.str)) = Seq(ConversationMemberData(selfUserId, ConvId(selfUserId.str)))

  val conversationsPager = Pager[ConversationResponse, RConvId] {
    case (Some(start), limit) => conversations.values.toSeq.sortBy(_.remoteId).dropWhile(_.remoteId.str <= start.str).take(limit) map convResponse
    case (None, limit) => conversations.values.toSeq.sortBy(_.remoteId).take(limit) map convResponse
  }

  def convResponse(conv: ConversationData) = ConversationResponse(conv, members(conv.remoteId))

  def addConnection(uid: UserId = UserId(), time: Timeline = DefaultTimeline)(implicit p: PushBehaviour) = {
    addOutgoingConnectionRequest(uid, time)
    acceptConnection(uid, time)
    connections(uid)
  }

  def autoConnect(uid: UserId = UserId(), time: Timeline = DefaultTimeline)(implicit p: PushBehaviour) = {
    val convId = RConvId()
    val conn = UserConnectionEvent(convId, selfUserId, uid, None, ConnectionStatus.Accepted, time.next())
    connections(uid) = conn
    conversations(convId) = ConversationData(ConvId(convId.str), convId, None, selfUserId, ConversationType.OneToOne)
    members(convId) = Seq(ConversationMemberData(selfUserId, ConvId(convId.str)), ConversationMemberData(uid, ConvId(convId.str)))
    addNotification(PushNotification(Uid(), Seq(conn)))
    addEvent(MemberJoinEvent(convId, time.next(), selfUserId, Seq(selfUserId)))
    conn
  }

  def addOutgoingConnectionRequest(uid: UserId = UserId(), time: Timeline = DefaultTimeline)(implicit p: PushBehaviour) = {
    val convId = RConvId()
    val conn = UserConnectionEvent(convId, selfUserId, uid, Some(s"Hello, let's connect $uid"), ConnectionStatus.PendingFromUser, time.next())
    val timestamp = time.next()
    connections(uid) = conn
    conversations(convId) = ConversationData(ConvId(convId.str), convId, None, selfUserId, ConversationType.WaitForConnection)
    members(convId) = Seq(ConversationMemberData(selfUserId, ConvId(convId.str)), ConversationMemberData(uid, ConvId(convId.str)))
    addNotification(PushNotification(Uid(), Seq(conn)))
    addEvent(MemberJoinEvent(convId, timestamp, selfUserId, Seq(selfUserId)))
    addEvent(ConnectRequestEvent(convId, timestamp, selfUserId, "request", uid, "name", None))
    conn
  }

  def addIncomingConnectionRequest(uid: UserId = UserId(), convId: RConvId = RConvId(), time: Timeline = DefaultTimeline)(implicit p: PushBehaviour) = {
    val conn = UserConnectionEvent(convId, selfUserId, uid, Some(s"Hello, let's connect $uid"), ConnectionStatus.PendingFromOther, time.next())
    connections(uid) = conn
    addNotification(PushNotification(Uid(), Seq(conn)))
    conn
  }

  def acceptConnection(uid: UserId, time: Timeline = DefaultTimeline)(implicit p: PushBehaviour) = {
    connections.get(uid) foreach { conn =>
      val now = time.next()
      connections(uid) = conn.copy(status = ConnectionStatus.Accepted, lastUpdated = now)
      conversations(conn.convId) = conversations(conn.convId).copy(convType = ConversationType.OneToOne)
      addNotification(connections(uid))
      addEvent(MemberJoinEvent(conn.convId, now, conn.from, Seq(uid)))
    }
  }

  def addGroupConversation(ms: Seq[UserId], time: Timeline = DefaultTimeline, id: RConvId = RConvId(), name: Option[String] = None, creator: UserId = selfUserId)(implicit p: PushBehaviour) = {
    val conv = ConversationData(ConvId(id.str), id, name, creator, ConversationType.Group)
    conversations(id) = conv
    members(id) = (selfUserId +: creator +: ms).toSet.map((u: UserId) => ConversationMemberData(u, ConvId(id.str))).toSeq
    val creationTime = time.next()
    addNotification(PushNotification(Uid(), Seq(CreateConversationEvent(id, creationTime, creator, ConversationResponse(conv, members(id))))))
    conv
  }

  def removeUsersFromGroupConversation(users: Seq[UserId], id: RConvId, from: UserId = selfUserId, time: Timeline = SystemTimeline)(implicit p: PushBehaviour): MemberLeaveEvent = {
    val evt = MemberLeaveEvent(id, time.next(), from, users)
    members(id) = members(id).filter(u => users.contains(u.userId))
    addEvent(evt)
  }

  def leaveGroupConversation(id: RConvId, from: UserId = selfUserId, time: Timeline = SystemTimeline)(implicit p: PushBehaviour): Seq[ConversationEvent] =
    Seq(
      removeUsersFromGroupConversation(Seq(selfUserId), id, from, time),
      archiveConversation(id))

  def addMembersToGroupConversation(convId: RConvId, ms: Seq[UserId], time: Timeline = DefaultTimeline, from: UserId = selfUserId)(implicit p: PushBehaviour) = {
    val conv = conversations(convId)
    val prev = members(convId)
    assert(prev.exists(_.userId == from), "only current member can add ppl to conv")
    members(convId) = prev ++ ms.map(id => ConversationMemberData(id, conv.id))
    val evt = MemberJoinEvent(convId, time.next(), from, ms)
    addEvent(evt)
    conv
  }

  def addLikingEvent(convId: RConvId, messageId: MessageId, action: Liking.Action, time: Timeline = DefaultTimeline, from: UserId = selfUserId)(implicit p: PushBehaviour) =
    addEvent(GenericMessageEvent(convId, time.next(), from, GenericMessage(Uid(), Reaction(messageId, action))))

  def addEvent(ev: ConversationEvent)(implicit p: PushBehaviour): ev.type = {
    require (ev match {
      case _: MemberUpdateEvent => true
      case _: GenericMessageEvent => true
      case _                    => ev.time.getTime >= events.get (ev.convId).flatMap (_.lastOption).fold (0L) (_.time.getTime)
    }, ev.toString)

    events(ev.convId) = events.getOrElse(ev.convId, Nil) :+ ev
    updateLastEvent(ev.convId)
    addNotification(PushNotification(Uid(), Seq(ev), transient = false))
    ev
  }

  def markAsRead(convId: RConvId, time: Timeline = SystemTimeline)(implicit p: PushBehaviour): GenericMessageEvent = {
    val t = time.next()
    returning(GenericMessageEvent(RConvId(selfUserId.str), t, selfUserId, GenericMessage(Uid(), LastRead(convId, t.instant)))) { lastRead =>
      addEvent(lastRead)
      conversations(convId) = conversations(convId).copy(lastRead = t.instant)
    }
  }

  def clearConversation(convId: RConvId, time: Timeline = SystemTimeline)(implicit p: PushBehaviour): GenericMessageEvent = {
    val t = conversations(convId).lastEventTime
    returning(GenericMessageEvent(RConvId(selfUserId.str), time.next(), selfUserId, GenericMessage(Uid(), Cleared(convId, t)))) { clearEvent =>
      addEvent(clearEvent)
      addEvent(MemberUpdateEvent(convId, time.next(), selfUserId, ConversationState(archived = Some(true), archiveTime = Some(t))))
      conversations(convId) = conversations(convId).copy(cleared = t, archived = true, archiveTime = t, lastRead = clearEvent.time.instant)
    }
  }

  def archiveConversation(convId: RConvId, time: Timeline = SystemTimeline)(implicit p: PushBehaviour): MemberUpdateEvent = {
    val t = conversations(convId).lastEventTime
    returning(MemberUpdateEvent(convId, time.next(), selfUserId, ConversationState(archived = Some(true), archiveTime = Some(t)))) { archiveEvent =>
      addEvent(archiveEvent)
      conversations(convId) = conversations(convId).copy(archived = true, archiveTime = t, lastRead = archiveEvent.time.instant)
    }
  }

  def addSearchResults(query: SearchQuery, numConnected: Int = 0, numUnconnected: Int = 0, numUnknownConnected: Int = 0, numUnknownUnconnected: Int = 0, numBlocked: Int = 0) = {
    def entry(connected: Option[Boolean], blocked: Boolean)(id: UserId) = UserSearchEntry(id, "dummy", None, Handle("dummy"))

    val (connected, unknown) = connections.keys.take(numConnected + numUnknownConnected) .splitAt(numConnected)
    val (unconnected, blockedAndUnknown) = Seq.fill(numUnconnected + numUnknownUnconnected + numBlocked) { UserId() } .splitAt(numUnconnected)
    val (blocked, unknown2) = blockedAndUnknown .splitAt(numBlocked)
    val results = connected.map(entry(Some(true), blocked = false)) ++
      unconnected.map(entry(Some(false), blocked = false)) ++
      blocked.map(entry(None, blocked = true)) ++
      (unknown ++ unknown2).map(entry(None, blocked = false))

    searchResults.put(query, results.toSeq)
  }

  def updateLastEvent(convId: RConvId) = synchronized {
    events.get(convId).flatMap(evs => if (evs.isEmpty) None else Some(evs.maxBy(_.time.getTime))).foreach { ev =>
      val counter = convTimeCounter.getOrElseUpdate(convId, new AtomicLong(ev.time.getTime + 1))
      if (counter.get <= ev.time.getTime) counter.set(ev.time.getTime + 1)

      conversations.get(convId).foreach { conv =>
        conversations(convId) = conv.copy(lastEventTime = ev.time.instant)
      }
    }
  }

  def addMessageEvents(convId: RConvId, from: UserId = selfUserId, count: Int = Random.nextInt(200), msg: String = "test message", timeline: Option[Timeline] = None)(implicit p: PushBehaviour) = {
    val current = events.getOrElse(convId, Nil)
    val time = timeline.getOrElse(if (current.isEmpty) DefaultTimeline else ArtificialTimeline(Instant.ofEpochMilli(current.map(_.time).max.getTime)))
    val es = for (i <- 1 to count) yield {
      GenericMessageEvent(convId, time.next(), from, TextMessage(s"$msg $i", Map.empty))
    }
    events(convId) = current ++: es
    addNotification(PushNotification(Uid(), es))
    updateLastEvent(convId)
    es
  }

  def getRandomConnections(count: Int): Seq[UserId] = {
    if (connections.isEmpty || count == 0) Nil
    else connections.valuesIterator.toIndexedSeq(Random.nextInt(connections.size)).to +: getRandomConnections(count - 1)
  }

  def getUser(id: UserId) = {
    val u = users.getOrElseUpdate(id, UserInfo(id, Some(Random.nextLong().toHexString), Some(Random.nextInt(5)), Some(EmailAddress("email@test.com")), None, None))
    if (id == selfUserId || connections.get(id).exists(c => UserData.ConnectionStatus.isConnected(c.status))) u
    else u.copy(email = None, phone = None)
  }

  override def loadSelf(): ErrorOrResponse[UserInfo] = CancellableFuture.delayed(clientDelay)(Right(getUser(selfUserId)))

  override def updateSelf(info: UserInfo): ErrorOrResponse[Unit] = returning(CancellableFuture.delayed(clientDelay) {
    users += info.id -> info
    Right(())
  })(_.onSuccess { case _ =>
    addNotification(UserUpdateEvent(info))(PushBehaviour.NoPush)
  })

  override def register(user: AccountData, name: String, accentId: Option[Int]) = {
    val info = UserInfo(selfUserId, Option(name), accentId, user.email, user.phone, None)
    users += selfUserId -> info
    CancellableFuture.successful(Right((info, Some(Cookie("cookie")))))
  }

  override def loadUsers(ids: Seq[UserId]): ErrorOrResponse[IndexedSeq[UserInfo]] = CancellableFuture.delayed(clientDelay)(Right(ids.map(getUser).toIndexedSeq))
  override def loadConnections(start: Option[UserId], pageSize: Int): ErrorOrResponse[Seq[UserConnectionEvent]] = CancellableFuture.delayed(clientDelay)(Right(connections.values.toSeq))
  override def loadConversations(start: Option[RConvId], limit: Int): ErrorOrResponse[ConversationsResult] = {
    val convs = conversationsPager(start, limit)
    val hasMore = conversationsPager(start, limit + 1).size > convs.size
    CancellableFuture.delayed(clientDelay)(Right(ConversationsResult(convs, hasMore)))
  }

  override def loadConversations(ids: Seq[RConvId]): ErrorOrResponse[Seq[ConversationResponse]] = CancellableFuture.delayed(clientDelay)(Right(
    ids.flatMap(id => conversations.get(id).flatMap(c => members.get(id).map(m => ConversationResponse(c, m))))))

  override def graphSearch(query: SearchQuery, limit: Int): ErrorOrResponse[Seq[UserSearchEntry]] = CancellableFuture.delayed(clientDelay)(Right(searchResults(query).take(limit)))

  override def postConversation(users: Seq[UserId], name: Option[String]): ErrorOrResponse[ConversationResponse] = {
    val conv = addGroupConversation(users, SystemTimeline, name = name)(PushBehaviour.NoPush)
    CancellableFuture.delayed(clientDelay)(Right(ConversationResponse(conv, users map { id => ConversationMemberData(id, conv.id) })))
  }

  override def postName(convId: RConvId, name: String): ErrorOrResponse[Option[RenameConversationEvent]] = {
    conversations.get(convId) match {
      case Some(conv) =>
        val event = RenameConversationEvent(convId, SystemTimeline.next(), selfUserId, name)
        conversations(convId) = conv.copy(name = Some(name))
        addEvent(event)(PushBehaviour.NoPush)

        CancellableFuture.delayed(clientDelay)(Right(Some(event)))
      case None =>
        CancellableFuture.delayed(clientDelay)(Left(ErrorResponse(404, "no conv found", "no-conv-found")))
    }
  }

  override def postMemberJoin(conv: RConvId, newMembers: Seq[UserId]): ErrorOrResponse[Option[MemberJoinEvent]] =
    if ((members(conv).map(_.userId) ++ newMembers).distinct.size > 128)
      CancellableFuture.successful(Left(ErrorResponse(403, "Maximum number of members per conversation reached", "too-many-members")))
    else {
      members.update(conv, members(conv) ++ newMembers.map(id => ConversationMemberData(id, ConvId(conv.str))))
      val evt = MemberJoinEvent(conv, SystemTimeline.next(), selfUserId, newMembers)
      addEvent(evt)(PushBehaviour.NoPush)
      CancellableFuture.delayed(clientDelay)(Right(Some(evt)))
    }

  override def postMemberLeave(conv: RConvId, member: UserId): ErrorOrResponse[Option[MemberLeaveEvent]] = CancellableFuture.delayed(clientDelay)(Right(Some(removeUsersFromGroupConversation(Seq(member), conv, selfUserId, time = SystemTimeline)(PushBehaviour.NoPush))))

  override def postMessage(convId: RConvId, msg: OtrMessage, ignoreMissing: Boolean): ErrorOrResponse[MessageResponse] =
    CancellableFuture.delayed(clientDelay)(Right(MessageResponse.Success(ClientMismatch(SystemTimeline.next()))))

  override def postConversationState(convId: RConvId, updated: ConversationState): ErrorOrResponse[Boolean] = {
    val previous = conversations(convId)
    conversations.update(convId, previous.copy(
      archived = updated.archived.getOrElse(previous.archived),
      archiveTime = updated.archiveTime.getOrElse(previous.archiveTime),
      muted = updated.muted.getOrElse(previous.muted),
      muteTime = updated.muteTime.getOrElse(previous.muteTime)))

    addEvent(MemberUpdateEvent(convId, SystemTimeline.next(), selfUserId, updated))(PushBehaviour.NoPush)
    CancellableFuture.delayed(clientDelay)(Right(true))
  }


  override def loadOtrClients(): ErrorOrResponse[Seq[Client]] =
    CancellableFuture.delayed(clientDelay)(Right(otrClients.values.toSeq))

  override def postOtrClient(userId: AccountId, client: Client, lastKey: PreKey, keys: Seq[PreKey], password: Option[String]): ErrorOrResponse[Client] = {
    otrClients(client.id) = client
    CancellableFuture.delayed(clientDelay)(Right(client))
  }
}

object MockBackend {

  trait Pager[A, B] extends ((Option[B], Int) => Seq[A])
  
  object Pager {

    def fromSeq[A, B](source: => Seq[A], selector: A => B) = new Pager[A, B] {
      override def apply(start: Option[B], limit: Int) = {
        val items = source
        start.fold(items.take(limit)){ start => items.dropWhile(i => selector(i) != start).slice(1, limit + 1) }
      }
    }

    def apply[A, B](source: PartialFunction[(Option[B], Int), Seq[A]]) = new Pager[A, B] {
      override def apply(start: Option[B], limit: Int): Seq[A] = source((start, limit))
    }
  }
  
  val DefaultTimeline = Timeline.sometimeInThePast
}

trait MockNetwork {

  var networkDelay = Duration.Zero
  var networkError = Option.empty[ErrorResponse]

  def success[A](resp: A): ErrorOrResponse[A] = CancellableFuture.delayed(networkDelay) {
    networkError match {
      case Some(err) => Left(err)
      case None => Right(resp)
    }
  }

  def failure(resp: ErrorResponse) = CancellableFuture.delayed(networkDelay) {
    networkError match {
      case Some(err) => Left(err)
      case None => Left(resp)
    }
  }
}

trait MockOrlop { self: ApiSpec with MockedClient with MockedWebSocket with MockedGcm with MockNetwork =>

  def selfUserId: UserId

  @volatile var notifications = Vector.empty[PushNotification]

  override def loadLastNotification(): ErrorOrResponse[Option[PushNotification]] = CancellableFuture.successful(Right(notifications.lastOption))

  def addNotification(events: Event*)(implicit p: PushBehaviour): Uid = addNotification(PushNotification(Uid(), events))

  def addNotification(n: PushNotification)(implicit p: PushBehaviour): Uid = {
    notifications = notifications :+ n

    p match {
      case PushBehaviour.Push => if (! push(n)) pushGcm(n, selfUserId)
      case PushBehaviour.NoPush =>
    }

    n.id
  }
}
