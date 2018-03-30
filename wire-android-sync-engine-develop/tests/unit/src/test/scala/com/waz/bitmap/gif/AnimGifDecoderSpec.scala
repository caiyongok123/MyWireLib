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

import com.waz.utils.IoUtils
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

import scala.concurrent.duration.Duration

@Ignore class AnimGifDecoderSpec extends FeatureSpec with Matchers with RobolectricTests {

  def load(resName: String) = GifReader(IoUtils.toByteArray(getClass.getResourceAsStream(resName)))

  scenario("Load first frame") {
    val decoder = new AnimGifDecoder(load("/gifs/regular1.gif").get)

    decoder.getFrameDelay shouldEqual Duration.Zero
    decoder.advanceNextFrame()
    val frame = decoder.getCurrentFrame
    info(s"decoded frame: $frame")
  }

  scenario("Decode artifacts1") {
    val decoder = new AnimGifDecoder(load("/gifs/artifacts1.gif").get)

    decoder.getFrameDelay shouldEqual Duration.Zero
    for (_ <- 1 to decoder.framesCount) {
      decoder.advanceNextFrame()
      val frame = decoder.getCurrentFrame
      info(s"decoded frame: $frame")
    }
  }
}
