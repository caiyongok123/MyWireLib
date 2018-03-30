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

import java.io.InputStream

import android.graphics.{Bitmap, BitmapFactory}
import com.waz.RobolectricUtils
import com.waz.api.impl.LocalBitmapAsset
import com.waz.bitmap.BitmapUtils.Mime
import com.waz.model._
import com.waz.service.images.ImageLoader.Metadata
import com.waz.testutils.Matchers._
import com.waz.testutils.{DefaultPatienceConfig, MockUiModule, MockZMessaging}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Await
import scala.concurrent.duration._

@Ignore class ImageAssetGeneratorSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with BeforeAndAfter with OptionValues with RobolectricTests with RobolectricUtils with ScalaFutures with DefaultPatienceConfig { test =>

  lazy val zms = new MockZMessaging()
  lazy val generator = zms.assetGenerator
  lazy val ui = new MockUiModule(zms)

  def imageAssetFor(bitmap: Bitmap): AssetData = ui.images.getOrCreateImageAssetFrom(bitmap).asInstanceOf[LocalBitmapAsset].data

  def imageAssetFor(imageName: String): AssetData = imageAssetFor(BitmapFactory.decodeStream(getClass.getResourceAsStream(s"/images/$imageName")))

  def imageAssetFor(stream: => InputStream): AssetData = imageAssetFor(BitmapFactory.decodeStream(stream))

  def generate(asset: AssetData, profilePicture: Boolean = false) = {
    Await.result(generator.generateWireAsset(asset, profilePicture = true), 25.seconds)
  }

  feature("profile image generation") {


    scenario("generate assets for small png image") {
      val medium = generate(imageAssetFor("penguin_128.png"))
      info(s"generated: $medium")

//      small.width shouldEqual 128
//      small.height shouldEqual 128
//      small.mime shouldEqual Mime.Jpg
//      small.tag shouldEqual "smallProfile"
//      small.size should be <= 100 * 1024

      medium.width shouldEqual 128
      medium.tag shouldEqual "medium"

      val mediumEntry = Await.result(zms.cache.getEntry(medium.cacheKey), 10.seconds).value

//      smallEntry.cacheFile should exist
//      smallEntry.cacheFile.length shouldEqual small.size
      mediumEntry.cacheFile should exist
      mediumEntry.cacheFile.length shouldEqual medium.size
    }

    scenario("generate assets for big png image") {
      val medium = generate(imageAssetFor("big.png"))

      info(s"generated assets: $medium")

//      small.width shouldEqual 280
//      small.height shouldEqual 280
//      small.mime shouldEqual Mime.Jpg
//      small.tag shouldEqual "smallProfile"
//      small.size should be < 25 * 1024

      medium.width shouldEqual 1920
      medium.height shouldEqual 1080
      medium.tag shouldEqual "medium"
    }

    scenario("generate assets for huge jpg image") {
      val entry = imageAssetFor(Bitmap.createBitmap(10 * 1024, 3280, Bitmap.Config.ARGB_8888))
      val medium = generate(entry)

      info(s"generated assets: $medium")

//      small.width shouldEqual 280
//      small.height shouldEqual 280
//      small.mime shouldEqual Mime.Jpg
//      small.tag shouldEqual "smallProfile"
//      small.size should be < 25 * 1024

      medium.width should be < 3000
      medium.height should be <  1000
      medium.tag shouldEqual "medium"
    }
  }

  feature("generate from file") {

    scenario("generate assets for small png image") {

      val entry = imageAssetFor("penguin_128.png")
      val medium = generate(entry, profilePicture = true)

      medium.width shouldEqual 128
      medium.height shouldEqual 128
      medium.mime shouldEqual Mime.Png

      val cached = Await.result(zms.cache.getEntry(medium.cacheKey), 10.seconds).value

      cached.cacheFile should exist
      cached.cacheFile.length shouldEqual medium.size
    }

    scenario("generate assets for big png image") {

      val entry = imageAssetFor("big.png")
      val asset = generate(entry)

      asset.width shouldEqual 1920
      asset.height shouldEqual 1080

      asset.mime shouldEqual Mime.Jpg
    }

    scenario("generate assets for huge jpg image") {

      val entry = imageAssetFor("huge.jpg")
      val asset = generate(entry)

      assert(asset.width < 2560)

      fail()
//      asset.width shouldEqual asset.origWidth
//      asset.height shouldEqual asset.origHeight
    }

    scenario("generate assets for malformed jpg image") {
      val image = imageAssetFor(Bitmap.createBitmap(20 * 1024, 1280, Bitmap.Config.ARGB_8888))
      val assets = generate(image)

      fail()
//      assets.width shouldEqual assets.origWidth
//      assets.height shouldEqual assets.origHeight
    }
  }

