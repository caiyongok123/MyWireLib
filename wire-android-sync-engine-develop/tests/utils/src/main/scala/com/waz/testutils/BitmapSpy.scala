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
package com.waz.testutils

import android.graphics.Bitmap
import com.waz.api.{BitmapCallback, ImageAsset, LoadHandle, UpdateListener}

class BitmapSpy(img: ImageAsset, size: Int = 600) {
  var failed = false
  var result = Option.empty[Bitmap]

  private var handle: LoadHandle = _

  load()

  img.addUpdateListener(new UpdateListener {
    override def updated(): Unit = load()
  })

  private def load() = {
    handle = img.getBitmap(size, new BitmapCallback {
      override def onBitmapLoadingFailed(): Unit = failed = true
      override def onBitmapLoaded(b: Bitmap): Unit = result = Option(b)
    })
  }
}
