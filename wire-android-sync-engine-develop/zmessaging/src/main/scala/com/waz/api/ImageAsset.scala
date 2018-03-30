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

import android.os.Parcelable.Creator
import android.os.{Parcel, Parcelable}
import com.waz.api.ImageAsset.SaveCallback
import com.waz.service.ZMessaging
import com.waz.utils.wrappers.URI

object ImageAsset {

  trait SaveCallback {
    def imageSaved(uri: URI): Unit
    def imageSavingFailed(ex: Exception): Unit
  }

  val CREATOR: Parcelable.Creator[ImageAsset] = new Creator[ImageAsset] {
    override def newArray(size: Int) = new Array[ImageAsset](size)
    override def createFromParcel(source: Parcel): ImageAsset = ZMessaging.currentUi.images.getImageAsset(source)
  }
}

trait ImageAsset extends UiObservable with Parcelable {
  def getId: String

  def getWidth: Int

  def getHeight: Int

  def getMimeType: String

  def isEmpty: Boolean

  def getBitmap(width: Int, callback: BitmapCallback): LoadHandle

  /**
   * This Returns bitmap in one go,
   */
  def getSingleBitmap(width: Int, callback: BitmapCallback): LoadHandle

  def getRoundBitmap(width: Int, callback: BitmapCallback): LoadHandle

  def getRoundBitmap(width: Int, borderWidth: Int, borderColor: Int, callback: BitmapCallback): LoadHandle

  def saveImageToGallery(): Unit

  def saveImageToGallery(callback: SaveCallback): Unit

  def mirrored: Boolean

  def setMirrored(mirrored: Boolean): Unit

  override def describeContents(): Int = 0
}
