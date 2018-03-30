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
package com.waz.service.otr

import com.waz.RobolectricUtils
import com.waz.api.Message
import com.waz.api.impl.ErrorResponse
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.model.otr.{Client, ClientId, SignalingKey}
import com.waz.sync.client.OtrClient
import com.waz.testutils.{MockUserModule, MockZMessaging}
import com.waz.threading.CancellableFuture
import com.waz.utils._
import com.waz.znet.ZNetClient.ErrorOrResponse
import org.robolectric.shadows.ShadowLog
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.threeten.bp.Instant
import com.waz.ZLog.ImplicitTag._

import scala.concurrent.duration._

@Ignore class OtrClientsServiceSpec extends FeatureSpec with Matchers with OptionValues with BeforeAndAfter with RobolectricTests with RobolectricUtils with ScalaFutures { test =>

  implicit val defaultPatience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  lazy val selfUser = UserData("test user")
  lazy val selfUserId = selfUser.id

  var deleteResponse: Either[ErrorResponse, Unit] = Right(())
  var deleteRequest = Option.empty[(ClientId, String)]

  lazy val userModule = new MockUserModule(userId = selfUserId) {

    override lazy val otrClient: OtrClient = new OtrClient(account.netClient) {
      override def deleteClient(id: ClientId, password: String): ErrorOrResponse[Unit] = {
        deleteRequest = Some((id, password))
        CancellableFuture.successful(deleteResponse)
      }
    }
  }

  lazy val service = new MockZMessaging(userModule, None, ClientId("client1")) {
    usersStorage.addOrOverwrite(selfUser).futureValue
  }

  before {
    deleteResponse = Right(())
    deleteRequest = None
  }

  after {
    ShadowLog.stream = null
  }

  feature("Clients listing") {

    lazy val time = Instant.now

    lazy val clients = returning(service.otrClientsStorage.signal(selfUserId))(_.disableAutowiring())

    lazy val incoming = returning(service.otrClientsStorage.incomingClientsSignal(service.selfUserId, service.clientId))(_.disableAutowiring())

    scenario("update clients") {
      service.otrClientsService.updateSelfClients(Seq(Client(ClientId("client1"), "client 1", signalingKey = Some(SignalingKey(AESKey("enc"), "mac")), regTime = Some(time)), Client(ClientId("client2"), "client 2", regTime = Some(time - 1.day)))).futureValue

      withDelay {
        clients.currentValue shouldBe 'defined
        incoming.currentValue shouldEqual Some(Seq.empty[Client])
        val cs = clients.currentValue.get
        cs.user shouldEqual selfUserId
        cs.clients should have size 2
        cs.clients.get(ClientId("client1")) shouldBe 'defined
        cs.clients.get(ClientId("client2")) shouldBe 'defined
      }
    }

    scenario("update self clients without signaling key") {
      service.otrClientsService.updateSelfClients(Seq(Client(ClientId("client1"), "client 1"), Client(ClientId("client2"), "client 2"), Client(ClientId("client3"), "client3"))).futureValue

      withDelay {
        clients.currentValue shouldBe 'defined
        val cs = clients.currentValue.get
        cs.user shouldEqual selfUserId
        cs.clients should have size 3
        cs.clients.get(ClientId("client1")) shouldBe 'defined
        cs.clients.get(ClientId("client2")) shouldBe 'defined
        cs.clients.get(ClientId("client3")) shouldBe 'defined
        cs.clients.get(ClientId("client1")).get.signalingKey shouldEqual Some(SignalingKey(AESKey("enc"), "mac"))
      }
    }

    scenario("incoming clients should return added devices") {
      service.otrClientsService.updateSelfClients(Seq(Client(ClientId("client4"), "client 4", regTime = Some(time + 1.hour))), replace = false).futureValue

      withDelay {
        clients.currentValue.map(_.clients.size) shouldEqual Some(4)
        incoming.currentValue.value should have size 1
        incoming.currentValue.value.head.label shouldEqual "client 4"
      }
    }

    scenario("incoming clients should only return devices in UNKNOWN state") {
      service.otrClientsStorage.updateVerified(selfUserId, ClientId("client4"), verified = true).futureValue

      withDelay {
        incoming.currentValue shouldEqual Some(Seq.empty[Client])
      }
    }
  }

  feature("Delete client") {

    scenario("Try deleting not existing client") {
      ShadowLog.stream = System.out
      val res = service.otrClientsService.deleteClient(ClientId(), "passwd").futureValue
      res shouldBe 'left
      deleteRequest shouldBe empty
    }

    scenario("Delete fails on server") {
      deleteResponse = Left(ErrorResponse(400, "error", "error"))
      service.otrClientsService.deleteClient(ClientId("client2"), "passwd").futureValue shouldEqual deleteResponse
      deleteRequest shouldEqual Some((ClientId("client2"), "passwd"))
    }

    scenario("Delete for other client is successful") {
      service.otrClientsService.deleteClient(ClientId("client2"), "passwd").futureValue shouldEqual Right(())
      deleteRequest shouldEqual Some((ClientId("client2"), "passwd"))

      service.otrClientsStorage.getClients(selfUserId).futureValue should have size 3
    }

    scenario("Delete for current client is successful") {
      service.otrClientsService.deleteClient(ClientId("client1"), "passwd").futureValue shouldEqual Right(())
      deleteRequest shouldEqual Some((ClientId("client1"), "passwd"))

      service.otrClientsStorage.getClients(selfUserId).futureValue should have size 2
    }
  }

