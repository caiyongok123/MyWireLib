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

import com.waz.RobolectricUtils
import com.waz.model.ConversationData.ConversationType
import com.waz.model.Event.EventDecoder
import com.waz.model._
import com.waz.sync._
import com.waz.sync.client.ConnectionsClient
import com.waz.sync.handler.ConnectionsSyncHandler
import com.waz.testutils.{EmptySyncService, MockZMessaging}
import com.waz.threading.CancellableFuture
import com.waz.utils.events.EventContext
import org.json
import org.json.JSONObject
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

@Ignore class IncomingConnectionSpec extends FeatureSpec with Matchers with BeforeAndAfter with GivenWhenThen with RobolectricTests with RobolectricUtils {

  var zmessaging: MockZMessaging = _

  implicit val ec = EventContext.Global
  val timeout = 5.seconds

  lazy val selfUser = UserData("Self User")
  lazy val otherUser = UserData("Other User")

  var postConnectionCalled : Option[(UserId, String)] = None
  var syncUserFromServerCalled: Option[UserId] = None

  var createConnectionReturnValue: Either[ErrorResponse, UserConnectionEvent] = Left(ErrorResponse(400, "msg", "label"))

  before {
    postConnectionCalled = None
    createConnectionReturnValue = Left(ErrorResponse(400, "msg", "label"))
    syncUserFromServerCalled = None

    zmessaging = new MockZMessaging(selfUserId = selfUser.id) { self =>

      override lazy val sync: SyncServiceHandle = new EmptySyncService {

        lazy val handler = new ConnectionsSyncHandler(usersStorage, connection, new ConnectionsClient(null) {
          override def createConnection(user: UserId, name: String, message: String) = {
            CancellableFuture.successful(createConnectionReturnValue)
          }
        }, eventPipeline)

        override def syncUsers(id: UserId*) = {
          id match {
            case selfUser.id => insertUser(selfUser)
            case _ => ()
          }

          syncUserFromServerCalled = id.headOption
          super.syncUsers(id: _*)
        }

        override def postConnection(user: UserId, name: String, message: String) = {
          postConnectionCalled = Some((user, name))
          handler.postConnection(user, name, message)
          super.postConnection(user, name, message)
        }
      }
    }

    zmessaging.users.getSelfUser
    zmessaging.users.getSelfUserId
  }

  after {
    Await.result(zmessaging.storage.db { _ => }, 5.seconds)
  }


