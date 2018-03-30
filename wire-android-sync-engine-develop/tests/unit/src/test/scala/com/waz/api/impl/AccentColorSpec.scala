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
package com.waz.api.impl

import android.graphics.Color
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

/**
  */
@Ignore class AccentColorSpec extends FeatureSpec with Matchers with RobolectricTests {

  val colors = Array(
    new AccentColor(1, 0, 0.784, 0, 1),
    new AccentColor(5, 0.996, 0.368, 0.741, 1),
    new AccentColor(2, 1, 0.823, 0, 1),
    new AccentColor(0, 0.141, 0.552, 0.827, 1),
    new AccentColor(3, 1, 0.152, 0, 1),
    new AccentColor(4, 1, 0.588, 0, 1),
    new AccentColor(6, 0.615, 0, 1, 1)
  )

  scenario("Check default color is color with key 0") {
    AccentColors.setColors(colors)
    AccentColor() should be(colors.find(_.id == 0).get)
  }

  scenario("Check initial default color") {
    info(AccentColor().toString)
    info(AccentColors.create(0, Color.RED).toString)

    AccentColors.create(1, Color.RED).getColor shouldEqual Color.RED
  }
}
