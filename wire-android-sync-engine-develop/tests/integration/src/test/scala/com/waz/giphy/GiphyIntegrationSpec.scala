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
package com.waz.giphy

import com.waz.api.ProvisionedApiSpec
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.scalatest.{FeatureSpec, Matchers}

class GiphyIntegrationSpec extends FeatureSpec with Matchers with ProvisionedApiSpec {
  override val provisionFile: String = "/one_user.json"

  feature("random") {
    scenario("random") {
      val result = api.getGiphy.random()
      (result should (be('ready) and have(size(1)))).soon

      result.head.shouldBeAnAnimatedGif
    }
  }

  feature("search") {

    lazy val catSearch = api.getGiphy.search("cat")

    scenario("search") {
      (catSearch should (be('ready) and have(size(25)))).soon

      val img = catSearch.head
      img.data.tag shouldEqual "medium"
      img.shouldBeAnAnimatedGif
    }

    scenario("fetch next page on demand") {
      catSearch(23).shouldBeAnAnimatedGif
      (catSearch.size should be > 25).soon
    }
  }

  feature("trending") {

    lazy val results = api.getGiphy.trending()

    scenario("search") {
      (results should (be('ready) and have(size(25)))).soon

      val img = results.head
      img.data.tag shouldEqual "medium"
      img.shouldBeAnAnimatedGif
    }

    scenario("fetch next page on demand") {
      results(23).shouldBeAnAnimatedGif
      (results.size should be > 25).soon
    }
  }
}
