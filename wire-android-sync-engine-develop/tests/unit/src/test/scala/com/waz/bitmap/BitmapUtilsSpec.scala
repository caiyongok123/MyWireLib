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

import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

@Ignore class BitmapUtilsSpec extends FeatureSpec with Matchers with RobolectricTests {

  feature("In sample size") {

    scenario("sample size when image is smaller") {
      BitmapUtils.computeInSampleSize(100, 99) shouldEqual 1
      BitmapUtils.computeInSampleSize(100, 10) shouldEqual 1
      BitmapUtils.computeInSampleSize(100, 0) shouldEqual 1
    }

    scenario("sample size for bigger images, use power of two sizes") {
      BitmapUtils.computeInSampleSize(100, 199) shouldEqual 1
      BitmapUtils.computeInSampleSize(100, 200) shouldEqual 2
      BitmapUtils.computeInSampleSize(100, 201) shouldEqual 2
      BitmapUtils.computeInSampleSize(100, 299) shouldEqual 2
      BitmapUtils.computeInSampleSize(100, 399) shouldEqual 2
      BitmapUtils.computeInSampleSize(100, 400) shouldEqual 4
      BitmapUtils.computeInSampleSize(100, 800) shouldEqual 8
    }
  }
}
