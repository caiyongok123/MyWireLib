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
package org.robolectric.shadows

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.awt.Graphics2D
import java.awt.image.BufferedImage

/**
 * Shadows the {@code android.graphics.Canvas} class.
 * <p/>
 * Broken.
 * This implementation is very specific to the application for which it was developed.
 * Todo: Reimplement. Consider using the same strategy of collecting a history of draw events and providing methods for writing queries based on type, number, and order of events.
 */
@Implements(classOf[Canvas]) class ShadowIOCanvas {
  var image: BufferedImage = _
  var graphics: Graphics2D = _

  def __constructor__(bitmap: Bitmap): Unit = {
    this.image = ShadowIOBitmap.shadowOf(bitmap).image
    this.graphics = image.getGraphics.asInstanceOf[Graphics2D]
  }

  @Implementation def setBitmap(bitmap: Bitmap): Unit = {
    this.image = ShadowIOBitmap.shadowOf(bitmap).image
    this.graphics = image.getGraphics.asInstanceOf[Graphics2D]
  }

  @Implementation def drawText(text: String, x: Float, y: Float, paint: Paint): Unit = ???

  @Implementation def translate(x: Float, y: Float): Unit = ???

  @Implementation def scale(sx: Float, sy: Float): Unit = ???

  @Implementation def scale(sx: Float, sy: Float, px: Float, py: Float): Unit = ???

  @Implementation def drawPaint(paint: Paint): Unit = ???

  @Implementation def drawColor(color: Int): Unit = ???

  @Implementation def drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint): Unit = {
    graphics.drawImage(ShadowIOBitmap.shadowOf(bitmap).image, left.toInt, top.toInt, null) // TODO: use paint
  }

  @Implementation def drawBitmap(bitmap: Bitmap, src: Rect, dst: Rect, paint: Paint): Unit = {
    graphics.drawImage(ShadowIOBitmap.shadowOf(bitmap).image, 0, 0, null) // TODO: use transform and paint
  }

  @Implementation def drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint): Unit = {
    graphics.drawImage(ShadowIOBitmap.shadowOf(bitmap).image, 0, 0, null) // TODO: use transform and paint
  }

  @Implementation def drawPath(path: Path, paint: Paint): Unit = ()

  @Implementation def drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint): Unit = ()

  @Implementation def getWidth: Int = image.getWidth

  @Implementation def getHeight: Int = image.getHeight
}
