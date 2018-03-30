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

import java.io.InputStream

import akka.pattern.ask
import android.content.Context
import com.waz.api.ProgressIndicator.State
import com.waz.api._
import com.waz.content.WireContentProvider.CacheUri
import com.waz.model.{AssetId, CacheKey, Mime}
import com.waz.provision.ActorMessage.{AwaitSyncCompleted, Login, SendAsset, Successful}
import com.waz.testutils.DefaultPatienceConfig
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.utils.wrappers.URI
import com.waz.utils.{IoUtils, returning}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Random

class AssetLoadingSpec extends FeatureSpec with Matchers with BeforeAndAfter with OptionValues with ProvisionedApiSpec with ThreadActorSpec with ScalaFutures with DefaultPatienceConfig {
  override val provisionFile: String = "/two_users_connected.json"

  scenario("init remote") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    auto2 ? AwaitSyncCompleted should eventually(be(Successful))
  }

  feature("Loading previously uploaded asset") {

    lazy val asset = new com.waz.api.impl.Asset(messages(1).assetId, messages(1).id)

    scenario("Send asset") {
      val asset: impl.AssetForUpload = new impl.AssetForUpload(AssetId()) {
        override def name: Future[Option[String]] = Future successful Some("file.dat")
        override def sizeInBytes: Future[Option[Long]] = Future successful None
        override def openDataStream(context: Context): InputStream = getClass.getResourceAsStream("/assets/ScalaReference.pdf")
        override def mimeType: Future[Mime] = Future successful Mime.Default
      }
      val errorHandler = new MessageContent.Asset.ErrorHandler {
        override def noWifiAndFileIsLarge(sizeInBytes: Long, net: NetworkMode, answer: MessageContent.Asset.Answer): Unit = ()
      }
      zmessaging.convsUi.sendMessage(conv.id, asset, errorHandler)

      soon {
        messages should not be empty
        messages(1).msgType shouldEqual Message.Type.ANY_ASSET
      }
    }

    scenario("Loading just sent asset should return data from local cache") {
      awaitUiFuture(contentCacheKey(asset))(1.second).toOption should be('defined)
    }

    scenario("Download previously uploaded asset from backend") {
      val key = contentCacheKey(asset).await()
      val entry = zmessaging.cache.getEntry(key).await().value
      IoUtils.toByteArray(entry.inputStream).toSeq shouldEqual IoUtils.toByteArray(getClass.getResourceAsStream("/assets/ScalaReference.pdf")).toSeq
    }
  }

  feature("Loading received asset") {
    lazy val assetData = returning(Array.ofDim[Byte](100000))(Random.nextBytes)

    scenario("Receive an asset message") {
      val fromBefore = messages.size

      auto2 ? SendAsset(conv.data.remoteId, assetData, "application/octet-stream", "random.dat") should eventually(beMatching { case Successful(_) => })

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
    }

    scenario("Download received asset") {
      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      val cacheKey = contentCacheKey(asset).await()

      asset.getStatus shouldEqual AssetStatus.DOWNLOAD_DONE

      val entry = zmessaging.cache.getEntry(cacheKey).futureValue
      entry should be('defined)
      entry.map(_.data.mimeType) shouldEqual Some(Mime.Default)
      entry.flatMap(_.data.fileName) shouldEqual Some("random.dat")
      entry.map(_.length) shouldEqual Some(100000)
      IoUtils.toByteArray(entry.get.inputStream).toSeq shouldEqual assetData.toSeq
    }

    scenario("Report download progress for bigger files") {
      val fromBefore = messages.size
      val size = 3 * 1024 * 1024
      val encryptedSize = 3145760

      auto2 ? SendAsset(conv.data.remoteId, returning(Array.ofDim[Byte](3 * 1024 * 1024))(Random.nextBytes), Mime.Default.str, "random1.dat") should eventually(beMatching { case Successful(_) => })

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      soon {
        messages should have size (fromBefore + 1)
        messages.last.msgType shouldEqual Message.Type.ANY_ASSET
        asset.getName shouldEqual "random1.dat"
        asset.getMimeType shouldEqual "application/octet-stream"
        asset.getStatus shouldEqual AssetStatus.UPLOAD_DONE
        asset.getSizeInBytes shouldEqual size
      }

      @volatile var states = Seq.empty[AssetStatus]
      val l1 = new UpdateListener {
        override def updated(): Unit = {
          states = states :+ asset.getStatus
        }
      }
      asset.addUpdateListener(l1)

      val progress = asset.getDownloadProgress
      progress.isIndefinite shouldEqual false

      @volatile var updates = Seq.empty[(Long, Long, ProgressIndicator.State, Boolean)]
      val listener = new UpdateListener {
        override def updated(): Unit = {
          updates = updates :+ (progress.getProgress, progress.getTotalSize, progress.getState, progress.isIndefinite)
        }
      }
      progress.addUpdateListener(listener)

      // download
      val key = contentCacheKey(asset).await()

      // check asset download state, it should go directly to DOWNLOAD_DONE
      info("asset states: " + states)
      states shouldEqual Seq(AssetStatus.DOWNLOAD_IN_PROGRESS, AssetStatus.DOWNLOAD_DONE)

      // check progress updates
      updates foreach { _._4 shouldEqual false }
      val withSize = updates.filter(_._2 > 0)
      val running = withSize.filter(_._3 == State.RUNNING)
      running.size should be > 5
      running foreach(_._2 shouldEqual encryptedSize)
      running.map(_._1) should be(sorted)
      val completed = withSize.filter(_._3 == State.COMPLETED)
      completed should not be empty
      completed.last._1 shouldEqual encryptedSize
      completed.last._2 shouldEqual encryptedSize

      zmessaging.cache.remove(key).await()
    }

    scenario("Cancel download right away - before it actually starts downloading") {
      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      val downloadFuture = contentCacheKey(asset)

      soon {
        asset.getStatus shouldEqual AssetStatus.DOWNLOAD_IN_PROGRESS
      }
      val progress = asset.getDownloadProgress
      progress.cancel()

      intercept[Exception] {
        downloadFuture.await() // download should be cancelled (throws exception here)
      }

      progress.getState shouldEqual State.CANCELLED
      asset.getStatus shouldEqual AssetStatus.UPLOAD_DONE
    }

    scenario("Cancel running download") {
      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      val downloadFuture = contentCacheKey(asset)

      soon {
        asset.getStatus shouldEqual AssetStatus.DOWNLOAD_IN_PROGRESS
      }
      val progress = asset.getDownloadProgress
      soon {
        progress.getState shouldEqual State.RUNNING
        progress.getTotalSize should be > 0L
        progress.getProgress should be > 0L
      }
      progress.cancel()

      intercept[Exception] {
        downloadFuture.await() // download should be cancelled (throws exception here)
      }

      progress.getState shouldEqual State.CANCELLED
      asset.getStatus shouldEqual AssetStatus.UPLOAD_DONE
    }
  }

  lazy val conversations = api.getConversations
  lazy val self = api.getSelf
  lazy val conv = conversations.head
  def messages = listMessages(conv.id)

  lazy val auto2 = registerDevice("auto2")

  def contentCacheKey(asset: Asset) = {
    val p = Promise[CacheKey]
    asset.getContentUri(new Asset.LoadCallback[URI]() {
      override def onLoaded(uri: URI): Unit = CacheUri.unapply(context)(uri) match {
        case Some(key) => p.success(key)
        case None => p.failure(new Exception(s"Returned uri is not CacheUri: $uri"))
      }
      override def onLoadFailed(): Unit = p.failure(new Exception("loading failed"))
    })
    p.future
  }
}

