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
package com.waz.service.connections

import java.util.Date

import com.waz._
import com.waz.api.Message
import com.waz.content.ZmsDatabase
import com.waz.model.ConversationData.{ConversationDataDao, ConversationType}
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.UserData.ConnectionStatus._
import com.waz.model._
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.testutils.Matchers._
import com.waz.testutils.{EmptySyncService, MockZMessaging}
import com.waz.threading.Threading
import org.robolectric.Robolectric
import org.scalacheck.{Gen, Shrink}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.wrappers.DB
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

@Ignore class ConnectionsServiceSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils with GeneratorDrivenPropertyChecks { test =>
  implicit lazy val dispatcher = Threading.Background

  def storage: ZmsDatabase = service.db
  implicit def db: DB = storage.dbHelper.getWritableDatabase

  val timeout = 5.seconds

  var service: MockZMessaging = _

  lazy val selfUser = UserData("FromUser").copy(connection = ConnectionStatus.Self)

  lazy val otherUser = UserData("ToUser")
  var syncConnection = None: Option[UserId]
  var syncRequestedUsers = Set[UserId]()


  before {
    syncConnection = None
    syncRequestedUsers = Set()

    service = new MockZMessaging(selfUserId = selfUser.id) {
      override lazy val sync = new EmptySyncService {
        override def postConnection(user: UserId, name: String, message: String) = {
          syncConnection = Some(user)
          super.postConnection(user, name, message)
        }

        override def syncUsers(ids: UserId*) = {
          syncRequestedUsers ++= ids
          super.syncUsers(ids: _*)
        }
      }
    }
  }

  after {
    awaitStorage()
    Await.result(storage.close(), 10.seconds)
    Robolectric.application.getDatabasePath(storage.dbHelper.getDatabaseName).delete()
  }

  def awaitStorage() = Await.result(storage { _ => }, 25.seconds) // wait for storage operations to complete

  def insertConv(conv: ConversationData) = service.insertConv(conv)
  def getConv(id: ConvId) = service.getConv(id)
  def getConv(id: RConvId) = service.getConv(id)
  def listConvs = service.listConvs

  def addMembers(conv: ConvId, users: UserId*) = Await.result(service.membersStorage.add(conv, users), 1.second)
  def removeMember(conv: ConvId, user: UserId) = Await.result(service.membersStorage.remove(conv, user), 1.second)
  def listActiveMembers(conv: ConvId) = Await.result(service.membersStorage.getActiveUsers(conv), 1.second).toList
  def listMembers(conv: ConvId) = Await.result(service.membersStorage.getByConv(conv), 1.second).toList

  def insertUsers(users: Seq[UserData]) = users map { user => Await.result(service.usersStorage.addOrOverwrite(user), 1.second) }
  def insertUser(user: UserData) = Await.result(service.usersStorage.addOrOverwrite(user), 1.second)
  def getUser(id: UserId) = service.getUser(id)
  
  feature("Handle events") {
    scenario("Handle UserConnectionEvent update to-user connectionStatus") {

      insertUser(otherUser)
      val conversationId = RConvId()

      service.dispatchEvent(UserConnectionEvent(conversationId, selfUser.id, otherUser.id, Some("Hello from Test"), PendingFromUser, new Date()))
      Thread.sleep(500)

      getUser(otherUser.id).map(_.connection) should be(Some(PendingFromUser))

      service.dispatchEvent(UserConnectionEvent(conversationId, selfUser.id, otherUser.id, Some("Hello from Test"), Cancelled, new Date()))
      Thread.sleep(500)

      getUser(otherUser.id).map(_.connection) should be(Some(Cancelled))

      service.dispatchEvent(UserConnectionEvent(conversationId, selfUser.id, otherUser.id, Some("Hello from Test"), Accepted, new Date()))
      Thread.sleep(500)

      getUser(otherUser.id).map(_.connection) should be(Some(Accepted))

      service.dispatchEvent(UserConnectionEvent(conversationId, selfUser.id, otherUser.id, Some("Hello from Test"), Ignored, new Date()))
      Thread.sleep(500)

      getUser(otherUser.id) map (_.connection) should be(Some(Ignored))

      service.dispatchEvent(UserConnectionEvent(conversationId, selfUser.id, otherUser.id, Some("Hello from Test"), Blocked, new Date()))
      Thread.sleep(500)

      getUser(otherUser.id) map (_.connection) should be(Some(Blocked))
    }

    scenario("Handle ContactJoinEvent") {
      val userId = UserId()
      Await.ready(service.dispatch(ContactJoinEvent(userId, "test name")), 1.second)
      syncRequestedUsers shouldEqual Set(userId)
      service.usersStorage.get(userId) should eventually(beMatching {
        case Some(u: UserData) if u.id == userId && u.name == "test name" => true
      })
    }
  }

