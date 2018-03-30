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
package com.waz.ui

import com.waz.Control.getOrUpdate
import com.waz.testutils.{MockUiModule, MockZMessaging}
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

@Ignore class UiCacheSpec extends FeatureSpec with Matchers with RobolectricTests {

  implicit lazy val ui = new MockUiModule(new MockZMessaging())

  case class Item(id: String)

  scenario("add item to cache and retrieve it") {
    val cache = new UiCache[String, Item] {}
    val item = new Item("item1")

    getOrUpdate(cache)("item1", item) should be(item)
    cache.get("item1") should be(Some(item))
  }

  scenario("add item to cache, loose the reference, force GC") {
    val cache = new UiCache[String, Item] {}
    getOrUpdate(cache)("item0", new Item("item0")) should be(new Item("item0"))
    System.gc()

    Thread.sleep(100)
    cache.get("item0") should be(None)
  }
}
