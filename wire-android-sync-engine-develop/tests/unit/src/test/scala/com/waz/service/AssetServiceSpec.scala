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
package com.waz.service

import java.io.{File, FileInputStream}

import android.database.sqlite.SQLiteDatabase
import com.waz.RobolectricUtils
import com.waz.api.impl.{AssetForUpload, ErrorResponse}
import com.waz.cache.{CacheEntry, CacheService}
import com.waz.model.AssetMetaData.Image.Tag.Medium
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{Mime, _}
import com.waz.service.assets.AssetService
import com.waz.sync.client.{AssetClient, AssetClientImpl}
import com.waz.testutils.Matchers._
import com.waz.testutils.MockZMessaging
import com.waz.threading.CancellableFuture
import com.waz.utils.IoUtils
import com.waz.znet.Request
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

@Ignore class AssetServiceSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils {

  val mediumFile = new File(getClass.getResource("/images/penguin_240.png").getFile).getAbsoluteFile
  val imData = {
    val is = getClass.getResourceAsStream("/images/penguin_128.png")
    try {
      Stream.continually(is.read()).takeWhile(_ != -1).map(_.toByte).toArray
    } finally is.close()
  }

  val asset = AssetData(convId = Some(RConvId()), sizeInBytes = mediumFile.length(), metaData = Some(AssetMetaData.Image(Dim2(240, 246), Medium)), remoteId = Some(RAssetId()))

  lazy val zms = new MockZMessaging() { self =>
    override lazy val assetClient: AssetClient = new AssetClientImpl(zNetClient) {
      override def loadAsset(req: Request[Unit]) = CancellableFuture.successful(loadImageResult)
    }
  }

  lazy val service: AssetService = zms.assets
  lazy val cacheService: CacheService = zms.cache
  implicit def db: SQLiteDatabase = zms.db.dbHelper.getWritableDatabase

  var loadImageResult: Either[ErrorResponse, CacheEntry] = _

  before {
    loadImageResult = Left(ErrorResponse.internalError("loading failed"))
  }

  feature("Image assets") {
    scenario("Save image to gallery") {
      zms.assetsStorage.insert(asset)

      val entry = Await.result(cacheService.addFile(CacheKey("key"), mediumFile), 10.seconds)
      loadImageResult = Right(entry)

      val uri = zms.imageLoader.saveImageToGallery(asset).await()(patience(15.seconds))
      uri should be('defined)

      info(s"image saved to: ${uri.get}")

      val savedFile = new File(uri.get.getPath)
      savedFile should exist
      savedFile.length() shouldEqual mediumFile.length()
    }
  }

  feature("File assets") {

    scenario("Save asset to downloads") {
      val conv = zms.insertConv(ConversationData(ConvId(), RConvId(), None, UserId(), ConversationType.Group))
      val source = AssetForUpload(AssetId(), Some("name.txt"), Mime("text/txt"), None) { _ => getClass.getResourceAsStream("/images/penguin_128.png") }
      lazy val asset = service.addAsset(source, conv.remoteId).await()

      val file = service.saveAssetToDownloads(asset.id).await()
      file should be('defined)
      info(s"saved to file: $file")
      IoUtils.toByteArray(new FileInputStream(file.get)).toSeq shouldEqual IoUtils.toByteArray(source.openDataStream(context)).toSeq
    }

  }
}