  feature("generate from bitmap") {

    def generateAssets(resource: String) = generate(imageAssetFor(resource))

    scenario("generate assets for small image") {
      val asset = generateAssets("penguin_128.png")

      asset.width shouldEqual 128
      asset.height shouldEqual 128

      asset.mime shouldEqual Mime.Png
    }

    scenario("generate assets for big image") {
      val asset = generateAssets("big.png")

      asset.width shouldEqual 1920
      asset.height shouldEqual 1080

      asset.mime shouldEqual Mime.Jpg
    }

    scenario("generate assets for huge image") {
      val asset = generateAssets("huge.jpg")

      assert(asset.width < 2560)

//      asset.width shouldEqual asset.last.origWidth
//      asset.height shouldEqual asset.last.origHeight

      asset.mime shouldEqual Mime.Jpg
    }

    scenario("generate assets for very wide image") {
      val asset = generate(imageAssetFor(Bitmap.createBitmap(10 * 1024, 1280, Bitmap.Config.ARGB_8888)))

      asset.width  should be < 5000
      (asset.width * asset.height) should be < ImageAssetGenerator.MaxImagePixelCount.toInt

//      asset.width shouldEqual asset.last.origWidth
//      asset.height shouldEqual asset.last.origHeight

      asset.mime shouldEqual Mime.Png
    }

    scenario("generate assets for very tall image") {
      val asset = generate(imageAssetFor(Bitmap.createBitmap(1024, 10 * 1280, Bitmap.Config.ARGB_8888)))

      asset.width should be < 1024

//      asset.width shouldEqual asset.last.origWidth
//      asset.height shouldEqual asset.last.origHeight

      asset.mime shouldEqual Mime.Png
    }
  }

  feature("recode based on mime type") {

    lazy val tmpEntry = ui.cache.addData(CacheKey("tmp"), Array.ofDim(100)).await()

    scenario("recode profile image") {
      Seq(Mime.Jpg, Mime.Png) foreach { mime =>
        generator.shouldRecode(tmpEntry, Metadata(10, 10, mime), ImageAssetGenerator.SmallProfileOptions) shouldEqual false
        generator.shouldRecode(tmpEntry, Metadata(10, 10, mime), ImageAssetGenerator.MediumProfileOptions) shouldEqual false
      }
      Seq(Mime.Gif, Mime.Tiff, Mime.WebP, Mime.Unknown) foreach { mime =>
        generator.shouldRecode(tmpEntry, Metadata(10, 10, mime), ImageAssetGenerator.SmallProfileOptions) shouldEqual true
        generator.shouldRecode(tmpEntry, Metadata(10, 10, mime), ImageAssetGenerator.MediumProfileOptions) shouldEqual true
      }
    }

    scenario("recode regular image") {
      generator.shouldRecode(tmpEntry, Metadata(10, 10, Mime.Gif), ImageAssetGenerator.PreviewOptions) shouldEqual true
      generator.shouldRecode(tmpEntry, Metadata(10, 10, Mime.Gif), ImageAssetGenerator.MediumOptions) shouldEqual false

      Seq(Mime.Jpg, Mime.Png) foreach { mime =>
        generator.shouldRecode(tmpEntry, Metadata(10, 10, mime), ImageAssetGenerator.PreviewOptions) shouldEqual false
        generator.shouldRecode(tmpEntry, Metadata(10, 10, mime), ImageAssetGenerator.MediumOptions) shouldEqual false
      }

      Seq(Mime.Tiff, Mime.WebP, Mime.Unknown) foreach { mime =>
        generator.shouldRecode(tmpEntry, Metadata(10, 10, mime), ImageAssetGenerator.PreviewOptions) shouldEqual true
        generator.shouldRecode(tmpEntry, Metadata(10, 10, mime), ImageAssetGenerator.MediumOptions) shouldEqual true
      }
    }
  }
}
