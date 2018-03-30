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

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.TypedValue
import java.io.{FileInputStream, ByteArrayInputStream, File, InputStream}
import org.robolectric.annotation.{Implementation, Implements}
import javax.imageio.ImageIO
import org.robolectric.Robolectric

@Implements(classOf[BitmapFactory]) object ShadowIOBitmapFactory {

  @Implementation def decodeResource(res: Resources, id: Int, options: BitmapFactory.Options): Bitmap = ???

  @Implementation def decodeResource(res: Resources, id: Int): Bitmap = ???

  @Implementation def decodeResourceStream(res: Resources, value: TypedValue, is: InputStream, pad: Rect, opts: BitmapFactory.Options): Bitmap = decodeStream(is, pad, opts)

  @Implementation def decodeFile(pathName: String): Bitmap = decodeFile(pathName, null)

  @Implementation def decodeFile(pathName: String, options: BitmapFactory.Options): Bitmap = {
    decodeStream(new FileInputStream(new File(pathName)), new Rect, options)
  }

  @Implementation def decodeStream(is: InputStream): Bitmap = decodeStream(is, null, null)

  @Implementation def decodeStream(is: InputStream, outPadding: Rect, options: BitmapFactory.Options): Bitmap = {

    val opts = Option(options).getOrElse(new BitmapFactory.Options)

    val iis = ImageIO.createImageInputStream(is)
    val reader = ImageIO.getImageReaders(iis).next()
    opts.outMimeType = "image/" + reader.getFormatName.toLowerCase
    reader.setInput(iis)
    val img = reader.read(0)
    val image =
      if (opts.inSampleSize <= 1) img
      else ShadowIOBitmap.scaled(img, img.getWidth / opts.inSampleSize, img.getHeight / opts.inSampleSize)

    opts.outWidth = image.getWidth
    opts.outHeight = image.getHeight

    val bitmap: Bitmap = Robolectric.newInstanceOf(classOf[Bitmap])
    ShadowIOBitmap.shadowOf(bitmap).image = image
    bitmap
  }

  @Implementation def decodeByteArray(data: Array[Byte], offset: Int, length: Int): Bitmap = decodeStream(new ByteArrayInputStream(data, offset, length))

  @Implementation def decodeByteArray(data: Array[Byte], offset: Int, length: Int, opts: BitmapFactory.Options): Bitmap = decodeStream(new ByteArrayInputStream(data, offset, length), new Rect(), opts)
}


@Implements(classOf[BitmapFactory]) class ShadowIOBitmapFactory {}
