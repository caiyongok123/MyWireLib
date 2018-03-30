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
package com.waz.sync.client

import com.waz.service.media.RichMediaContentParser.GoogleMapsLocation
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

@Ignore class GoogleMapsClientSpec extends FeatureSpec with Matchers with TableDrivenPropertyChecks with RobolectricTests {

  feature("generating the static URI") {
    scenario("to do ") {
      forAll(Table(
        ("x", "y", "zoom", "width", "height", "uri"),
        ("wow-x", "wow-y", "meep", 800, 600, "/proxy/googlemaps/api/staticmap?center=wow-x%2Cwow-y&zoom=meep&size=800x600"),
        ("other-x", "other-y", "foo", 80, 60, "/proxy/googlemaps/api/staticmap?center=other-x%2Cother-y&zoom=foo&size=80x60")
      )) { (x: String, y: String, zoom: String, width: Int, height: Int, path: String) =>

        GoogleMapsClient.getStaticMapPath(GoogleMapsLocation(x, y, zoom), width, height) shouldEqual path
      }
    }
  }
}
