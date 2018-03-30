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

import akka.pattern._
import com.waz.api._
import com.waz.model.ConversationData.ConversationType
import com.waz.provision.ActorMessage.{Login, SendText, Successful}
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.robolectric.annotation.Config
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

@Config(manifest = "tests/OtrAndroidManifest.xml")
class OtrVerificationSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with ProvisionedApiSpec with ThreadActorSpec { test =>

  override val provisionFile = "/three_users_connected.json"
  override val otrOnly = true

  lazy val convs = api.getConversations
  lazy val conv = convs.find(_.getType == ConversationType.Group).get
  def msgs = listMessages(conv.id)

  lazy val auto2Id = provisionedUserId("auto2")
  lazy val auto3Id = provisionedUserId("auto3")

  lazy val auto2 = registerDevice("otr_auto2")
  lazy val auto2_1 = registerDevice("otr_auto2_1")
  lazy val auto2_2 = registerDevice("otr_auto2_2")
  lazy val auto2_3 = registerDevice("otr_auto2_3")
  lazy val auto3 = registerDevice("otr_auto3")
  lazy val auto3_1 = registerDevice("otr_auto3_1")
  lazy val auto1_1 = registerDevice("otr_auto1_1")
  lazy val auto1_2 = registerDevice("otr_auto1_2")

  def verifyConversation() = {
    val clients = api.getSelf.getOtherOtrClients
    val auto2Clients = api.getUser(provisionedUserId("auto2").str).getOtrClients
    val auto3Clients = api.getUser(provisionedUserId("auto3").str).getOtrClients
    withDelay {
      clients should not be empty
      auto2Clients should not be empty
      auto3Clients should not be empty
    }
    clients foreach { _.setVerified(true) }
    auto2Clients foreach { _.setVerified(true) }
    auto3Clients foreach { _.setVerified(true) }
  }

  def dismissErrors() = {
    val errors = api.getErrors
    withDelay {
      errors should not be empty
    }
    errors foreach { _.dismiss() }
  }

  scenario("init") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    auto3 ? Login(provisionedEmail("auto3"), "auto3_pass") should eventually(be(Successful))
    auto1_1 ? Login(email, password) should eventually(be(Successful))

