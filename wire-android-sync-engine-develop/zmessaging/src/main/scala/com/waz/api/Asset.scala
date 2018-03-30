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

import com.waz.api.Asset.LoadCallback
import com.waz.utils.wrappers.URI
import org.threeten.bp.Duration

object Asset {
  trait LoadCallback[A] {
    def onLoaded(a: A): Unit
    def onLoadFailed(): Unit
  }
}

trait Asset extends UiObservable {
  def getId: String
  def getName: String
  def isEmpty: Boolean

  def getMimeType: String
  def isVideo: Boolean
  def isAudio: Boolean

  def getSizeInBytes: Long // will return negative value if size is unknown
  def getStatus: AssetStatus

  def getDuration: Duration
  def getWidth: Int
  def getHeight: Int
  def getAudioOverview: AudioOverview

  def getContentUri(callback: LoadCallback[URI]): Unit
  def saveToDownloads(callback: LoadCallback[URI]): Unit
  def getPlaybackControls(callback: LoadCallback[PlaybackControls]): Unit

  def getDownloadProgress: ProgressIndicator
  def getUploadProgress: ProgressIndicator
}

trait PlaybackControls extends UiObservable {
  def play(): Unit
  def stop(): Unit

  def isPlaying: Boolean
  def getDuration: Duration

  def getPlayhead: Duration
  def setPlayhead(position: Duration): Unit
}
