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
package com.waz.ui

import android.content.{ContentResolver, Context}
import android.graphics.Bitmap
import android.media.ExifInterface
import android.os.Parcel
import com.waz.Control.getOrUpdate
import com.waz.ZLog._
import com.waz.api.impl.ImageAsset.Parcelable
import com.waz.api.impl._
import com.waz.bitmap.BitmapDecoder
import com.waz.model.AssetMetaData.Image.Tag.Medium
import com.waz.model._
import com.waz.service.tracking.TrackingService
import com.waz.threading.Threading
import com.waz.utils.wrappers.URI
import com.waz.utils.{JsonDecoder, returning}
import com.waz.{api, bitmap}

import scala.util.Try

class Images(context: Context, bitmapLoader: BitmapDecoder, tracking: TrackingService)(implicit ui: UiModule) {

  import Images._

  private implicit val dispatcher = Threading.ImageDispatcher

  val images      = new UiCache[AssetId, ImageAsset](lruSize = 15)
  val zms         = ui.zms

  def getImageAsset(id: AssetId): ImageAsset = cacheImageAsset(id, new ImageAsset(id))

  def getLocalImageAsset(data: AssetData) = cacheImageAsset(data.id, new LocalImageAsset(data))

  def getLocalImageAssetWithPreview(preview: Option[AssetData], data: AssetData) = cacheImageAsset(data.id, new LocalImageAssetWithPreview(preview, data))

  def getOrCreateUriImageAsset(uri: URI): api.ImageAsset = {
    if (uri == null || uri.toString == "null") {
      tracking.exception(new NullPointerException("image uri is null"), "ImageAssetFactory does not accept null uris.")
      ImageAsset.Empty
    } else {
      val asset = AssetData.newImageAssetFromUri(tag = Medium, uri = uri)
      cacheImageAsset(asset.id, new LocalImageAsset(asset))
    }
  }

  private def cacheImageAsset(id: AssetId, img: ImageAsset) = {
    getOrUpdate(images)(id, img)
  }

  def getImageAsset(p: Parcel): api.ImageAsset = {
    p.readInt() match {
      case Parcelable.FlagEmpty => ImageAsset.Empty
      case Parcelable.FlagWire => getImageAsset(AssetId(p.readString()))
      case Parcelable.FlagLocal => getLocalImageAsset(JsonDecoder.decode[AssetData](p.readString()))
      case Parcelable.FlagLocalWithPreview =>
        val medium = JsonDecoder.decode[AssetData](p.readString())
        val preview = Try(JsonDecoder.decode[AssetData](p.readString())).toOption
        getLocalImageAssetWithPreview(preview, medium)
    }
  }

  def createImageAssetFrom(bytes: Array[Byte]): api.ImageAsset = {
    if (bytes == null || bytes.isEmpty) ImageAsset.Empty
    //Created with camera, so don't cache since if the user cancels or sends, the image asset won't be needed again
    else new LocalImageAsset(AssetData.newImageAsset(tag = Medium).copy(sizeInBytes = bytes.length, data = Some(bytes)))
  }

  def createMirroredImageAssetFrom(bytes: Array[Byte]): api.ImageAsset =
    returning(createImageAssetFrom(bytes))(_.setMirrored(true))

  def getOrCreateImageAssetFromResourceId(resourceId: Int): api.ImageAsset =
    getOrCreateUriImageAsset(URI.parse(s"${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.getPackageName}/$resourceId"))

  def getOrCreateImageAssetFrom(bitmap: Bitmap, orientation: Int = ExifInterface.ORIENTATION_NORMAL): api.ImageAsset = {
    if (bitmap == null || bitmap == com.waz.bitmap.EmptyBitmap) ImageAsset.Empty
    //Created with sketch, so don't cache since if the user cancels or sends, the image asset won't be needed again
    else new LocalBitmapAsset(bitmap, orientation)
  }
}

object Images {
  private implicit val logTag: LogTag = logTagFor[Images]

  val EmptyBitmap = bitmap.EmptyBitmap
}

trait ImagesComponent {
  val images: Images
}