  feature("creating Connection") {

    implicit val disableShrink: Shrink[Seq[Event]] = Shrink(s => Stream.empty)

    scenario("connect to user") {
      insertUsers(Seq(selfUser, otherUser))

      val conv = Await.result(service.connection.connectToUser(otherUser.id, "Hello from test", "user"), timeout).get

      conv.convType shouldEqual ConversationType.WaitForConnection
      conv.creator shouldEqual selfUser.id
      conv.id.str shouldEqual otherUser.id.str

      getUser(otherUser.id).map(_.connection) should be(Some(PendingFromUser))

      getConv(conv.id) shouldEqual Some(conv)
      listMembers(conv.id).map(_.userId).toSet shouldEqual Set(selfUser.id, otherUser.id)
      listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, otherUser.id)

      syncConnection shouldEqual Some(otherUser.id)

      val msg = service.lastMessage(conv.id)
      msg should be('defined)
      msg.map(_.msgType) shouldEqual Some(Message.Type.CONNECT_REQUEST)
      msg.map(_.userId) shouldEqual Some(selfUser.id)
      msg.map(_.contentString) shouldEqual Some("Hello from test")
      msg.flatMap(_.recipient) shouldEqual Some(otherUser.id)
      msg.flatMap(_.name) shouldEqual Some("user")
    }

    scenario("connect to already connected user") {
      insertUsers(Seq(selfUser, otherUser.copy(connection = ConnectionStatus.Accepted)))

      val conversation = ConversationData(ConvId(otherUser.id.str), RConvId(), None, selfUser.id, ConversationData.ConversationType.OneToOne).withFreshSearchKey

      insertConv(conversation)
      addMembers(conversation.id, selfUser.id, otherUser.id)

      val convo = Await.result(service.connection.connectToUser(otherUser.id, "Hello from test", "user"), timeout).get

      convo should be(conversation)
      syncConnection should be('empty)
    }

    scenario("connect to self user") {
      insertUsers(Seq(selfUser, otherUser))
      val conversation = ConversationData(ConvId(selfUser.id.str), RConvId(), None, selfUser.id, ConversationData.ConversationType.Self)
      insertConv(conversation)
      addMembers(conversation.id, selfUser.id)

      val conv = Await.result(service.connection.connectToUser(selfUser.id, "Hello from test", "user"), timeout)
      conv should be ('defined)
      conv.get.convType shouldEqual ConversationType.Self
      syncConnection should be('empty)
    }

    scenario("request connect, receive events in random order") {
      insertUsers(Seq(selfUser, otherUser))

      val rconvId = RConvId()
      val events = Seq[Event](
        CreateConversationEvent(rconvId, new Date, selfUser.id, ConversationResponse(ConversationData(ConvId(), rconvId, Some("test"), selfUser.id, ConversationType.WaitForConnection), Seq())),
        ConnectRequestEvent(rconvId, new Date, selfUser.id, "test", otherUser.id, "test", None),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("test"), ConnectionStatus.PendingFromUser, new Date)
      )

      val eventsGen = Gen.choose(1, 2).map(_ => Random.shuffle(events))

      forAll(eventsGen, MinSuccessful(25)) { (evs: Seq[Event]) =>
        ConversationDataDao.deleteAll
        insertUser(otherUser.copy(connection = ConnectionStatus.Unconnected))

        val conv = Await.result(service.connection.connectToUser(otherUser.id, "Hello from test", "user"), timeout).get
        service.dispatch(evs:_*)

        Thread.sleep(100)

        listConvs should have size 1
        val conv1 = getConv(conv.id)
        conv1 should be('defined)

        conv1.get.convType shouldEqual ConversationType.WaitForConnection
        conv1.get.remoteId shouldEqual rconvId
        conv1.get.id.str shouldEqual otherUser.id.str
        getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.PendingFromUser)
        listMembers(conv.id).map(_.userId).toSet shouldEqual Set(selfUser.id, otherUser.id)
      }
    }

    scenario("request connect and accept") {
      insertUsers(Seq(selfUser, otherUser))

      val rconvId = RConvId()
      val time = System.currentTimeMillis()
      val events = Seq[Event](
        MemberJoinEvent(rconvId, new Date(time + 4), selfUser.id, Seq(otherUser.id)),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, None, ConnectionStatus.Accepted, new Date(time + 5))
      )

      ConversationDataDao.deleteAll
      insertUser(otherUser.copy(connection = ConnectionStatus.Unconnected))

      val conv = Await.result(service.connection.connectToUser(otherUser.id, "Hello from test", "user"), timeout).get
      service.dispatch(events:_*)

      withDelay {
        listConvs should have size 1
        val conv1 = getConv(conv.id)
        conv1 should be('defined)

        conv1.get.convType shouldEqual ConversationType.OneToOne
        conv1.get.remoteId shouldEqual rconvId
        conv1.get.id.str shouldEqual otherUser.id.str
        getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Accepted)
        listMembers(conv.id).map(_.userId).toSet shouldEqual Set(selfUser.id, otherUser.id)
      }
    }

    scenario("create hidden conversation when receiving create conv event on other device") {
      insertUsers(Seq(selfUser, otherUser))

      val rconvId = RConvId()
      service.dispatchEvent(CreateConversationEvent(rconvId, new Date, selfUser.id, ConversationResponse(ConversationData(ConvId(), rconvId, Some("test"), selfUser.id, ConversationType.WaitForConnection), Seq())))

      withDelay {
        listConvs should have size 1
        getConv(rconvId).map(_.convType) shouldEqual Some(ConversationType.WaitForConnection)
        getConv(rconvId).map(_.hidden) shouldEqual Some(true)
      }

      service.dispatchEvent(ConnectRequestEvent(rconvId, new Date, selfUser.id, "test", otherUser.id, "test", None))

      withDelay {
        listConvs should have size 1
        getConv(rconvId).map(_.convType) shouldEqual Some(ConversationType.WaitForConnection)
      }
    }

    scenario("receive events on other device") {
      insertUsers(Seq(selfUser, otherUser))

      val rconvId = RConvId()
      val time = System.currentTimeMillis()
      val events = Seq[Event](
        CreateConversationEvent(rconvId, new Date(time), selfUser.id, ConversationResponse(ConversationData(ConvId(), rconvId, Some("test"), selfUser.id, ConversationType.WaitForConnection), Seq())),
        ConnectRequestEvent(rconvId, new Date(time + 1), selfUser.id, "test", otherUser.id, "test", None),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("test"), ConnectionStatus.PendingFromUser, new Date(time + 1))
      )

      service.dispatch(events:_*)

      withDelay {
        listConvs should have size 1

        val conv = listConvs.head
        conv.convType shouldEqual ConversationType.WaitForConnection
        conv.remoteId shouldEqual rconvId
        conv.id.str shouldEqual otherUser.id.str
        getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.PendingFromUser)
        listMembers(conv.id).map(_.userId).toSet shouldEqual Set(selfUser.id, otherUser.id)
      }
    }

