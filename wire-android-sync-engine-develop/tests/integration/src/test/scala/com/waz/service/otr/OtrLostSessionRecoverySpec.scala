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

import java.io.File

import akka.pattern.ask
import com.waz.api.{Message, ProvisionedApiSpec, ThreadActorSpec}
import com.waz.model.RConvId
import com.waz.provision.ActorMessage.{Login, SendText, Successful}
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.robolectric.annotation.Config
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}

@Config(manifest = "tests/OtrAndroidManifest.xml")
class OtrLostSessionRecoverySpec extends FeatureSpec with Matchers with BeforeAndAfterAll with ProvisionedApiSpec with ThreadActorSpec with ScalaFutures { test =>
  override val provisionFile = "/two_users_connected.json"
  override val otrOnly = true

  lazy val convs = api.getConversations
  lazy val conv = {
    withDelay(convs should not be empty)
    convs.head
  }
  def msgs = listMessages(conv.id)

  lazy val auto2 = registerDevice("otr_auto2")

  lazy val auto2UserId = provisionedUserId("auto2")
  lazy val auto2User = api.getUser(provisionedUserId("auto2").str)
  lazy val auto2Clients = auto2User.getOtrClients

  lazy val auto2Session = {
    withDelay { auto2Clients should not be empty }
    val client = auto2Clients.head.asInstanceOf[com.waz.api.impl.otr.OtrClient]
    new File(new File(zmessaging.cryptoBox.cryptoBoxDir, "sessions"), OtrService.sessionId(client.userId, client.clientId))
  }

  scenario("init both clients and exchange messages") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    withDelay {
      convs should not be empty
      zmessaging.otrClientsService.getSelfClient should eventually(be('defined))
    }

    zmessaging.convsUi.sendMessage(conv.id,"Test message")

    conv.getId shouldEqual provisionedUserId("auto2").str
    auto2 ? SendText(RConvId(provisionedUserId("auto1").str), "Test message 1") should eventually(be(Successful))

    withDelay {
      msgs.map(_.contentString) should contain allOf("Test message", "Test message 1")
      msgs.filter(_.contentString.startsWith("Test message")) foreach { msg =>
        msg.state shouldEqual Message.Status.SENT
      }
    }

    zmessaging.convsUi.sendMessage(conv.id, "Test message 2")
    auto2 ? SendText(RConvId(provisionedUserId("auto1").str), "Test message 3") should eventually(be(Successful))

    withDelay {
      msgs.map(_.contentString) should contain allOf("Test message", "Test message 1", "Test message 2", "Test message 3")
      msgs.filter(_.contentString.startsWith("Test message")) foreach { msg =>
        msg.state shouldEqual Message.Status.SENT
      }
    }
  }

  scenario("delete local session and send new message - should use new prekey") {
    val session = auto2Session

    zmessaging.otrService.sessions.deleteSession(session.getName).futureValue
    session.exists() shouldEqual false

    zmessaging.convsUi.sendMessage(conv.id, "Test message 4")

    withDelay {
      session.exists() shouldEqual true // session should be recreated
      msgs.last.contentString shouldEqual "Test message 4"
      msgs.last.state shouldEqual Message.Status.SENT
    }
  }

  scenario("receive message on recreated session") {
    val count = msgs.size
    auto2 ? SendText(RConvId(provisionedUserId("auto1").str), "Test message 5") should eventually(be(Successful))

    withDelay {
      msgs.last.contentString shouldEqual "Test message 5"
      msgs.last.state shouldEqual Message.Status.SENT
      msgs should have size (count + 1)
    }
  }

  scenario("delete local session and receive new message - should not be able to decode") {
    val session = auto2Session

    zmessaging.otrService.sessions.deleteSession(session.getName).futureValue
    session.exists() shouldEqual false

    val count = msgs.size

    auto2 ? SendText(RConvId(provisionedUserId("auto1").str), "Test message 6") should eventually(be(Successful))

    withDelay {
      msgs should have size (count + 1)
      msgs.last.state shouldEqual Message.Status.SENT
      msgs.last.msgType shouldEqual Message.Type.OTR_ERROR
    }
  }
}