  feature("Verification state") {
    import com.waz.api.Verification._

    lazy val user1 = service.insertUser(UserData("test user1"))
    lazy val user2 = service.insertUser(UserData("test user2"))
    lazy val user3 = service.insertUser(UserData("test user3"))
    lazy val conv = returning(service.insertConv(ConversationData(ConvId(), RConvId(), None, selfUserId, ConversationType.Group))) { c =>
      service.addMember(c.id, selfUserId)
      service.addMember(c.id, user1.id)
      service.addMember(c.id, user2.id)
    }
    lazy val selfClients = Seq(Client(ClientId(), "self", verified = VERIFIED))
    lazy val clients1 = Seq(Client(ClientId(), "c1"), Client(ClientId(), "c2"))
    lazy val clients2 = Seq(Client(ClientId(), "c3"))
    lazy val clients3 = Seq(Client(ClientId(), "c4"))
    lazy val clients2_1 = Seq(Client(ClientId(), "c5"))

    scenario("update user verification state when clients are verified") {
      service.otrClientsService.updateSelfClients(selfClients, true)
      service.otrClientsService.updateUserClients(user1.id, clients1)
      service.otrClientsService.updateUserClients(user2.id, clients2)
      service.getUser(user1.id).map(_.verified) shouldEqual Some(UNKNOWN)

      clients1 foreach { c =>
        service.otrClientsStorage.updateVerified(user1.id, c.id, verified = true)
      }
      withDelay {
        service.getUser(user1.id).map(_.verified) shouldEqual Some(VERIFIED)
      }

      service.getUser(user2.id).map(_.verified) shouldEqual Some(UNKNOWN)
    }

    scenario("update conversation verification state when users are verified") {
      service.getConv(conv.id).map(_.verified) shouldEqual Some(UNKNOWN)
      clients2 foreach { c =>
        service.otrClientsStorage.updateVerified(user2.id, c.id, verified = true)
      }

      withDelay {
        service.getUser(user2.id).map(_.verified) shouldEqual Some(VERIFIED)
        service.getConv(conv.id).map(_.verified) shouldEqual Some(VERIFIED)
      }
    }

    scenario("add message when conversation gets verified") {
      withDelay {
        service.messagesStorage.getLastMessage(conv.id).futureValue.map(_.msgType) shouldEqual Some(Message.Type.OTR_VERIFIED)
      }
    }

    scenario("add message when conversation is unverified") {
      val msg = service.messages.addTextMessage(conv.id, "test msg").futureValue
      service.messagesStorage.update(msg.id, _.copy(state = Message.Status.SENT))

      service.otrClientsStorage.updateVerified(user1.id, clients1.head.id, verified = false)

      withDelay {
        service.getUser(user1.id).map(_.verified) shouldEqual Some(UNVERIFIED)
        service.getConv(conv.id).map(_.verified) shouldEqual Some(UNKNOWN)
        service.messagesStorage.getLastMessage(conv.id).futureValue.map(_.msgType) shouldEqual Some(Message.Type.OTR_UNVERIFIED)
        service.messagesStorage.getLastMessage(conv.id).futureValue.map(_.members) shouldEqual Some(Set(user1.id))
      }
    }

    scenario("switch back to verified when unverified user is removed") {
      service.membersStorage.remove(conv.id, user1.id)

      withDelay {
        service.getConv(conv.id).map(_.verified) shouldEqual Some(VERIFIED)
        service.listMessages(conv.id).last.contentString shouldEqual "test msg" // OTR_UNVERIFIED gets deleted
        service.messagesStorage.getLastMessage(conv.id).futureValue.map(_.contentString) shouldEqual Some("test msg")
      }
    }

    scenario("switch to unverified state when user without otr devices is added") {
      service.addMember(conv.id, user3.id)

      withDelay {
        service.getConv(conv.id).map(_.verified) shouldEqual Some(UNKNOWN)
        service.messagesStorage.getLastMessage(conv.id).futureValue.map(_.msgType) shouldEqual Some(Message.Type.OTR_MEMBER_ADDED)
      }
    }

    scenario(s"switch to verified when clients are added and verified") {
      service.otrClientsService.updateUserClients(user3.id, clients3).futureValue
      clients3 foreach { c =>
        service.otrClientsStorage.updateVerified(user3.id, c.id, verified = true)
      }

      withDelay {
        service.getConv(conv.id).map(_.verified) shouldEqual Some(VERIFIED)
        service.messagesStorage.getLastMessage(conv.id).futureValue.map(_.contentString) shouldEqual Some("test msg") // OTR_UNVERIFIED gets deleted again
      }
    }

    scenario("switch to unverified state when a verified user adds an unverified device") {
      service.otrClientsService.updateUserClients(user2.id, clients2_1).futureValue

      withDelay {
        service.getConv(conv.id).map(_.verified) shouldEqual Some(UNVERIFIED)
        service.messagesStorage.getLastMessage(conv.id).futureValue.map(_.msgType) shouldEqual Some(Message.Type.OTR_DEVICE_ADDED)
      }
    }
  }
}
