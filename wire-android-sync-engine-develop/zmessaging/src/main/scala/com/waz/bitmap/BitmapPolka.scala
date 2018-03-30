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
package com.waz.bitmap

import android.content.Context
import android.graphics.{Bitmap, Canvas, Color, Paint}
import com.waz.ZLog._
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.LoggedTry
import com.waz.zms.R

object BitmapPolka {
  private implicit val logTag: LogTag = logTagFor(BitmapPolka)
  implicit val dispatcher = Threading.ImageDispatcher

  def apply(context: Context, bitmap: Bitmap, width: Int): CancellableFuture[Bitmap] = {
    val circleSize = LoggedTry(context.getResources.getDimensionPixelSize(R.dimen.content__image__polka_size)).getOrElse(10) // XXX exception should only happen when testing
    val circlePadding = LoggedTry(context.getResources.getDimensionPixelSize(R.dimen.content__image__polka_spacing)).getOrElse(4)
    dispatcher {
      val height = (width * bitmap.getHeight.toFloat / bitmap.getWidth.toFloat).toInt
      val numCirclesX = Math.max(1, width / (circleSize + circlePadding))
      val numCirclesY = Math.max(1, height / (circleSize + circlePadding))

      val conf = Bitmap.Config.ARGB_8888
      val bmp = Bitmap.createBitmap(width, height, conf)
      val canvas = new Canvas(bmp)
      val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
      paint.setStyle(Paint.Style.FILL)

      val spacing = circleSize + circlePadding
      val circleRadius = circleSize / 2
      val firstX = (width - (numCirclesX * spacing)) / 2 + spacing / 2
      val firstY = (height - (numCirclesY * spacing)) / 2 + spacing / 2

      for {y <- 0 until numCirclesY
           x <- 0 until numCirclesX } {
        paint.setColor(sampleColor(bitmap, (.5f + x) / numCirclesX, (.5f + y) / numCirclesY))
        canvas.drawCircle(firstX + (x * spacing),
          firstY + (y * spacing),
          circleRadius,
          paint)
      }
      bmp
    }
  }

  private def sampleColor(bitmap: Bitmap, x: Float, y: Float) = {
    val bitmapWidth = bitmap.getWidth
    val bitmapHeight = bitmap.getHeight
    val fx = x * bitmapWidth
    val fy = y * bitmapHeight
    val bx = fx.toInt
    val by = fy.toInt
    var sx = fx - bx
    var sy = fy - by
    val p00 = bitmap.getPixel(bx, by)
    var p10 = 0
    var p01 = 0
    var p11 = 0
    if (bx == bitmapWidth - 1) {
      sx = 0
    }
    if (by == bitmapHeight - 1) {
      sy = 0
    }
    if (sx > 0) {
      p10 = bitmap.getPixel(bx + 1, by)
    }
    if (sy > 0) {
      p01 = bitmap.getPixel(bx, by + 1)
    }
    if (sx > 0 && sy > 0) {
      p11 = bitmap.getPixel(bx + 1, by + 1)
    }
    interpolate(sx, sy, p00, p01, p10, p11)
  }

  private def interpolate(sx: Float, sy: Float, p00: Int, p01: Int, p10: Int, p11: Int) = {
    Color.argb(
      ((1f - sx) * (1f - sy) * Color.alpha(p00) + (1f - sx) * sy * Color.alpha(p01) + sx * (1f - sy) * Color.alpha(p10) + sx * sy * Color.alpha(p11)).toInt,
      ((1f - sx) * (1f - sy) * Color.red(p00) + (1f - sx) * sy * Color.red(p01) + sx * (1f - sy) * Color.red(p10) + sx * sy * Color.red(p11)).toInt,
      ((1f - sx) * (1f - sy) * Color.green(p00) + (1f - sx) * sy * Color.green(p01) + sx * (1f - sy) * Color.green(p10) + sx * sy * Color.green(p11)).toInt,
      ((1f - sx) * (1f - sy) * Color.blue(p00) + (1f - sx) * sy * Color.blue(p01) + sx * (1f - sy) * Color.blue(p10) + sx * sy * Color.blue(p11)).toInt)
  }
}
