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
package com.waz.model

import com.waz.api.ContentSearchQuery
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

@Ignore class ContentSearchQuerySpec extends FeatureSpec with Matchers with RobolectricTests {
  scenario("Århus should be transliterated to arhus"){
    ContentSearchQuery("Århus").toString shouldBe "arhus"
  }

  scenario("björn should be transliterated to bjorn"){
    ContentSearchQuery("björn").toString shouldBe "bjorn"
  }

  scenario("bjoern should be transliterated to bjoern"){
    ContentSearchQuery("bjoern").toString shouldBe "bjoern"
  }

  scenario("Saint-Étienne should be transliterated to saint-etienne"){
    ContentSearchQuery("Saint-Étienne").toString shouldBe "saint-etienne"
  }

  scenario("Coraçao should be transliterated to coracao"){
    ContentSearchQuery("Coraçao").toString shouldBe "coracao"
  }

  scenario("❤️\uD83C\uDF55 should be transliterated to ❤️\uD83C\uDF55"){
    ContentSearchQuery("❤️\uD83C\uDF55").toString shouldBe "❤️\uD83C\uDF55"
  }

  scenario("苹果 should be transliterated to ping guo"){
    ContentSearchQuery("苹果").toString shouldBe "ping guo"
  }

  scenario("सेवफलम् should be transliterated to sevaphalam"){
    ContentSearchQuery("सेवफलम्").toString shouldBe "sevaphalam"
  }

  scenario("μήλο should be transliterated to melo"){
    ContentSearchQuery("μήλο").toString shouldBe "melo"
  }

  scenario("Яблоко should be transliterated to abloko"){
    ContentSearchQuery("Яблоко").toString shouldBe "abloko"
  }

  scenario(" خطای سطح دسترسی" + " should be transliterated to khtay sth dstrsy"){
    ContentSearchQuery("خطای سطح دسترسی").toString shouldBe "khtay sth dstrsy"
  }

  scenario("תפוח" + " should be transliterated to tpwh"){
    ContentSearchQuery("תפוח").toString shouldBe "tpwh"
  }

  scenario("ᑭᒻᒥᓇᐅᔭᖅ should be transliterated to ᑭᒻᒥᓇᐅᔭᖅ"){
    ContentSearchQuery("ᑭᒻᒥᓇᐅᔭᖅ").toString shouldBe "ᑭᒻᒥᓇᐅᔭᖅ"
  }
}