    scenario("receive accept events on other device") {
      insertUsers(Seq(selfUser, otherUser))

      val rconvId = RConvId()
      val time = System.currentTimeMillis()
      val events = Seq[Event](
        CreateConversationEvent(rconvId, new Date(time), selfUser.id, ConversationResponse(ConversationData(ConvId(), rconvId, Some("test"), selfUser.id, ConversationType.WaitForConnection), Seq())),
        ConnectRequestEvent(rconvId, new Date(time + 1), selfUser.id, "test", otherUser.id, "test", None),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("test"), ConnectionStatus.PendingFromUser, new Date(time + 2)),
        MemberJoinEvent(rconvId, new Date(time + 3), selfUser.id, Seq(otherUser.id)),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, None, ConnectionStatus.Accepted, new Date(time + 4))
      )

      service.dispatch(events:_*)

      withDelay {
        listConvs should have size 1

        val conv = listConvs.head
        conv.convType shouldEqual ConversationType.OneToOne
        conv.remoteId shouldEqual rconvId
        conv.id.str shouldEqual otherUser.id.str
        getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Accepted)
        listMembers(conv.id).map(_.userId).toSet shouldEqual Set(selfUser.id, otherUser.id)
      }
    }

    scenario("receive accept events in random order on other device") { // TODO: should we support that ?
      insertUsers(Seq(selfUser, otherUser))

      val rconvId = RConvId()
      val time = System.currentTimeMillis()
      val events = Seq[Event](
        CreateConversationEvent(rconvId, new Date(time), selfUser.id, ConversationResponse(ConversationData(ConvId(), rconvId, Some("test"), selfUser.id, ConversationType.WaitForConnection), Seq())),
        ConnectRequestEvent(rconvId, new Date(time + 1), selfUser.id, "test", otherUser.id, "test", None),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("test"), ConnectionStatus.PendingFromUser, new Date(time + 2)),
        MemberJoinEvent(rconvId, new Date(time + 3), selfUser.id, Seq(otherUser.id)),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, None, ConnectionStatus.Accepted, new Date(time + 4))
      )

      val eventsGen = Gen.choose(1, 2).map(_ => Random.shuffle(events))

      forAll(eventsGen, MinSuccessful(25)) { (evs: Seq[Event]) =>
        ConversationDataDao.deleteAll
        insertUser(otherUser.copy(connection = ConnectionStatus.Unconnected))

        service.dispatch(evs:_*)

        withDelay {
          listConvs should have size 1

          val conv = listConvs.head
          conv.convType shouldEqual ConversationType.OneToOne
          conv.remoteId shouldEqual rconvId
          conv.id.str shouldEqual otherUser.id.str
          getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Accepted)
          listMembers(conv.id).map(_.userId).toSet shouldEqual Set(selfUser.id, otherUser.id)
        }
      }
    }
  }
}
