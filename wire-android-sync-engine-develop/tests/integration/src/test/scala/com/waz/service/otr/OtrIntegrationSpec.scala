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

import akka.pattern.ask
import android.graphics.{Bitmap, BitmapFactory}
import com.waz.api.Message.Status
import com.waz.api.OtrClient.DeleteCallback
import com.waz.api._
import com.waz.model.AssetId
import com.waz.provision.ActorMessage._
import com.waz.testutils.BitmapSpy
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.utils.IoUtils
import org.robolectric.annotation.Config
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}
import org.threeten.bp.Instant

import scala.concurrent.duration._
import scala.util.Try

@Config(manifest = "tests/OtrAndroidManifest.xml")
class OtrIntegrationSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with ProvisionedApiSpec with ProcessActorSpec { test =>

  override val provisionFile = "/two_users_connected.json"
  override val autoLogin = false

  lazy val convs = api.getConversations
  lazy val conv = {
    withDelay { convs should not be empty }
    convs.get(0)
  }
  def msgs = listMessages(conv.id)

  lazy val process = Try(awaitRemote("SE worker 1", 5.seconds)).getOrElse(registerProcess("auto2_local_process"))

  lazy val auto2 = registerDevice("otr_auto2", process)
  lazy val auto2_1 = registerDevice("otr_auto2_1")
  lazy val auto1_1 = registerDevice("otr_auto1_1")
  lazy val auto1_2 = registerDevice("otr_auto1_2")

  lazy val auto1Id = provisionedUserId("auto1")
  lazy val auto2Id = provisionedUserId("auto2")

  @volatile var refreshInterval = 1.day

