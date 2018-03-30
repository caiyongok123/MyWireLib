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
package com.waz.api

import android.graphics.Bitmap
import com.waz.service.ZMessaging
import com.waz.utils.wrappers.URI

object ImageAssetFactory {

  /**
   * Retrieve an image asset from a URI. The content, file and http/https schemes are supported.
   * This will handle caching, orientation (according to exif data if available) and sampling.
   */
  def getImageAsset(uri: URI): ImageAsset = ZMessaging.currentUi.images.getOrCreateUriImageAsset(uri)

  /**
   * Retrieve an image asset from a byte array.
   * This will handle caching, orientation (according to exif data if available) and sampling.
   */
  def getImageAsset(bytes: Array[Byte]): ImageAsset = ZMessaging.currentUi.images.createImageAssetFrom(bytes)

  def getImageAsset(bitmap: Bitmap): ImageAsset = ZMessaging.currentUi.images.getOrCreateImageAssetFrom(bitmap)

  /**
   * Created image asset from rotated bitmap.
   * @param orientation ExifInterface.ORIENTATION_* constant
   */
  def getImageAsset(bitmap: Bitmap, orientation: Int): ImageAsset = {
    assert(orientation >= 0 && orientation <= 8, "rotation should be ExifInterface.ORIENTATION_* constant (values in range: [0-8])")
    ZMessaging.currentUi.images.getOrCreateImageAssetFrom(bitmap, orientation)
  }

  /**
   * Retrieve mirrored image asset from a byte array.
   * This will handle caching, orientation (according to exif data if available) and sampling.
   */
  def getMirroredImageAsset(bytes: Array[Byte]): ImageAsset = ZMessaging.currentUi.images.createMirroredImageAssetFrom(bytes)

  /**
   * Retrieve an image asset directly from a resource id.
   * This will handle caching and sampling.
   */
  def getImageAsset(resourceId: Int): ImageAsset = ZMessaging.currentUi.images.getOrCreateImageAssetFromResourceId(resourceId)
}
