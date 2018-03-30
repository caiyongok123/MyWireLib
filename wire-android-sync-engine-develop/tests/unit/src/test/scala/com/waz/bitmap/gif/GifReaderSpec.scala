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

import java.io.File

import com.waz.utils.IoUtils
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

@Ignore class GifReaderSpec extends FeatureSpec with Matchers with RobolectricTests {


  feature("Load gif from byte array") {
    def load(resName: String) = GifReader(IoUtils.toByteArray(getClass.getResourceAsStream(resName)))

    scenario("Load stripes_5") {
      val gif = load("/gifs/stripes_5.gif").get
      gif.frames should have size 1
      gif.width shouldEqual 5
      gif.height shouldEqual 5
      gif.frames.head.transparency shouldEqual false

      val gct = gif.gct.toSeq.takeWhile(_ != 0).map(_.toHexString)

      info(s"loaded: $gif")
      info(s"gct: $gct")
    }

    scenario("Load stripes_trans_5") {
      val gif = load("/gifs/stripes_trans_5.gif").get
      gif.frames should have size 1
      gif.width shouldEqual 5
      gif.height shouldEqual 5
      gif.frames.head.transparency shouldEqual true

      val gct = gif.gct.toSeq.takeWhile(_ != 0).map(_.toHexString)

      info(s"loaded: $gif")
      info(s"gct: $gct")
    }
  }

  feature("Load from file") {
    def load(resName: String) = GifReader(new File(getClass.getResource(resName).getFile))

    scenario("Load stripes_5") {
      val gif = load("/gifs/stripes_5.gif").get
      gif.frames should have size 1
      gif.width shouldEqual 5
      gif.height shouldEqual 5
      gif.frames.head.transparency shouldEqual false

      val gct = gif.gct.toSeq.takeWhile(_ != 0).map(_.toHexString)

      info(s"loaded: $gif")
      info(s"gct: $gct")
    }

    scenario("Load stripes_trans_5") {
      val gif = load("/gifs/stripes_trans_5.gif").get
      gif.frames should have size 1
      gif.width shouldEqual 5
      gif.height shouldEqual 5
      gif.frames.head.transparency shouldEqual true

      val gct = gif.gct.toSeq.takeWhile(_ != 0).map(_.toHexString)

      info(s"loaded: $gif")
      info(s"gct: $gct")
    }
  }

}