  feature("Clients registry") {

    scenario("Register client on first start") {
      awaitUi(3.seconds)
      login()
      withDelay {
        zmessaging.otrClientsService.getSelfClient should eventually(be('defined))
      }
    }

    scenario("Fetch client location") {
      val client = api.getSelf.getOtrClient
      withDelay {
        client should not be empty
        client.get.getRegLocation should not be null
        client.get.getRegLocation.getDisplayName shouldEqual "Berlin, DE"
      }
    }

    scenario("Fetch client reg time") {
      val client = api.getSelf.getOtrClient
      withDelay {
        client should not be empty
        client.get.getRegTime should be > Instant.now.minusSeconds(300)
      }
    }

    scenario("Update client label") {
      val client = api.getSelf.getOtrClient
      withDelay {
        client should not be empty
        client.get.getLabel should not be empty
        client.get.getModel should not be empty
      }
      client.get.setLabel("test label")
      awaitUi(1.second) // wait for sync request to be processed
      withDelay {
        client.get.getLabel shouldEqual "test label"
      }
    }
  }

  scenario("init remote process") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    awaitUi(3.seconds)
  }

  feature("Message sending") {

    scenario("Send otr message") {
      withDelay(msgs should have size 1)

      zmessaging.convsUi.sendMessage(conv.id, "Test message")

      withDelay {
        msgs should have size 2
        msgs.last.contentString shouldEqual "Test message"
        msgs.last.state shouldEqual Status.SENT
      }
    }

    scenario("Send multiple messages in correct order") {
      withDelay(msgs should have size 2)

      for (i <- 0 until 15) {
        zmessaging.convsUi.sendMessage(conv.id, s"ordered message $i")
        awaitUi(1.millis)
      }

      withDelay {
        msgs should have size 17
        msgs.drop(2).zipWithIndex foreach { case (msg, i) =>
          msg.contentString shouldEqual s"ordered message $i"
          msg.state shouldEqual Status.SENT
        }
      }
    }

    scenario("Receive otr message") {
      val count = msgs.size
      conv.getId shouldEqual auto2Id.str
      auto2 ? SendText(conv.data.remoteId, "Test message 3") should eventually(be(Successful))


      withDelay {
        msgs should have size (count + 1)
        withClue(msgs) {

          val last = msgs.last
          last.contentString shouldEqual "Test message 3"
          last.state shouldEqual Status.SENT
        }
      }
    }

    scenario("Reset session with auto2") {
      val auto2User = api.getUser(provisionedUserId("auto2").str)
      val clients = auto2User.getOtrClients
      withDelay {
        clients should not be empty
      }
      val client = clients.head
      var success = false
      var error = Option.empty[String]
      client.resetSession(new OtrClient.ResetCallback() {
        override def onSessionReset(client: OtrClient): Unit = success = true
        override def onSessionResetFailed(code: Int, message: String, label: String): Unit = error = Some(message)
      })
      withDelay {
        error shouldBe empty
        success shouldEqual true
      }
    }
  }

  feature("Pings") {

    scenario("Send ping through otr") {
      val count = msgs.size
      conv.knock()

      lazy val last = msgs.last

      withDelay {
        msgs should have size (count + 1)
        last.msgType shouldEqual Message.Type.KNOCK
        last.state shouldEqual Status.SENT
      }
    }

    scenario("Receive ping") {
      val count = msgs.size
      auto2 ? Knock(conv.data.remoteId) should eventually(be(Successful))

      lazy val last = msgs.last

      withDelay {
        msgs should have size (count + 1)
        last.msgType shouldEqual Message.Type.KNOCK
        last.state shouldEqual Status.SENT
        last.userId shouldEqual auto2Id
      }
    }
  }

  feature("Asset messages") {
    scenario("Send image asset") {
      val count = msgs.size
      val bmp = BitmapFactory.decodeStream(getClass.getResourceAsStream("/images/penguin.png"))
      zmessaging.convsUi.sendMessage(conv.id, ImageAssetFactory.getImageAsset(bmp))

      withDelay {
        msgs should have size (count + 1)
        val last = msgs.last
        last.msgType shouldEqual Message.Type.ASSET
        last.state shouldEqual Status.SENT
      }
      val asset = new com.waz.api.impl.ImageAsset(msgs.last.assetId)
      withDelay {
        asset.data.remoteData shouldBe 'defined
      }
    }

    scenario("download just uploaded image") {
      awaitUi(1.second)
      val asset = new com.waz.api.impl.ImageAsset(msgs.last.assetId)
      val asset1 = asset.data.copy(id = AssetId()) // create asset copy to make sure it is not cached
      zmessaging.assets.updateAsset(asset1.id, _ => asset1)

      val a = api.ui.images.getImageAsset(asset1.id)

      var full = Option.empty[Bitmap]
      var failed = false

      a.getBitmap(500, new BitmapCallback() {
        override def onBitmapLoaded(b: Bitmap): Unit = full = Some(b)
        override def onBitmapLoadingFailed(): Unit = failed = true
      })

      withDelay {
        failed shouldEqual false
        full should be('defined)
      }

      info(s"full: ${full.map(b => (b.getWidth, b.getHeight))}")
    }

    scenario("Receive image") {
      val count = msgs.size
      auto2 ? SendImageData(conv.data.remoteId, IoUtils.toByteArray(getClass.getResourceAsStream("/images/penguin.png"))) should eventually(be(Successful))

      withDelay {
        msgs should have size (count + 1)
        val last = msgs.last
        last.msgType shouldEqual Message.Type.ASSET
        last.state shouldEqual Status.SENT
      }

      val img = new com.waz.api.impl.ImageAsset(msgs.last.assetId)
      withDelay {
        img.isEmpty shouldEqual false
      }

      val bitmap = new BitmapSpy(img, 300)
      withDelay {
        bitmap.failed shouldEqual false
        bitmap.result shouldBe 'defined
      }

      info(s"received image size: (${bitmap.result.get.getWidth}, ${bitmap.result.get.getHeight})")
    }
  }

  feature("Add second device for auto2") {

    scenario("init remote process auto2_1") {
      auto2_1 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
      awaitUi(3.seconds)
    }

    scenario("Send message") {
      zmessaging.convsUi.sendMessage(conv.id, "Test message 4")
      withDelay {
        val last = msgs.last
        last.contentString shouldEqual "Test message 4"
        last.state shouldEqual Status.SENT
      }
    }

    scenario("Receive message from new device") {
      awaitUi(1.second)
      val count = msgs.size
      auto2_1 ? SendText(conv.data.remoteId, "Test message 5") should eventually(be(Successful))

      withDelay {
        val last = msgs.last
        last.contentString shouldEqual "Test message 5"
        last.state shouldEqual Status.SENT
        msgs should have size (count + 1)
      }
      auto2_1 ? SendText(conv.data.remoteId, "Test message 5_1") should eventually(be(Successful))

      withDelay {
        val last = msgs.last
        last.contentString shouldEqual "Test message 5_1"
        last.state shouldEqual Status.SENT
        withClue(msgs.map(_.contentString + "\n")) {
          msgs should have size (count + 2)
        }
      }
    }
  }

  feature("Add second device for current user") {

    scenario("init remote device") {
      auto1_1 ? Login(provisionedEmail("auto1"), "auto1_pass") should eventually(be(Successful))
      awaitUi(3.seconds)
    }

    scenario("clients list should be updated") {
      val clients = api.getSelf.getOtherOtrClients
      withDelay {
        clients should have size 1
        clients.head.getLabel shouldEqual "otr_auto1_1"
        clients.head.getModel shouldEqual "otr_auto1_1"
      }
    }

    scenario("set label on remote client") {
      auto1_1 ? SetDeviceLabel("Label1") should eventually(be(Successful))
    }

    scenario("Receive message sent from second device") {
      val count = msgs.size

      auto1_1 ? SendText(conv.data.remoteId, "Test message 6") should eventually(be(Successful))

      withDelay {
        val last = msgs.last
        last.contentString shouldEqual "Test message 6"
        last.state shouldEqual Status.SENT
        last.userId shouldEqual provisionedUserId("auto1")
        msgs should have size (count + 1)
      }
    }

    scenario("clients label should be updated after some time") {
      awaitUi(1.second)
      refreshInterval = 1.second
      awaitUi(1.second)
      val clients = api.getSelf.getOtherOtrClients
      try withDelay {
        clients should have size 1
        clients.head.getLabel shouldEqual "Label1"
        clients.head.getModel shouldEqual "otr_auto1_1"
      } finally refreshInterval = 1.day
    }

    scenario("Receive last read event from second device") {
      val last = msgs.last
      info(s"last: $last")
      info(s"lastRead: ${lastRead(conv.id)}")
      withDelay {
        lastRead(conv.id) shouldEqual last.time
      } (10.seconds)
    }

    scenario("fetch location and time for added client") {
      val clients = api.getSelf.getOtherOtrClients
      withDelay {
        clients should have size 1
        clients.head.getRegTime should be > Instant.now.minusSeconds(300)
        clients.head.getRegLocation.getDisplayName shouldEqual "Berlin, DE"
      }
    }

    scenario("change label for remote client") {
      val clients = api.getSelf.getOtherOtrClients
      withDelay { clients should have size 1}
      val client = clients.head
      client.setLabel("Remote client")
      awaitUi(1.second)
      withDelay {
        client.getLabel shouldEqual "Remote client"
      }
    }

    scenario("Reset session for second device") {
      val clients = api.getSelf.getOtherOtrClients
      withDelay {
        clients should not be empty
      }
      val client = clients.head
      var success = false
      var error = Option.empty[String]
      client.resetSession(new OtrClient.ResetCallback() {
        override def onSessionReset(client: OtrClient): Unit = success = true
        override def onSessionResetFailed(code: Int, message: String, label: String): Unit = error = Some(message)
      })
      withDelay {
        error shouldBe empty
        success shouldEqual true
      }
    }
  }

  feature("Asset messages2") {
    scenario("Send image asset2") {
      val count = msgs.size
      val bmp = BitmapFactory.decodeStream(getClass.getResourceAsStream("/images/penguin.png"))
      zmessaging.convsUi.sendMessage(conv.id, ImageAssetFactory.getImageAsset(bmp))

      withDelay {
        msgs should have size (count + 1)
        msgs.last.msgType shouldEqual Message.Type.ASSET
        msgs.last.state shouldEqual Status.SENT
        // TODO: assert image data can be decoded
      }
    }

    scenario("Receive image2") {
      val count = msgs.size
      auto2 ? SendImageData(conv.data.remoteId, IoUtils.toByteArray(getClass.getResourceAsStream("/images/penguin.png"))) should eventually(be(Successful))

      withDelay {
        msgs should have size (count + 1)
        val last = msgs.last
        last.msgType shouldEqual Message.Type.ASSET
        last.state shouldEqual Status.SENT
      }

      val img = new com.waz.api.impl.ImageAsset(msgs.last.assetId)
      withDelay {
        img.isEmpty shouldEqual false
      }

      val bitmap = new BitmapSpy(img, 300)
      withDelay {
        bitmap.failed shouldEqual false
        bitmap.result shouldBe 'defined
      }

      info(s"received image size: (${bitmap.result.get.getWidth}, ${bitmap.result.get.getHeight})")
    }
  }

  feature("Clients management") {

    lazy val client = api.getSelf.getOtrClient
    lazy val clients = api.getSelf.getOtherOtrClients

    scenario("Force refresh clients list") {
      zmessaging.sync.syncSelfClients()
      awaitUi(3.seconds)
    }

    scenario("Load otr clients list") {
      withDelay {
        clients should have size 1
        clients.head.getVerified shouldEqual Verification.UNVERIFIED
        clients.head.getLabel should not be empty
        clients.head.getModel should not be empty
        clients.head.getRegTime should be > Instant.now.minusSeconds(300)
        clients.head.getRegLocation.getDisplayName shouldEqual "Berlin, DE"
        client should not be empty
        client.get.getVerified shouldEqual Verification.VERIFIED
        client.get.getLabel should not be empty
        client.get.getModel should not be empty
        client.get.getRegTime should be > Instant.now.minusSeconds(300)
        client.get.getRegLocation.getDisplayName shouldEqual "Berlin, DE"
      }
    }

    scenario("Delete other client") {
      val other = clients.headOption
      val spy = new DeleteSpy
      other shouldBe 'defined
      other.get.delete("auto1_pass", spy)

      withDelay {
        spy.failed shouldBe empty
        spy.deleted should not be empty
        clients should be(empty)
      }
    }
  }

  feature("Discover deletion on removed client") {
    lazy val client = api.getSelf.getOtrClient

    scenario("init remote device") {
      auto1_2 ? Login(provisionedEmail("auto1"), "auto1_pass") should eventually(be(Successful))
      val clients = api.getSelf.getOtherOtrClients
      withDelay {
        withClue(clients.map(_.getLabel).mkString(",")) {
          clients should have size 1
          clients.head.getLabel shouldEqual "otr_auto1_2"
          clients.head.getModel shouldEqual "otr_auto1_2"
        }
      }
    }

    scenario("force logout when current device is removed from remote side") {
      val self = api.getSelf
      withDelay(client.isEmpty shouldEqual false)
      auto1_2 ? DeleteDevice(client.get.asInstanceOf[com.waz.api.impl.otr.OtrClient].clientId.str, "auto1_pass") should eventually(be(Successful))

      withDelay {
        self.isLoggedIn shouldEqual false
      }
    }
  }
}

class DeleteSpy extends DeleteCallback {
  var deleted = Option.empty[OtrClient]
  var failed = Option.empty[String]

  override def onClientDeleted(client: OtrClient): Unit = deleted = Some(client)
  override def onDeleteFailed(error: String): Unit = failed = Some(error)
}
