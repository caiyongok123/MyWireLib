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

import java.io.{ByteArrayInputStream, File}

import com.waz.api._
import com.waz.service.RemoteZmsSpec
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.utils.IoUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}

import scala.util.Random

class OtrRecoverySpec extends FeatureSpec with Matchers with BeforeAndAfterAll with ProvisionedApiSpec with RemoteZmsSpec with ScalaFutures { test =>
  import com.waz.threading.Threading.Implicits.Background
  override val provisionFile = "/two_users_connected.json"

  lazy val convs = api.getConversations
  lazy val conv = {
    withDelay(convs should not be empty)
    convs.head
  }
  def msgs = listMessages(conv.id)

  lazy val tag = Random.nextLong().toHexString
  lazy val auto2 = createRemoteZms(tag)
  lazy val cryptoDir = auto2.account.get.cryptoBox.cryptoBoxDir

  lazy val auto2_1 = createRemoteZms(tag)

  lazy val auto2UserId = provisionedUserId("auto2")
  lazy val auto2User = api.getUser(provisionedUserId("auto2").str)
  lazy val auto2Clients = auto2User.getOtrClients

  feature("recover CryptoBox on login") {

    scenario("init both clients and exchange messages") {
      awaitUiFuture(auto2.login(provisionedEmail("auto2"), "auto2_pass"))
      withDelay {
        convs should not be empty
        zmessaging.otrClientsService.getSelfClient should eventually(be('defined))
      }

      zmessaging.convsUi.sendMessage(conv.id, "Test message")

      conv.getId shouldEqual provisionedUserId("auto2").str
      awaitUiFuture(auto2.findConv(conv.data.remoteId).map { c =>
        zmessaging.convsUi.sendMessage(c.id, "Test message 1")
      })

      withDelay {
        msgs.map(_.contentString) should contain allOf("Test message", "Test message 1")
        msgs.filter(_.contentString.startsWith("Test message")) foreach { msg =>
          msg.state shouldEqual Message.Status.SENT
        }

        auto2Clients should have size 1
      }
    }

    scenario("logout auto2") {
      val auto2Self = auto2.getSelf
      auto2Self.isLoggedIn shouldEqual true
      cryptoDir should exist
      val cryptobox = auto2.account.get.cryptoBox
      val db = auto2.account.get.storage.db
      val globalDb = auto2.account.get.global.storage

      info("clientId: " + auto2.zmessaging.futureValue.get.clientId)

      auto2.logout()

      withDelay {
        auto2Self.isLoggedIn shouldEqual false
      }

      auto2.onPause()
      auto2.onDestroy()
      cryptobox.close().await()
      db.close().await()
      globalDb.close().await()
    }

    scenario("corrupt auto2 otr identity and login again in new process") {
      val identity = new File(new File(cryptoDir, "identities"), "local")
      identity should exist
      IoUtils.copy(new ByteArrayInputStream("broken".getBytes), identity)

      var auto2Self = Option.empty[Self]

      auto2_1.onInit(new InitListener {
        override def onInitialized(user: Self): Unit = auto2Self = Some(user)
      })

      soon { auto2Self.map(_.isLoggedIn) shouldEqual Some(false) }

      awaitUiFuture(auto2_1.login(provisionedEmail("auto2"), "auto2_pass"))

      auto2Self.map(_.isLoggedIn) shouldEqual Some(true) // login is successful
      auto2Self.get.getClientRegistrationState shouldEqual ClientRegistrationState.REGISTERED

      auto2_1.ui.currentZms.head.await() shouldBe defined
      auto2_1.ui.currentZms.head.await().get.cryptoBox.cryptoBox.futureValue shouldBe 'defined
      auto2_1.account shouldBe defined

      info("clientId: " + auto2_1.zmessaging.futureValue.get.clientId)
    }

    scenario("receive new message") {
      val auto2Self = auto2_1.getSelf

      // auto2 should be logged in
      withDelay {
        auto2Self.isLoggedIn shouldEqual true
        auto2Self.getClientRegistrationState shouldEqual ClientRegistrationState.REGISTERED
      }

      val conv2 = awaitUiFuture(auto2_1.findConv(conv.data.remoteId)).get
      def msgs2 = listMessages(conv2.id)

      zmessaging.convsUi.sendMessage(conv.id, "Test message 2")

      withDelay {
        auto2Clients should have size 2 // login with broken cryptobox created new device
        msgs2.last.contentString shouldEqual "Test message 2"
      }
    }
  }

}
