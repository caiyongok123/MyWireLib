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
package com.waz.api.impl

import java.util.Date

import android.database.sqlite.SQLiteDatabase
import com.waz.RobolectricUtils
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.sync._
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.sync.client._
import com.waz.testutils.{EmptySyncService, MockZMessaging}
import com.waz.threading.CancellableFuture
import com.waz.utils.events.EventContext
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

@Ignore class UserConnectSpec extends FeatureSpec with Matchers with BeforeAndAfter with GivenWhenThen with RobolectricTests with RobolectricUtils { test =>

  implicit def db: SQLiteDatabase = service.db.dbHelper.getWritableDatabase

  implicit val timeout: Timeout = 5.seconds
  implicit val ec = EventContext.Global

  lazy val selfUser = UserData("Self User")

  var postConnectionCalled : Option[(UserId, String)] = None
  var syncUserFromServerCalled: Option[UserId] = None

  var createConnectionReturnValue: Either[ErrorResponse, UserConnectionEvent] = Left(ErrorResponse(400, "msg", "label"))

  lazy val service = new MockZMessaging(selfUserId = selfUser.id) { self =>

    override lazy val connectionsClient: ConnectionsClient = new ConnectionsClient(zNetClient) {
      override def createConnection(user: UserId, name: String, message: String) = {
        CancellableFuture.successful(createConnectionReturnValue)
      }
    }

    override lazy val sync: SyncServiceHandle = new EmptySyncService {

      override def syncUsers(id: UserId*) = {
        syncUserFromServerCalled = id.headOption
        super.syncUsers(id: _*)
      }

      override def postConnection(user: UserId, name: String, message: String) = {
        postConnectionCalled = Some((user, name))
        self.connectionsSync.postConnection(user, name, message)
        super.postConnection(user, name, message)
      }
    }
  }

  import service._

  before {
    insertUser(selfUser)

    postConnectionCalled = None
    createConnectionReturnValue = Left(ErrorResponse(400, "msg", "label"))
    syncUserFromServerCalled = None
  }

  def connectToUser(user: UserData) = Await.result(service.connection.connectToUser(user.id, "Hi there", user.name), timeout)