    val clients = api.getSelf.getOtherOtrClients
    withDelay {
      convs should not be empty
      clients should not be empty
    }
  }

  feature("Conversation verification state") {

    scenario("Send message in unverified conv") {
      conv.getVerified shouldEqual Verification.UNKNOWN
      zmessaging.convsUi.sendMessage(conv.id, "test msg")
      withDelay {
        msgs.last.contentString shouldEqual "test msg"
        msgs.last.state shouldEqual Message.Status.SENT
      }
    }

    scenario("Verify conversation") {
      conv.getVerified shouldEqual Verification.UNKNOWN

      verifyConversation()

      withDelay {
        conv.getVerified shouldEqual Verification.VERIFIED
        msgs.last.msgType shouldEqual Message.Type.OTR_VERIFIED
      } (10.seconds)
    }

    scenario("Send message in verified conv") {
      zmessaging.convsUi.sendMessage(conv.id, "test msg 1")
      withDelay {
        msgs.last.contentString shouldEqual "test msg 1"
        msgs.last.state shouldEqual Message.Status.SENT
      }
    }

    scenario("Auto2 adds new device") {
      auto2_1 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    }

    scenario("Receive new message from new device in previously verified conv") {
      awaitUi(1.second)
      val count = msgs.size
      auto2_1 ? SendText(conv.data.remoteId, "remote msg")

      withDelay {
        conv.getVerified shouldEqual Verification.UNVERIFIED
        withClue(msgs.map(m => (m.msgType, m.contentString)).mkString(", ")) {
          msgs.last.contentString shouldEqual "remote msg"
          msgs.last.msgType shouldEqual Message.Type.TEXT
          msgs.last.state shouldEqual Message.Status.SENT
          msgs should have size (count + 2)
          msgs(count).msgType shouldEqual Message.Type.OTR_DEVICE_ADDED
        }
      }
    }
  }

  feature("Sending in degraded conversation") {

    scenario("Sending a message in unverified conv fails") {
      conv.getVerified shouldEqual Verification.UNVERIFIED
      zmessaging.convsUi.sendMessage(conv.id, "failing msg 1")
      withDelay {
        msgs.last.contentString shouldEqual "failing msg 1"
        msgs.last.state shouldEqual Message.Status.FAILED
        conv.getVerified shouldEqual Verification.UNVERIFIED
      }
    }

    scenario("Errors list should contain an error with a message") {
      val errors = api.getErrors
      withDelay {
        errors should not be empty
        errors.head.getType shouldEqual ErrorType.CANNOT_SEND_MESSAGE_TO_UNVERIFIED_CONVERSATION
        errors.head.getMessages.asScala.toSeq.map(_.getBody) shouldEqual Seq("failing msg 1")
      }
    }

    scenario("Dismissing error changes conv state to UNKNOWN") {
      dismissErrors()
      withDelay {
        conv.getVerified shouldEqual Verification.UNKNOWN
      }
    }

    scenario("Verify conversation") {
      verifyConversation()

      withDelay {
        conv.getVerified shouldEqual Verification.VERIFIED
        withClue(msgs.map(_.msgType).mkString(", ")) {
          msgs.last.msgType shouldEqual Message.Type.OTR_VERIFIED
        }
      }
    }

    scenario("Send message in verified conv") {
      zmessaging.convsUi.sendMessage(conv.id, "test msg 1")
      withDelay {
        msgs.last.contentString shouldEqual "test msg 1"
        msgs.last.state shouldEqual Message.Status.SENT
      }
    }

    scenario("Remote user adds new device") {
      auto2_2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
      awaitUi(5.seconds)
    }

    scenario("Sending to conv with new client fails and conv gets unverified") {
      val count = msgs.size
      conv.getVerified shouldEqual Verification.VERIFIED
      zmessaging.convsUi.sendMessage(conv.id, "failing msg 2")
      withDelay {
        conv.getVerified shouldEqual Verification.UNVERIFIED
        withClue(msgs.map(_.msgType).mkString(", ")) {
          msgs(count).msgType shouldEqual Message.Type.OTR_DEVICE_ADDED
          msgs.last.contentString shouldEqual "failing msg 2"
          msgs.last.state shouldEqual Message.Status.FAILED
        }
      }
    }

    scenario("Trying to send another message also fails") {
      zmessaging.convsUi.sendMessage(conv.id, "failing msg 3")
      withDelay {
        msgs.last.contentString shouldEqual "failing msg 3"
        msgs.last.state shouldEqual Message.Status.FAILED
        conv.getVerified shouldEqual Verification.UNVERIFIED
      }
    }

    scenario("Errors list should contain an error with two messages") {
      val errors = api.getErrors
      withDelay {
        errors should not be empty
        errors.head.getType shouldEqual ErrorType.CANNOT_SEND_MESSAGE_TO_UNVERIFIED_CONVERSATION
        errors.head.getMessages.asScala.toSeq.map(_.getBody) shouldEqual Seq("failing msg 2", "failing msg 3")
      }
    }

    scenario("Dismiss errors") {
      dismissErrors()
      withDelay {
        conv.getVerified shouldEqual Verification.UNKNOWN
      }
    }

    scenario("Retry message sending") {
      val count = msgs.size
      val failed = msgs.filter { m => m.contentString.startsWith("failing msg") }
      failed foreach { m =>
        zmessaging.messages.retryMessageSending(m.convId, m.id)
      }
      withDelay {
        msgs.size shouldEqual count
        failed.map(_.state) shouldEqual failed.map(_ => Message.Status.SENT)
      }
    }

    scenario("Verify conversation again") {
      verifyConversation()

      withDelay {
        conv.getVerified shouldEqual Verification.VERIFIED
        withClue(msgs.map(_.msgType).mkString(", ")) {
          msgs.last.msgType shouldEqual Message.Type.OTR_VERIFIED
        }
      }
    }

    scenario("Two remote users add new device") {
      auto2_3 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
      auto3_1 ? Login(provisionedEmail("auto3"), "auto3_pass") should eventually(be(Successful))
      awaitUi(5.seconds)
    }

    scenario("Sending to conv with new clients fails, conv gets unverified, generated message contains both users") {
      val count = msgs.size
      val errors = api.getErrors
      conv.getVerified shouldEqual Verification.VERIFIED
      zmessaging.convsUi.sendMessage(conv.id, "failing msg 4")
      withDelay {
        conv.getVerified shouldEqual Verification.UNVERIFIED
        withClue(msgs.map(_.msgType).mkString(", ")) {
          msgs(count).msgType shouldEqual Message.Type.OTR_DEVICE_ADDED
          msgs(count).members shouldEqual Set(auto2Id, auto3Id)
          msgs.last.contentString shouldEqual "failing msg 4"
          msgs.last.state shouldEqual Message.Status.FAILED
        }
        errors should not be empty
        errors.head.getType shouldEqual ErrorType.CANNOT_SEND_MESSAGE_TO_UNVERIFIED_CONVERSATION
        errors.head.getMessages.asScala.toSeq.map(_.getBody) shouldEqual Seq("failing msg 4")
      }
    }
  }

  feature("Degrading on new devices") {

    scenario("Verify conversation") {
      verifyConversation()

      withDelay {
        conv.getVerified shouldEqual Verification.VERIFIED
        withClue(msgs.map(_.msgType).mkString(", ")) {
          msgs.last.msgType shouldEqual Message.Type.OTR_VERIFIED
        }
      }
    }

    scenario("Adding new device un-verifies the conversation") {
      auto1_2 ? Login(email, password) should eventually(be(Successful))

      val clients = api.getSelf.getOtherOtrClients
      withDelay {
        clients should have size 2
        conv.getVerified shouldEqual Verification.UNVERIFIED
        msgs.last.msgType shouldEqual Message.Type.OTR_DEVICE_ADDED
        msgs.last.members should have size 1
        msgs.last.members.head shouldEqual zmessaging.selfUserId
      }
    }

    scenario("Verify added device, conv gets verified") {
      val clients = api.getSelf.getOtherOtrClients
      withDelay { clients should have size 2 }
      val count = msgs.size

      clients foreach { c => if (c.getVerified == Verification.UNKNOWN) c.setVerified(true) }

      withDelay {
        conv.getVerified shouldEqual Verification.VERIFIED
        withClue(msgs.map(m => (m.msgType, m.contentString))) {
          msgs.last.msgType shouldEqual Message.Type.OTR_VERIFIED // OTR_DEVICE_ADDED msg gets removed
          msgs should have size (count - 1)
        }
      }
    }
  }
}
