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

import com.waz.RobolectricUtils
import com.waz.api.Message
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.testutils.{EmptySyncService, MockZMessaging}
import com.waz.threading.Threading
import org.scalacheck.{Gen, Shrink}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

@Ignore class IncomingConnectionsSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils with GeneratorDrivenPropertyChecks { test =>
  implicit lazy val dispatcher = Threading.Background

  val timeout = 5.seconds

  lazy val selfUser = UserData("FromUser")
  lazy val otherUser = UserData("ToUser")
  var syncConnection = None: Option[UserId]
  var syncUser: Seq[UserId] = Seq()

  lazy val service = new MockZMessaging(selfUserId = selfUser.id) {
    override lazy val sync = new EmptySyncService {
      override def postConnection(user: UserId, name: String, message: String) = {
        syncConnection = Some(user)
        super.postConnection(user, name, message)
      }

      override def syncUsers(ids: UserId*) = {
        syncUser = ids
        super.syncUsers(ids: _*)
      }
    }

    insertUser(selfUser)
  }

  before {
   syncConnection = None
   syncUser = Seq()
  }

  import service._

  def listVisibleConvs = listConvs.filterNot(_.hidden)

  feature("accept connection") {

    implicit val disableShrink: Shrink[Seq[Event]] = Shrink(s => Stream.empty)

    scenario("receive user connection event") {
      insertUsers(Seq(selfUser, otherUser))

      val rconvId = RConvId()
      service.dispatchEvent(UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("Hello from test"), ConnectionStatus.PendingFromOther, new Date))

      withDelay {
        listVisibleConvs should have size 1
        getConv(rconvId).map(_.convType) shouldEqual Some(ConversationType.Incoming)
      }

      val conv = getConv(rconvId)
      conv should be('defined)

      withDelay {
        val msg = lastMessage(conv.get.id)
        msg should be('defined)
        msg.map(_.msgType) shouldEqual Some(Message.Type.CONNECT_REQUEST)
        msg.map(_.userId) shouldEqual Some(otherUser.id)
        msg.map(_.contentString) shouldEqual Some("Hello from test")
        msg.flatMap(_.recipient) shouldEqual Some(selfUser.id)
        syncUser shouldEqual Seq(otherUser.id)

        listMembers(conv.get.id).map(_.userId).toSet shouldEqual Set(selfUser.id, otherUser.id)
      }
    }

    scenario("accept incoming connection") {
      insertUsers(Seq(selfUser, otherUser))

      val rconvId = RConvId()
      service.dispatchEvent(UserConnectionEvent(rconvId, selfUser.id, otherUser.id, None, ConnectionStatus.PendingFromOther, new Date))

      withDelay {
        listVisibleConvs should have size 1
        getConv(rconvId).map(_.convType) shouldEqual Some(ConversationType.Incoming)
      }

      Await.result(service.connection.acceptConnection(otherUser.id), timeout)

      listVisibleConvs should have size 1
      val conv = getConv(rconvId)
      withDelay {
        conv.map(_.convType) shouldEqual Some(ConversationType.OneToOne)
        getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Accepted)
      }

      val msg = lastMessage(conv.get.id)
      msg should be('defined)
      msg.map(_.msgType) shouldEqual Some(Message.Type.MEMBER_JOIN)
      msg.map(_.userId) shouldEqual Some(selfUser.id)

      service.dispatchEvent(MemberJoinEvent(rconvId, new Date, otherUser.id, Seq(selfUser.id)))
      service.dispatchEvent(UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("test"), ConnectionStatus.Accepted, new Date))

      withDelay {
        listVisibleConvs should have size 1
        getConv(rconvId).map(_.convType) shouldEqual Some(ConversationType.OneToOne)
        getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Accepted)
      }
    }

