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

import java.awt.Graphics2D
import java.awt.geom.AffineTransform

import android.graphics.Bitmap
import android.graphics.Matrix
import org.robolectric.annotation.Implementation
import java.io.OutputStream
import org.robolectric.Robolectric
import org.robolectric.annotation.Implements
import java.awt.image._
import javax.imageio.spi.{ServiceRegistry, IIORegistry, ImageWriterSpi}
import android.graphics.Bitmap.CompressFormat
import javax.imageio.{ImageIO, ImageWriteParam, IIOImage}
import javax.imageio.plugins.jpeg.JPEGImageWriteParam

@Implements(classOf[Bitmap]) object ShadowIOBitmap {

  def shadowOf(b: Bitmap) = Robolectric.shadowOf_(b).asInstanceOf[ShadowIOBitmap]

  @Implementation def createBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap = {
    val scaledBitmap: Bitmap = Robolectric.newInstanceOf(classOf[Bitmap])
    val shadowBitmap = shadowOf(scaledBitmap)
    shadowBitmap.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    shadowBitmap.config = config
    scaledBitmap
  }

  @Implementation def createBitmap(src: Bitmap): Bitmap = src

  @Implementation def createScaledBitmap(src: Bitmap, dstWidth: Int, dstHeight: Int, filter: Boolean): Bitmap = {
    if (dstWidth == src.getWidth && dstHeight == src.getHeight && !filter) src
    else {
      val scaledBitmap: Bitmap = Robolectric.newInstanceOf(classOf[Bitmap])
      val shadow = shadowOf(scaledBitmap)
      shadow.image = scaled(ShadowIOBitmap.shadowOf(src).image, dstWidth, dstHeight)
      scaledBitmap
    }
  }

  @Implementation def createBitmap(src: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap = {
    if (x == 0 && y == 0 && width == src.getWidth && height == src.getHeight) {
      return src
    }
    val img = ShadowIOBitmap.shadowOf(src).image.getSubimage(x, y, width, height)

    val bm = Robolectric.newInstanceOf(classOf[Bitmap])
    shadowOf(bm).image = img
    bm
  }

  @Implementation def createBitmap(src: Bitmap, x: Int, y: Int, width: Int, height: Int, matrix: Matrix, filter: Boolean): Bitmap = {
    if (x == 0 && y == 0 && width == src.getWidth && height == src.getHeight && (matrix == null || matrix.isIdentity)) {
      return src
    } else {
      val img = ShadowIOBitmap.shadowOf(src).image.getSubimage(x, y, width, height)

      val m = Array.fill(6)(0.0f)
      matrix.getValues(m)
      val transform = new AffineTransform(m)
      val op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR)
      val bounds = op.getBounds2D(img)
      val dest = op.filter(img, new BufferedImage(bounds.getWidth.toInt, bounds.getHeight.toInt, img.getType))

      val bm = Robolectric.newInstanceOf(classOf[Bitmap])
      shadowOf(bm).image = dest

      bm
    }
  }

  @Implementation def createBitmap(ignored: Array[Int], width: Int, height: Int, config: Bitmap.Config): Bitmap = Bitmap.createBitmap(width, height, config)

  def scaled(img: BufferedImage, w: Int, h: Int): BufferedImage = {
    val res = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val gr = res.getGraphics.asInstanceOf[Graphics2D]
    gr.drawImage(img, 0, 0, null) // TODO: use transform and paint
    res
  }

  def getBytesPerPixel(config: Bitmap.Config): Int =
    config match {
      case Bitmap.Config.ARGB_8888 => 4
      case Bitmap.Config.RGB_565 => 2
      case Bitmap.Config.ALPHA_8 => 1
      case null => 4
      case _ => 2
    }
}

@SuppressWarnings(Array("UnusedDeclaration"))
@Implements(classOf[Bitmap]) class ShadowIOBitmap {
  import ShadowIOBitmap._

  var image: BufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
  var config: Bitmap.Config = Bitmap.Config.ARGB_8888
  var mutable: Boolean = true
  var recycled: Boolean = false

  lazy val writerSpi =
    IIORegistry.getDefaultInstance.getServiceProviders(classOf[ImageWriterSpi], new ServiceRegistry.Filter() {
      override def filter(provider: scala.Any): Boolean = provider match {
        case spi: ImageWriterSpi if spi.getFormatNames.exists(_.equalsIgnoreCase("jpeg")) => true
        case _ => false
      }
    }, true).next()

  @Implementation def compress(format: Bitmap.CompressFormat, quality: Int, stream: OutputStream): Boolean = {
    if (format == CompressFormat.JPEG) {
      val writer = writerSpi.createWriterInstance()
      writer.setOutput(ImageIO.createImageOutputStream(stream))

      val jpegParams = new JPEGImageWriteParam(null)
      jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
      jpegParams.setCompressionQuality(quality / 100f)
      writer.write(null, new IIOImage(image, null, null), jpegParams)
      true
    } else {
      ImageIO.write(image, "png", stream)
    }
  }

  @Implementation def getPixels(pixels: Array[Int], offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int): Unit = {
//    image.getData.getPixels(x, y, width, height, pixels)
  }

  @Implementation def setPixels(pixels: Array[Int], offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int): Unit = {
//    require(pixels.length >= width * height)
//    image.setRGB(x, y, width, height, pixels, 0, stride)
  }

  @Implementation def getRowBytes: Int = getBytesPerPixel(config) * getWidth

  @Implementation def getByteCount: Int = getRowBytes * getHeight

  @Implementation def recycle(): Unit = recycled = true

  @Implementation final def isRecycled: Boolean = recycled

  @Implementation def copy(config: Bitmap.Config, isMutable: Boolean): Bitmap = {
    val newBitmap: Bitmap = Robolectric.newInstanceOf(classOf[Bitmap])
    shadowOf(newBitmap).config = config
    newBitmap
  }

  @Implementation final def getConfig: Bitmap.Config = config

  @Implementation final def isMutable: Boolean = mutable

  @Implementation def getWidth: Int = image.getWidth

  @Implementation def getHeight: Int = image.getHeight

  @Implementation override def equals(o: Any): Boolean = o match {
    case shadow: ShadowIOBitmap => (shadow eq this) || shadow.image == image
    case b: Bitmap => equals(shadowOf(b))
    case _ => false
  }

  @Implementation override def hashCode: Int = image.hashCode()
}
