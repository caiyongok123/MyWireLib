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
package com.waz.messages

import akka.pattern.ask
import com.waz.api.Asset.LoadCallback
import com.waz.api._
import com.waz.model.{AssetStatus => _, MessageContent => _, _}
import com.waz.provision.ActorMessage._
import com.waz.testutils.DefaultPatienceConfig
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.utils.wrappers.URI
import com.waz.utils.{IoUtils, returning}
import org.robolectric.shadows.ShadowContentResolver
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._
import scala.util.Random

class AssetReceivingSpec extends FeatureSpec with Matchers with BeforeAndAfter with OptionValues with ProvisionedApiSpec with ThreadActorSpec with ScalaFutures with Inspectors with DefaultPatienceConfig {

  lazy val conversations = api.getConversations
  lazy val self = api.getSelf
  lazy val conv = conversations.head
  def messages = listMessages(conv.id)

  lazy val auto2 = registerDevice("auto2")

  override val provisionFile: String = "/two_users_connected.json"

  before {
    ShadowContentResolver.reset()
    zmessaging.network.networkMode ! NetworkMode.WIFI
  }

  scenario("init remote") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    auto2 ? AwaitSyncCompleted should eventually(be(Successful))
    (messages should have size 1).soon
  }

  feature("Any asset") {
    lazy val assetData = returning(Array.ofDim[Byte](100000))(Random.nextBytes)

    scenario("Receive an asset message") {
      val fromBefore = messages.size

      val Successful(mid) = (auto2 ? SendAsset(conv.data.remoteId, assetData, "application/octet-stream", "random.dat")).await()

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      soon {
        messages should have size (fromBefore + 1)
        messages.last.msgType shouldEqual Message.Type.ANY_ASSET
        asset.getName shouldEqual "random.dat"
        asset.getMimeType shouldEqual "application/octet-stream"
        asset.getStatus shouldEqual AssetStatus.UPLOAD_DONE
        asset.getSizeInBytes shouldEqual 100000
      }

      auto2 ? CancelAssetUpload(MessageId(mid)) should eventually(be(Successful))

      idle(1.second)

      soon {
        messages should have size (fromBefore + 1)
        asset.getStatus shouldEqual AssetStatus.UPLOAD_DONE
      }
    }

    scenario("Receive an asset message that gets cancelled") {
      val fromBefore = messages.size

      val Successful(mid) = (auto2 ? SendAsset(conv.data.remoteId, assetData, "application/octet-stream", "random.dat", true)).await()

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      soon {
        messages should have size (fromBefore + 1)
        messages.last.msgType shouldEqual Message.Type.ANY_ASSET
        messages.last.state shouldEqual Message.Status.SENT
        asset.getName shouldEqual "random.dat"
        asset.getMimeType shouldEqual "application/octet-stream"
        asset.getStatus shouldEqual AssetStatus.UPLOAD_IN_PROGRESS
        asset.getSizeInBytes shouldEqual 100000
      }

      auto2 ? CancelAssetUpload(MessageId(mid)) should eventually(be(Successful))

      soon {
        asset.getStatus shouldEqual AssetStatus.UPLOAD_CANCELLED
        messages should have size fromBefore
      }
      zmessaging.assetsStorage.get(AssetId(mid)).await() shouldBe None
      zmessaging.messagesStorage.get(MessageId(mid)).await() shouldBe None
    }

    scenario("Received message should stay in a place where initial placeholder was added") {
      val fromBefore = messages.size

      val Successful(mid) = (auto2 ? SendAsset(conv.data.remoteId, assetData, "application/octet-stream", "random.dat", true)).await()

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      soon {
        messages should have size (fromBefore + 1)
        messages.last.msgType shouldEqual Message.Type.ANY_ASSET
        asset.getStatus shouldEqual AssetStatus.UPLOAD_IN_PROGRESS
      }

      zmessaging.convsUi.sendMessage(conv.id, "txt message 1")
      zmessaging.convsUi.sendMessage(conv.id, "txt message 2")

      within(20.seconds)(asset.getStatus shouldEqual AssetStatus.UPLOAD_DONE)

      forAsLongAs(3.seconds, after = 1.second) {
        messages should have size (fromBefore + 3)
        messages.last.msgType shouldEqual Message.Type.TEXT
      }
    }
  }

  feature("Video messages") {
    lazy val assetData = IoUtils.toByteArray(getClass.getResourceAsStream("/assets/video_hd.mp4"))

    scenario("Receive video asset message") {
      val fromBefore = messages.size

      val Successful(mid) = (auto2 ? SendAsset(conv.data.remoteId, assetData, "video/mp4", "movie.mp4")).await()

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      soon {
        messages should have size (fromBefore + 1)
        messages.last.msgType shouldEqual Message.Type.VIDEO_ASSET
        asset.getName shouldEqual "movie.mp4"
        asset.getMimeType shouldEqual "video/mp4"
        asset.getStatus shouldEqual AssetStatus.UPLOAD_DONE
        asset.getSizeInBytes shouldEqual assetData.length
        messages.last.imageDimensions.get.width shouldEqual 1080
        messages.last.imageDimensions.get.height shouldEqual 1920
        asset.getDuration.getSeconds shouldEqual 3
      }
    }

    scenario("Refresh metadata from downloaded data if received asset had none") {
      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      zmessaging.assetsStorage.updateAsset(AssetId(asset.getId), _.copy(metaData = None)).await()

      soon { asset.getWidth shouldEqual 0 }

      asset.getContentUri(new LoadCallback[URI] {
        override def onLoaded(a: URI): Unit = info(s"loaded $a")
        override def onLoadFailed(): Unit = fail("load failed")
      })

      withDelay {
        asset.getStatus shouldEqual AssetStatus.DOWNLOAD_DONE
        asset.getWidth shouldEqual 1080
        asset.getHeight shouldEqual 1920
        asset.getDuration.getSeconds shouldEqual 3
      } (15.seconds)
    }
  }

  feature("Audio message") {
    lazy val assetData = IoUtils.toByteArray(getClass.getResourceAsStream("/assets/audio.m4a"))

    scenario("Receive audio asset message") {
      val fromBefore = messages.size

      val Successful(mid) = (auto2 ? SendAsset(conv.data.remoteId, assetData, "audio/x-m4a", "audio.m4a")).await()

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      soon {
        messages should have size (fromBefore + 1)
        messages.last.msgType shouldEqual Message.Type.AUDIO_ASSET
        asset.getName shouldEqual "audio.m4a"
        asset.getMimeType shouldEqual "audio/x-m4a"
        asset.getStatus shouldEqual AssetStatus.UPLOAD_DONE
        asset.getSizeInBytes shouldEqual assetData.length
        asset.getDuration.getSeconds shouldEqual 4
        asset.getAudioOverview.isEmpty shouldBe false
      }
    }
  }
}
