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

import com.waz.api._
import com.waz.service.RemoteZmsSpec
import com.waz.testutils.Implicits._
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import org.robolectric.annotation.Config
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers, OptionValues}

import concurrent.duration._

@Config(manifest = "tests/OtrAndroidManifest.xml")
class OtrMultipleClientsSpec extends FeatureSpec with Matchers with OptionValues with BeforeAndAfterAll with ProvisionedApiSpec with RemoteZmsSpec { test =>
  import Threading.Implicits.Background
  override val provisionFile = "/two_users_connected.json"

  lazy val convs = api.getConversations
  lazy val conv = {
    withDelay(convs should not be empty)
    convs.head
  }
  def msgs = listMessages(conv.id)

  lazy val auto2 = createRemoteZms()

  lazy val auto1_1 = createRemoteZms()
  lazy val auto1_2 = createRemoteZms()

  feature("Self messages") {

    scenario("init second client") {
      auto1_1.login(email, password)
      val cs = auto1_1.getConversations
      withDelay(cs should not be empty)
      awaitUi(2.seconds)
    }

    scenario("client fingerprints") {
      val selfLocalFingerprint = api.getSelf.flatSignal(_.getOtrClient.signal.flatMap(_.getFingerprint.signal).map(_.getRawBytes)).sink
      val selfOtherFingerprints = api.getSelf.flatSignal(_.getOtherOtrClients.signal(_.asScala)).flatMap(fingerprints).sink

      withDelay {
        selfLocalFingerprint.current.value should not be empty
        selfOtherFingerprints.current.value should have size 1
        selfOtherFingerprints.current.value(0) should not be empty
      }
    }

    scenario("send message from second client") {
      withDelay {
        msgs should have size 1
      }

      auto1_1.findConv(conv.data.remoteId) map { conv => zmessaging.convsUi.sendMessage(conv.id, "self message 1") }

      withDelay {
        msgs should have size 2
        msgs.last.contentString shouldEqual "self message 1"
      }
    }
  }

  feature(s"Remote otr clients") {

    scenario("init remote client") {
      val f = auto2.login(provisionedEmail("auto2"), "auto2_pass")
      withDelay(f.isCompleted shouldEqual true)
      val cs = auto2.getConversations
      withDelay(cs should not be empty)
      awaitUi(2.seconds)
    }

    scenario("client fingerprints") {
      val auto2clients = conv.getOtherParticipant.getOtrClients
      lazy val fingerprint = auto2clients.head.getFingerprint

      withDelay {
        auto2clients should not be empty
        fingerprint should not be empty
        fingerprint.get.getRawBytes should not be empty
      }
    }

    scenario("send message to remote client") {
      val count = msgs.size

      zmessaging.convsUi.sendMessage(conv.id, "test message 1")

      withDelay {
        msgs should have size (count + 1)
        msgs.last.contentString shouldEqual "test message 1"
      }
    }

    scenario("receive message from remote client") {
      val count = msgs.size

      auto1_1.findConv(conv.data.remoteId) map { conv => zmessaging.convsUi.sendMessage(conv.id, "remote message 1") }

      withDelay {
        msgs should have size (count + 1)
        msgs.last.contentString shouldEqual "remote message 1"
      }
    }
  }

  def fingerprints(clients: Vector[OtrClient]) = Signal.foldLeft(clients.map(_.getFingerprint.signal):_*)(Vector.empty[Array[Byte]])((l, r) => l :+ r.getRawBytes)
}
