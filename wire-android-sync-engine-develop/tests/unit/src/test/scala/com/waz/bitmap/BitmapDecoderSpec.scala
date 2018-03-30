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

import android.graphics.{Bitmap => ABitmap}
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}
import com.waz.utils.wrappers.Bitmap

@Ignore class BitmapDecoderSpec extends FeatureSpec with Matchers with RobolectricTests {

  lazy val bitmap: Bitmap = ABitmap.createBitmap(10, 10, ABitmap.Config.ARGB_8888)

  feature("Retry on error") {

    scenario("retry on OOM error") {
      val decoder = new BitmapDecoder

      decoder.retryOnError(1, 8) { sample =>
        if (sample == 1) throw new OutOfMemoryError()
        else bitmap
      } shouldEqual bitmap
    }

    scenario("don't retry if there is no error") {
      val decoder = new BitmapDecoder

      decoder.retryOnError(1, 8) { sample =>
        sample shouldEqual 1
        bitmap
      } shouldEqual bitmap
    }

    scenario("don't retry forever") {
      val decoder = new BitmapDecoder

      var samples = List[Int]()

      intercept[OutOfMemoryError] {
        decoder.retryOnError(1, 8) { sample =>
          samples ::= sample
          throw new OutOfMemoryError()
        }
      }

      samples shouldEqual List(8, 4, 2, 1)
    }
  }
}