  // Remember the roles of selfUser and otherUser flipped   selfUser <~~> otherUser
  feature("Receive connection") {

    scenario("Some user wants to connect to you") {
      Given("a user in local database")
      zmessaging.insertUser(selfUser)
      zmessaging.insertUser(otherUser)

      When("Receiving user userConnectionEvent")
      val userConnectionEvent = EventDecoder(new json.JSONObject(userConnectionEventData_01)).asInstanceOf[UserConnectionEvent]
      zmessaging.dispatch(userConnectionEvent)
      Thread.sleep(500)

      Then("the connecting user should be synced")
      syncUserFromServerCalled should be(Some(otherUser.id))

      And("user should be in local db")
      zmessaging.getUser(selfUser.id) should be('defined)

      And("There should be a new incoming conversation with two members")
      val convo = zmessaging.getConv(userConnectionEvent.convId)
      convo.map(_.convType) should be(Some(ConversationData.ConversationType.Incoming))
      zmessaging.listMembers(convo.get.id).map(_.userId).toSet shouldEqual Set(selfUser.id, otherUser.id)

      When("receiving a memberJoinEvent")
      val memberJoinEvent: MemberJoinEvent = EventDecoder(new JSONObject(memberJoinEventData)).asInstanceOf[MemberJoinEvent]
      zmessaging.dispatch(memberJoinEvent)
      Thread.sleep(500)

      Then("The joined members should be in the Conversation")
      val convoMembers = zmessaging.listMembers(convo.get.id).map(_.userId)

      memberJoinEvent.userIds.foreach { userId =>
        convoMembers.contains(userId) shouldEqual true
      }

      When("receiving a UserConnection with status Accepted")
      syncUserFromServerCalled = None
      val userConnectionEvent02 = EventDecoder(new json.JSONObject(userConnectionEventData_04))
      zmessaging.dispatch(userConnectionEvent02)
      Thread.sleep(500)

      Then("Users connection status should be Accepted")
      zmessaging.getUser(otherUser.id).map(_.connection) should be(Some(UserData.ConnectionStatus.Accepted))

      And("Conversation type should be changed to one-to-one")
      zmessaging.getConv(userConnectionEvent.convId).map(_.convType) shouldEqual Some(ConversationType.OneToOne)
      zmessaging.listConvs should have size 1

      And("the accepted user should be synced")
      syncUserFromServerCalled should be(Some(otherUser.id))
    }

    scenario("Some user wants to connect to you and you don't know him") {
      Given("a user in local database")
      //insertUser(selfUser) // We don't know this user yet
      zmessaging.insertUser(selfUser)

      When("Receiving user userConnectionEvent")
      val userConnectionEvent: UserConnectionEvent = EventDecoder(new json.JSONObject(userConnectionEventData_01)).asInstanceOf[UserConnectionEvent]
      zmessaging.dispatch(userConnectionEvent)
      Thread.sleep(500)

      Then("there should be a sync for a new user")
      syncUserFromServerCalled should be(Some(otherUser.id))

      And("user should be in local db")
      zmessaging.getUser(otherUser.id) should be('defined)

      And("There should be a new one-2-one conversation with two member")
      val convo = zmessaging.getConv(userConnectionEvent.convId)
      convo.map(_.convType) should be(Some(ConversationData.ConversationType.Incoming))
      zmessaging.listMembers(convo.get.id).map(_.userId).toSet shouldEqual Set(selfUser.id, otherUser.id)

      When("receiving a memberJoinEvent")
      val memberJoinEvent: MemberJoinEvent = EventDecoder(new JSONObject(memberJoinEventData)).asInstanceOf[MemberJoinEvent]
      zmessaging.dispatch(memberJoinEvent)
      Thread.sleep(500)

      Then("The joined members should be in the Conversation")
      val convoMembers = zmessaging.listMembers(convo.get.id).map(_.userId)

      memberJoinEvent.userIds.foreach { userId =>
        convoMembers.contains(userId) shouldEqual true
      }

      When("receiving a UserConnection with status Accepted")
      val userConnectionEvent02 = EventDecoder(new json.JSONObject(userConnectionEventData_04))
      zmessaging.dispatch(userConnectionEvent02)
      Thread.sleep(500)

      Then("Users connection status should be Accepted")
      zmessaging.getUser(otherUser.id).map(_.connection) should be(Some(UserData.ConnectionStatus.Accepted))

      And("Conversation type should be changed to one-to-one")
      zmessaging.getConv(userConnectionEvent.convId).map(_.convType) shouldEqual Some(ConversationType.OneToOne)
      zmessaging.listConvs should have size 1
    }
  }

  lazy val conversationCreateEventData =
    s"""
      | {
      |   "conversation": "f660330f-f0e3-4511-8d15-71251f44ce32",
      |   "time": "2014-06-12T10:04:02.038Z",
      |   "data": {
      |     "creator": "${otherUser.id}",
      |     "members": {
      |       "self": {
      |         "status": 0,
      |         "last_read": "15.8001080027b41bf4",
      |         "muted_time": null,
      |         "muted": null,
      |         "status_time": "2014-06-12T10:04:02.008Z",
      |         "status_ref": "0.0",
      |         "id": "${otherUser.id}",
      |         "archived": null
      |       },
      |       "others": []
      |     },
      |     "name": "One2One",
      |     "id": "f660330f-f0e3-4511-8d15-71251f44ce32",
      |     "type": 3,
      |     "last_event_time": "2014-06-12T10:04:02.008Z",
      |     "last_event": "15.8001080027b41bf4"
      |   },
      |   "from": "${otherUser.id}",
      |   "type": "conversation.create"
      | }
    """.stripMargin

  lazy val conversationConnectRequestEventData =
    s"""
      | {
      |   "conversation": "f660330f-f0e3-4511-8d15-71251f44ce32",
      |   "time": "2014-06-12T10:04:02.008Z",
      |   "data": {
      |     "email": null,
      |     "name": "One2One",
      |     "message": "Hello Test",
      |     "recipient": "${selfUser.id}"
      |   },
      |   "from": "${otherUser.id}",
      |   "id": "16.8001080027b41bf5",
      |   "type": "conversation.connect-request"
      | }
    """.stripMargin

  lazy val userConnectionEventData_01 =
    s"""
      | {
      |   "connection": {
      |     "status": "pending",
      |     "conversation": "f660330f-f0e3-4511-8d15-71251f44ce32",
      |     "to": "${otherUser.id}",
      |     "from": "${selfUser.id}",
      |     "last_update": "2014-06-12T10:04:02.052Z",
      |     "message": "Hello Test"
      |   },
      |   "type": "user.connection"
      | }
    """.stripMargin

  lazy val userConnectionEventData_02 =
    s"""
      | {
      |   "connection": {
      |     "status": "sent",
      |     "conversation": "f660330f-f0e3-4511-8d15-71251f44ce32",
      |     "to": "${selfUser.id}",
      |     "from": "${otherUser.id}",
      |     "last_update": "2014-06-12T10:04:02.047Z",
      |     "message": "Hello Test"
      |   },
      |   "type": "user.connection"
      | }
    """.stripMargin

  lazy val memberJoinEventData =
    s"""
      | {
      |   "conversation": "f660330f-f0e3-4511-8d15-71251f44ce32",
      |   "time": "2014-06-12T10:23:26.764Z",
      |   "data": {
      |     "user_ids": [
      |       "${selfUser.id}"
      |     ]
      |   },
      |   "from": "${otherUser.id}",
      |   "id": "19.8001080027b41bf8",
      |   "type": "conversation.member-join"
      | }
    """.stripMargin

  lazy val userConnectionEventData_03 =
    s"""
      | {
      |   "connection": {
      |     "status": "accepted",
      |     "conversation": "f660330f-f0e3-4511-8d15-71251f44ce32",
      |     "to": "${selfUser.id}",
      |     "from": "${otherUser.id}",
      |     "last_update": "2014-06-12T10:23:26.794Z",
      |     "message": "Hello Test"
      |   },
      |   "type": "user.connection"
      | }
    """.stripMargin

  lazy val userConnectionEventData_04 =
    s"""
      | {
      |   "connection": {
      |     "status": "accepted",
      |     "conversation": "f660330f-f0e3-4511-8d15-71251f44ce32",
      |     "to": "${otherUser.id}",
      |     "from": "${selfUser.id}",
      |     "last_update": "2014-06-12T10:23:26.794Z",
      |     "message": "Hello Test"
      |   },
      |   "type": "user.connection"
      | }
    """.stripMargin

  lazy val postConnectionResponseData =
    s"""
      | {
      |    "conversation": "f660330f-f0e3-4511-8d15-71251f44ce32",
      |    "from": "${otherUser.id}",
      |    "last_update": "2014-06-12T10:20:37.844Z",
      |    "message": "Hello Test",
      |    "status": "sent",
      |    "to": "${selfUser.id}"
      | }
    """.stripMargin

  lazy val connectionClientReturn: UserConnectionEvent = {
    val js = new JSONObject()
    js.put("type", "user.connection")
    js.put("connection", new JSONObject(postConnectionResponseData))

    EventDecoder(js).asInstanceOf[UserConnectionEvent]
  }
}