    scenario("accept incoming connection 1") {
      deleteAllConvs()
      insertUsers(Seq(selfUser, otherUser))

      val rconvId = RConvId()
      val time = System.currentTimeMillis()
      val events = Seq[Event](
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("test"), ConnectionStatus.Accepted, new Date(time + 3)),
        MemberJoinEvent(rconvId, new Date(time + 1), otherUser.id, Seq(selfUser.id)),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("test"), ConnectionStatus.Accepted, new Date(time + 2)),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, None, ConnectionStatus.PendingFromOther, new Date(time))
      )

      listVisibleConvs should have size 0

      service.dispatch(events:_*)

      withDelay {
        listVisibleConvs should have size 1

        val conv = listVisibleConvs.head
        conv.convType shouldEqual ConversationType.OneToOne
        conv.remoteId shouldEqual rconvId
        conv.id.str shouldEqual otherUser.id.str
        getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Accepted)
        listMembers(conv.id).map(_.userId).toSet shouldEqual Set(selfUser.id, otherUser.id)
      }
    }

    scenario("accept incoming connection, random events order") {

      val rconvId = RConvId()
      val time = System.currentTimeMillis()
      val events = Seq[Event](
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, None, ConnectionStatus.PendingFromOther, new Date(time)),
        MemberJoinEvent(rconvId, new Date(time + 1), otherUser.id, Seq(selfUser.id)),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("test"), ConnectionStatus.Accepted, new Date(time + 2)),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("test1"), ConnectionStatus.Accepted, new Date(time + 3))
      )

      val eventsGen = Gen.choose(1, 2).map(_ => Random.shuffle(events))

      forAll(eventsGen, MinSuccessful(20)) { (evs: Seq[Event]) =>
        insertUsers(Seq(selfUser, otherUser))
        deleteAllConvs()
        listVisibleConvs should have size 0


        Await.result(service.dispatch(evs:_*), timeout)

        withDelay {
          listVisibleConvs should have size 1

          val conv = listVisibleConvs.head
          conv.remoteId shouldEqual rconvId
          conv.id.str shouldEqual otherUser.id.str
          getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Accepted)
          listMembers(conv.id).map(_.userId).toSet shouldEqual Set(selfUser.id, otherUser.id)
          conv.convType shouldEqual ConversationType.OneToOne
        }
      }
    }
  }

  feature("ignore connection") {

    scenario("ignore incoming connection") {
      insertUsers(Seq(selfUser, otherUser))

      val rconvId = RConvId()
      service.dispatchEvent(UserConnectionEvent(rconvId, selfUser.id, otherUser.id, None, ConnectionStatus.PendingFromOther, new Date))

      withDelay {
        listVisibleConvs should have size 1
        getConv(rconvId).map(_.convType) shouldEqual Some(ConversationType.Incoming)
      }

      Await.result(service.connection.ignoreConnection(otherUser.id), timeout)

      listVisibleConvs should have size 0
      getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Ignored)

      service.dispatchEvent(UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("test"), ConnectionStatus.Ignored, new Date))

      Thread.sleep(200)
      listVisibleConvs should have size 0
      getConv(rconvId).map(_.hidden) shouldEqual Some(true)
      getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Ignored)

      val conv = Await.result(service.convsUi.getOrCreateOneToOneConversation(otherUser.id), timeout)
      conv.convType shouldEqual ConversationType.Incoming
      conv.hidden shouldEqual true
    }

    scenario("ignore incoming connection, random events order") {
      insertUsers(Seq(selfUser, otherUser))

      val rconvId = RConvId()
      val time = System.currentTimeMillis()
      val events = Seq[Event](
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, None, ConnectionStatus.PendingFromOther, new Date(time)),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("test"), ConnectionStatus.Ignored, new Date(time + 1)),
        UserConnectionEvent(rconvId, selfUser.id, otherUser.id, Some("test"), ConnectionStatus.Ignored, new Date(time + 1))
      )

      val eventsGen = Gen.choose(1, 2).map(_ => Random.shuffle(events))

      forAll(eventsGen, MinSuccessful(25)) { (evs: Seq[Event]) =>
        service.deleteAllConvs()
        insertUser(otherUser.copy(connection = ConnectionStatus.Unconnected))

        service.dispatch(evs:_*)

        withDelay {
          listVisibleConvs should have size 0
          getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Ignored)
        }
      }
    }

    scenario("accept previously ignored connection") {
      insertUsers(Seq(selfUser, otherUser.copy(connection = ConnectionStatus.Ignored)))
      insertConv(ConversationData(ConvId(otherUser.id.str), RConvId(), None, selfUser.id, ConversationType.Incoming, hidden = true))

      // accept connection
      val conv1 = Await.result(service.connection.acceptConnection(otherUser.id), timeout)
      conv1.convType shouldEqual ConversationType.OneToOne
      conv1.hidden shouldEqual false

      listVisibleConvs should have size 1

      // TODO: assert messages
    }

    scenario("accept ignored connection without conversation") {
      // TODO
    }
  }

  feature("block connections") {

    scenario("block user") {
      insertUsers(Seq(selfUser, otherUser))
      val conv = ConversationData(ConvId(otherUser.id.str), RConvId(), None, selfUser.id, ConversationType.OneToOne)
      insertConv(conv)

      val blocked = Await.result(service.connection.blockConnection(otherUser.id), timeout)
      blocked.map(_.connection) shouldEqual Some(ConnectionStatus.Blocked)

      getConv(ConvId(otherUser.id.str)).map(_.hidden) shouldEqual Some(true)
      listVisibleConvs should be(empty)
    }

    scenario("handle block user event") {
      insertUsers(Seq(selfUser, otherUser))
      val conv = ConversationData(ConvId(otherUser.id.str), RConvId(), None, selfUser.id, ConversationType.OneToOne)
      insertConv(conv)

      service.dispatchEvent(UserConnectionEvent(conv.remoteId, selfUser.id, otherUser.id, None, ConnectionStatus.Blocked, new Date))
      Thread.sleep(200)

      getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Blocked)
      getConv(ConvId(otherUser.id.str)).map(_.hidden) shouldEqual Some(true)
      listVisibleConvs should be(empty)
    }

    scenario("unblock blocked user") {
      insertUsers(Seq(selfUser, otherUser.copy(connection = ConnectionStatus.Blocked)))
      insertConv(ConversationData(ConvId(otherUser.id.str), RConvId(), None, selfUser.id, ConversationType.OneToOne, hidden = true))

      val conv = Await.result(service.connection.unblockConnection(otherUser.id), timeout)
      conv.convType shouldEqual ConversationType.OneToOne
      conv.hidden shouldEqual false

      listVisibleConvs should have size 1
      getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Accepted)
    }

    scenario("unblock user on event") {
      insertUsers(Seq(selfUser, otherUser.copy(connection = ConnectionStatus.Blocked)))
      val conv = insertConv(ConversationData(ConvId(otherUser.id.str), RConvId(), None, selfUser.id, ConversationType.OneToOne, hidden = true))

      service.dispatchEvent(UserConnectionEvent(conv.remoteId, selfUser.id, otherUser.id, None, ConnectionStatus.Accepted, new Date))
      Thread.sleep(200)

      val conv1 = getConv(conv.id)
      conv1.map(_.convType) shouldEqual Some(ConversationType.OneToOne)
      conv1.map(_.hidden) shouldEqual Some(false)

      listVisibleConvs should have size 1
      getUser(otherUser.id).map(_.connection) shouldEqual Some(ConnectionStatus.Accepted)
    }
  }
}
