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
package com.waz.service.images

import com.waz.RobolectricUtils
import org.robolectric.annotation.Config
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers, RobolectricTests}

@Config(manifest = "tests/OtrAndroidManifest.xml", reportSdk = 17)
class ImageLoaderSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils { test =>

//  import com.waz.utils.events.EventContext.Implicits.global
//
//  var downloadRequest = Seq.empty[DownloadRequest]
//  var downloadResult = Map.empty[RAssetId, CacheEntry]
//
//  lazy val zms = new MockZMessaging() {
//    override lazy val downloader = new AssetLoaderService(network) {
//      override def download[A <: DownloadRequest](req: A, force: Boolean, withRetries: Boolean)(implicit loader: downloads.Downloader[A]): CancellableFuture[Option[CacheEntry]] = {
//        downloadRequest = downloadRequest :+ req
//        req match {
//          case WireAssetRequest(_, _, RemoteData(Some(id), _, _, _, _), _, _, _) => CancellableFuture.delayed(500.millis)(downloadResult.get(id))(Threading.Background)
//          case _ => CancellableFuture.successful(None)
//        }
//      }
//    }
//  }
//  lazy val service = zms.imageLoader
//
//  val fullFile = new File(getClass.getResource("/images/penguin.png").getFile).getAbsoluteFile
//  val mediumFile = new File(getClass.getResource("/images/penguin_240.png").getFile).getAbsoluteFile
//  val previewFile = new File(getClass.getResource("/images/penguin_128.png").getFile).getAbsoluteFile
//  lazy val previewData = Base64.encodeToString(IoUtils.toByteArray(new FileInputStream(previewFile)), Base64.NO_PADDING | Base64.NO_WRAP | Base64.NO_CLOSE)
//
//  val fullId = RAssetId()
//  val mediumId = RAssetId()
//  val previewId = RAssetId()
//
//  lazy val convId = RConvId()
//  lazy val req = BitmapRequest.Regular(300)
//  lazy val assetData = new AssetData(convId = Some(convId), mime = Mime.Image.Png, remoteId = Some(mediumId), metaData = Some(Image(Dim2(240, 240), Medium)), sizeInBytes = mediumFile.length())
//
//  before {
//    Robolectric.application.getResources.getText(R.string.cancel, "") // force resource loading
//    downloadRequest = Nil
//    downloadResult = Map.empty
//  }
//
//  after {
//    ShadowLog.stream = null
//  }
//
//  def clearImageCache(assetData: AssetData =assetData) = Await.result(zms.cache.remove(assetData.cacheKey), 5.seconds)
//
//  def prepareDownload(assetData: AssetData =assetData) = {
//    service.memoryCache.remove(assetData.id, req)
//    clearImageCache(assetData)
//
//    val entry = Await.result(zms.cache.addFile(CacheKey("test_full"), fullFile), 5.second)
//    downloadResult = Map(assetData.remoteId.get -> entry)
//    entry
//  }
//
//  class SignalListener(req: BitmapRequest, assetData: AssetData = assetData) {
//    var results = Seq.empty[BitmapResult]
//    val signal = new AssetBitmapSignal(assetData, req, service, BitmapSignal.EmptyAssetStore)
//    val obs = signal { result => results = results :+ result }
//  }
//
//  def assertResults(req: BitmapRequest, assetData: AssetData =assetData)(body: Seq[BitmapResult] => Unit) = {
//    val listener = new SignalListener(req, assetData)
//    withDelay {
//      body(listener.results.filter(_ != BitmapResult.Empty))
//    }
//  }
//
//  feature("Image downloading") {
//
//    scenario("Download image") {
//      val entry = Await.result(zms.cache.addFile(CacheKey("test_medium"), mediumFile), 5.second)
//      downloadResult = Map(assetData.remoteId.get -> entry)
//
//      val f = service.loadBitmap(assetData, req)
//      val image = Await.result(f, 5.seconds)
//      image should not be null
//      image.getWidth shouldEqual 240
//
//      downloadRequest should beMatching({
//        case Seq(WireAssetRequest(_, _, RemoteData(Some(`mediumId`), _, _, _, _), Some(`convId`), _, _)) => true
//      })
//    }
//
//    scenario("Try loading same image multiple times concurrently") {
//      val REPEATS = 9 // the mocked download takes 0.5s, so 10 repeats + overhead would result in a timeout if WAIT_TIME = 5s
//      val WAIT_TIME = 5.second
//
//      service.memoryCache.remove(assetData.id, req)
//      clearImageCache()
//
//      val entry = Await.result(zms.cache.addFile(CacheKey("test_medium"), mediumFile), WAIT_TIME)
//      downloadResult = Map(assetData.remoteId.get -> entry)
//
//      val futures = Seq.fill(REPEATS) { service.loadBitmap(assetData, req) }
//
//      CancellableFuture.sequence(futures)(Threading.Background).await()
//
//      futures foreach { f =>
//        val image = f.await()
//        image should not be null
//        image.getWidth shouldEqual 240
//      }
//
//      downloadRequest.size shouldEqual (REPEATS)
//
//      downloadRequest.foreach{ _ should beMatching({
//        case WireAssetRequest(_, _, RemoteData(Some(`mediumId`), _, _, _, _), Some(`convId`), _, _) => true
//      })}
//    }
//  }
//
//  feature("Bitmap signal") {
//
//    scenario("Download full image") {
//      prepareDownload()
//
//      assertResults(BitmapRequest.Regular(300)) { results =>
//        results should have size 1
//        downloadRequest should beMatching({
//          case Seq(WireAssetRequest(_, _, RemoteData(Some(`mediumId`), _, _, _, _), Some(`convId`), _, _)) => true
//        })
//      }
//    }
//
//    scenario("Return full from image cache") {
//      assertResults(BitmapRequest.Regular(300)) { results =>
//        results should beMatching({
//          case Seq(BitmapLoaded(b, _)) if b.getWidth == 480 => true
//        })
//        downloadRequest shouldBe empty
//      }
//    }
//
//    scenario("Download with multiple signals at the same time") {
//      prepareDownload()
//
//      val listeners = Seq.fill(2)(new SignalListener(BitmapRequest.Regular(300)))
//      withDelay {
//        listeners foreach { l =>
//          // all listeners should get preview and full image
//          l.results.filter(_ != BitmapResult.Empty) should have size 1
//        }
//      }
//
//      downloadRequest should beMatching({
//        case Seq(WireAssetRequest(_, _, RemoteData(Some(`mediumId`), _, _, _, _), Some(`convId`), _, _)) => true
//      })
//    }
//
//    scenario("Stop listening on shared signal") {
//      prepareDownload()
//
//      val l1 = new SignalListener(BitmapRequest.Regular(300))
//      val l2 = new SignalListener(BitmapRequest.Regular(300))
//
//      l1.obs.destroy()
//
//      withDelay {
//        l1.results.count(_ != BitmapResult.Empty) should be < 2
//        l2.results.filter(_ != BitmapResult.Empty) should have size 1
//      }
//    }
//
//    scenario("Restart cancelled loading on new listener") {
//      prepareDownload()
//
//      var results = Seq.empty[BitmapResult]
//      val signal = new AssetBitmapSignal(assetData, BitmapRequest.Regular(300), service, BitmapSignal.EmptyAssetStore)
//      val obs = signal { result => results = results :+ result }
//      withDelay(results.count(_ != BitmapResult.Empty) shouldEqual 1)
//      obs.destroy()
//
//      signal { result => results = results :+ result }
//
//      withDelay {
//        results.filter(_ != BitmapResult.Empty) should have size 2
//      }
//    }
//  }
//
//  feature("Gif downloading") {
//    val mediumFile = new File(getClass.getResource("/images/penguin320.gif").getFile).getAbsoluteFile
//    val previewFile = new File(getClass.getResource("/images/penguin160.gif").getFile).getAbsoluteFile
//
//    lazy val assetData = new AssetData(convId = Some(convId), mime = Mime.Image.Gif, remoteId = Some(mediumId), metaData = Some(Image(Dim2(320, 320), Medium)), sizeInBytes = mediumFile.length())
//
//    def preparePreview() = {
//      service.memoryCache.remove(assetData.id, req)
//      clearImageCache(assetData)
//
//      val entry = Await.result(zms.cache.addFile(CacheKey("test_preview"), previewFile), 5.second)
//      downloadResult = Map(assetData.remoteId.get -> entry)
//      entry
//    }
//
//    scenario("Load preview for an animated gif") {
//      preparePreview()
//
//      assertResults(BitmapRequest.Regular(320), assetData) { results =>
//        results.foreach(_ should beMatching({ // the preview may consist of any number of frames, but they all should be the same size
//          case Seq(BitmapLoaded(b, _)) if b.getWidth == 160 => true
//        }))
//      }
//    }
//  }
//
//  feature("exiff orientation") {
//
//    lazy val file = new File(getClass.getResource("/images/Landscape_5.jpg").getFile).getAbsoluteFile
//    lazy val bytes = IoUtils.toByteArray(new FileInputStream(file))
//
//    scenario("Fix orientation from byte array") {
//      ExifOrientation(new FileInputStream(file)) shouldEqual ExifInterface.ORIENTATION_TRANSPOSE
//      ExifOrientation(new ByteArrayInputStream(bytes)) shouldEqual ExifInterface.ORIENTATION_TRANSPOSE
//
//      val asset = AssetData(metaData = Some(Image(Dim2(0, 0), Medium)), sizeInBytes = bytes.length, remoteId = Some(RAssetId()), data = Some(bytes), convId = Some(RConvId()))
//
//      val bmp = Await.result(service.loadBitmap(asset, Regular(100)), 5.seconds)
//      //      TODO: fix ShadowIOBitmap to produce correct image - this works on device
//      //      bmp.getWidth shouldEqual 600
//      //      bmp.getHeight shouldEqual 450
//    }
//  }

}