  feature("User getConversation") {
    scenario("createConversation when connecting to User") {
      val user = insertUser(UserData("Other user1"))

      val conv = connectToUser(user)
      conv.map(_.convType) shouldEqual Some(ConversationType.WaitForConnection)
      postConnectionCalled should be(Some(user.id, user.name))
    }

    scenario("handle response from postConnection ~~> set connection status and remoteConvId") {
      val user = insertUser(UserData("Other user2"))
      val convId = RConvId()
      createConnectionReturnValue = Right(sentConnectionEvent(user.id, convId))
      val count = listConvs.size

      connectToUser(user)

      withDelay {
        postConnectionCalled should be(Some(user.id, user.name))
        getUser(user.id).map(_.connection) should be(Some(UserData.ConnectionStatus.PendingFromUser))
        getConv(user.id).map(_.remoteId) should be(Some(convId))
      }

      listConvs should have size (count + 1)
    }

    scenario("handle events after connect called on user object, assume that we got a response from postConnection") {
      val user = insertUser(UserData("Other user2"))
      val convId = RConvId()
      val convCount = listConvs.size

      When("calling connect on user")
      connectToUser(user)

      And("receiving conversationCreateEvent")
      dispatchEvent(conversationCreateEvent(convId))

      var convo1 = getConv(user.id)
      var convo2 = getConv(convId)

      convo1 should be('defined)
      convo2 should be('defined)
      convo1.get.id should not be convo2.get.id

      convo1.map(_.convType) shouldEqual Some(ConversationType.WaitForConnection)
      convo2.map(_.convType) shouldEqual Some(ConversationType.WaitForConnection)
      convo2.map(_.hidden) shouldEqual Some(true)

      When("receiving events")
      dispatchEvent(conversationConnectRequestEvent(user.id, convId))
      dispatchEvent(sentConnectionEvent(user.id, convId))

      Then("we should have only one conversation with the correct remoteId and id")
      convo1 = getConv(user.id)
      convo2 = getConv(convId)

      convo1 should be(convo2)
      convo1.get.id should be(ConvId(user.id.toString))
      convo1.get.remoteId should be(convId)
      convo1.get.convType should be(ConversationData.ConversationType.WaitForConnection)

      listConvs should have size (convCount + 1)

      When("receiving userConnectionEvent")
      dispatchEvent(sentConnectionEvent(user.id, convId))

      Then("otherUsers connection status should be set to send")
      getUser(user.id).get.connection should be(UserData.ConnectionStatus.PendingFromUser)
    }

    scenario("handle events after connect called on user object, on other device (No local conversation created)") {
      Given("a user in local database")
      val user = insertUser(UserData("Other User"))
      val convId = RConvId()
      val convsCount = listConvs.size

      When("receiving conversationCreateEvent")
      dispatchEvent(conversationCreateEvent(convId))

      Then("we should have a conversation with remoteId")
      var convo = getConv(convId)

      convo should be('defined)
      convo.get.hidden shouldEqual true

      When("receiving events")
      dispatchEvent(conversationConnectRequestEvent(user.id, convId))
      dispatchEvent(sentConnectionEvent(user.id, convId))

      Then("we should have only one conversation with the correct remoteId")
      listConvs should have size (convsCount + 1)

      convo = getConv(convId)
      convo.get.remoteId should be(convId)
      convo.get.id should be(ConvId(user.id.toString))
      convo.get.convType shouldEqual ConversationType.WaitForConnection

      Then("otherUsers connection status should be set to sent")
      getUser(user.id).get.connection should be(UserData.ConnectionStatus.PendingFromUser)
    }

    scenario("handle events after connect called on user object") {
      Given("a user in local database")
      val user = insertUser(UserData("Other User"))
      val convId = RConvId()

      And("createConnection return value set to Some(UserConnectionEvent)")
      createConnectionReturnValue = Right(sentConnectionEvent(user.id, convId).copy(lastUpdated = new Date))

      When("calling connect on the user object")
      connectToUser(user)

      And("receiving conversationCreateEvent")
      dispatchEvent(conversationCreateEvent(convId))

      Then("one conversations should be created")
      val conv = getConv(user.id)

      conv should be('defined)
      conv.map(_.remoteId) shouldEqual Some(convId)

      When("receiving conversationConnectRequest")
      val conversationConnectRequest = conversationConnectRequestEvent(user.id, convId)
      dispatchEvent(conversationConnectRequest)

      Then("we should have only one conversation with the correct remoteId and id")
      val convo1 = getConv(user.id)
      val convo2 = getConv(convId)

      convo1 should be(convo2)
      convo1.get.id should be(ConvId(user.id.toString))
      convo1.get.remoteId should be(convId)
      convo1.get.convType should be(ConversationData.ConversationType.WaitForConnection)

      When("receiving userConnectionEvent")
      val userConnectionEvent =  sentConnectionEvent(user.id)
      dispatchEvent(userConnectionEvent)

      Then("otherUsers connection status should be set to send")
      getUser(user.id).get.connection should be(UserData.ConnectionStatus.PendingFromUser)
    }

    scenario("other user accepts connection after connect called on user object") {
      Given("a user in local database")
      val user = insertUser(UserData("Other User"))
      val convId = RConvId()

      And("createConnection return value set to Some(UserConnectionEvent)")
      createConnectionReturnValue = Right(sentConnectionEvent(user.id, convId).copy(lastUpdated = new Date))

      When("calling connect on the user object")
      connectToUser(user)

      And("receiving conversationCreateEvent")
      dispatchEvent(conversationCreateEvent(convId))

      And("receiving events")
      dispatchEvent(conversationCreateEvent(convId))
      dispatchEvent(conversationConnectRequestEvent(user.id, convId))
      dispatchEvent(sentConnectionEvent(user.id, convId))

      When("receiving member join event")
      dispatchEvent(memberJoinEvent(user.id, convId))

      Then("We should have a conversation with two members")
      val members = getConv(convId).map { conversation =>
        Await.result(service.membersStorage.getActiveUsers(conversation.id), timeout)
      }

      members should be('defined)
      members.get should contain allOf(selfUser.id, user.id)

      When("receiving userConnectionEvent with status connected")
      dispatchEvent(acceptedConnectionEvent(selfUser.id, user.id, convId))

      Then("Other user status should be accepted")
      getUser(user.id).map(_.connection) should be(Some(UserData.ConnectionStatus.Accepted))

      And("Conversation type should be one-2-one")
      getConv(convId).map(_.convType) should be(Some(ConversationData.ConversationType.OneToOne))
    }
  }

  def sentConnectionEvent(user: UserId, conv: RConvId = RConvId()) =
    UserConnectionEvent(conv, selfUser.id, user, Some("Hi"), ConnectionStatus.PendingFromUser, new Date)

  def pendingConnectionEvent(user: UserId, conv: RConvId = RConvId()) =
    UserConnectionEvent(conv, user, selfUser.id, Some("Hi"), ConnectionStatus.PendingFromOther, new Date)

  def acceptedConnectionEvent(from: UserId, to: UserId, conv: RConvId) =
    UserConnectionEvent(conv, selfUser.id, to, Some("Hello test"), ConnectionStatus.Accepted, new Date)

  def conversationCreateEvent(conv: RConvId = RConvId(), user: UserId = selfUser.id) = 
    CreateConversationEvent(conv, new Date, user, ConversationResponse(ConversationData(ConvId(conv.str), conv, None, user, ConversationType.WaitForConnection), Nil))

  def conversationConnectRequestEvent(user: UserId, conv: RConvId) = 
    ConnectRequestEvent(conv, new Date, selfUser.id, "Hello Test", user, "One2One", None)
  
  def memberJoinEvent(user: UserId, conv: RConvId) =
    MemberJoinEvent(conv, new Date, selfUser.id, Seq(user))
}
