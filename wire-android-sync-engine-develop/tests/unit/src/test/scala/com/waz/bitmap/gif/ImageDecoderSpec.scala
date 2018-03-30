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
package com.waz.bitmap.gif

import com.waz.bitmap.gif.Gif.Frame
import com.waz.bitmap.gif.ImageDecoderHelper.{LineProducer, PixelConsumer}
import com.waz.utils.IoUtils
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

@Ignore class ImageDecoderSpec extends FeatureSpec with Matchers with RobolectricTests {

  def load(resName: String) = GifReader(IoUtils.toByteArray(getClass.getResourceAsStream(resName)))

  var count = 0
  var pixels: Array[Int] = _

  def consumer(gif: Gif) = {
    pixels = new Array[Int](gif.width * gif.height)
    count = 0
    new PixelConsumer(pixels, gif.gct, gif.width, gif.height) {
      override def reset(frame: Frame): Unit = {
        idx = 0
        count = 0
      }
      override def apply(colorIndex: Int): Unit = {
        pixels(idx) = colorIndex
        idx += 1
        count += 1
      }
    }
  }

  feature("Image data decoder") {

    scenario("Decode stripes") {
      val gif = load("/gifs/stripes_5.gif").get

      new ImageDecoderHelper().decodeBitmapData(gif.data(gif.frames.head), gif.width, gif.height, consumer(gif))
      count shouldEqual 25
      pixels.toSeq shouldEqual Seq(0, 0, 0, 0, 0,
        1, 1, 1, 1, 1,
        0, 0, 0, 0, 0,
        1, 1, 1, 1, 1,
        0, 0, 0, 0, 0)
    }

    scenario("Decode transparent stripes") {
      val gif = load("/gifs/stripes_trans_5.gif").get

      new ImageDecoderHelper().decodeBitmapData(gif.data(gif.frames.head), gif.width, gif.height, consumer(gif))
      count shouldEqual 25
      pixels.toSeq shouldEqual Seq(0, 0, 0, 0, 0,
        2, 2, 2, 2, 2,
        1, 1, 1, 1, 1,
        2, 2, 2, 2, 2,
        0, 0, 0, 0, 0)
    }

    scenario("Decode first frame") {
      val gif = load("/gifs/regular1.gif").get
      new ImageDecoderHelper().decodeBitmapData(gif.data(gif.frames.head), gif.width, gif.height, consumer(gif))
      count shouldEqual gif.width * gif.height
    }

    scenario("Decode first frame from artifacts") {
      val gif = load("/gifs/artifacts1.gif").get
      new ImageDecoderHelper().decodeBitmapData(gif.data(gif.frames.head), gif.width, gif.height, consumer(gif))
      count shouldEqual gif.width * gif.height
    }
    
    scenario("Decode first frame from artifacts2") {
      val gif = load("/gifs/artifacts2.gif").get
      new ImageDecoderHelper().decodeBitmapData(gif.data(gif.frames.head), gif.width, gif.height, consumer(gif))
      count shouldEqual gif.width * gif.height
    }

    scenario("Decode first frame from artifacts2 java") {
      val gif = load("/gifs/artifacts2.gif").get
      new ImageDecoderHelper().decodeBitmapData(gif.data(gif.frames.head), gif.width, gif.height, consumer(gif))
      count shouldEqual gif.width * gif.height
    }
  }

  feature("Line interlacing") {
    scenario("interlace line ordering") {
      val lines = new LineProducer
      lines.reset(Gif.Frame(Gif.Bounds(0, 0, 11, 11), interlace = true))
      Iterator.continually(lines.getNextLine).take(11).toSeq shouldEqual Seq(0, 8, 4, 2, 6, 10, 1, 3, 5, 7, 9)
    }
  }
}
