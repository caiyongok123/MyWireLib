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

import com.waz.RobolectricUtils
import com.waz.testutils.{MockUiModule, MockZMessaging}
import com.waz.utils._
import com.waz.utils.events.Publisher
import org.robolectric.Robolectric
import org.scalatest._
import com.waz.ZLog.ImplicitTag._

@Ignore class UiCacheUpdaterSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with BeforeAndAfter with RobolectricTests with RobolectricUtils {

  case class Item(key: String, value: String)

  var reloaded = Seq.empty[Item]
  var updated = Seq.empty[Item]

  implicit object Cached extends UiCached[Item, String, Item] {
    override def reload(item: Item): Unit = reloaded = reloaded :+ item
    override def update(item: Item, d: Item): Unit = updated = updated :+ d
    override def toUpdateMap(values: Seq[Item]) = values.map(i => i.key -> i)(collection.breakOut)
  }

  implicit lazy val ui = new MockUiModule(new MockZMessaging())

  lazy val publisher = new Publisher[Item]

  lazy val cache = returning(new UiCache[String, Item](10)) { cache =>
    UiCacheUpdater(cache, _ => publisher)
  }

  val items = Seq(Item("k1", "v1"), Item("k2", "v2"))

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    ui.onCreate(Robolectric.application)
  }

  scenario("Reload on resume") {
    withDelay(ui.currentZms.currentValue.flatten shouldBe 'defined)
    items foreach { i => cache.put(i.key, i) }

    reloaded shouldBe empty

    ui.onResume()
    withDelay { reloaded.toSet shouldEqual items.toSet }
  }

  scenario("Update on signal") {
    updated shouldBe empty

    publisher ! Item("k3", "v3")
    updated shouldBe empty

    publisher ! Item("k2", "v22")
    withDelay { updated shouldEqual Seq(Item("k2", "v22")) }
  }

  scenario("Don't update when paused and destroyed") {
    updated = Nil

    ui.onPause()
    publisher ! Item("k1", "v11")
    updated shouldBe empty

    ui.onDestroy()
    publisher ! Item("k1", "v11")
    updated shouldBe empty
  }

  scenario("Update on signal when created and resumed again") {
    reloaded = Nil
    ui.onCreate(Robolectric.application)
    ui.onResume()
    withDelay { reloaded.toSet shouldEqual items.toSet }

    publisher ! Item("k1", "v11")
    withDelay { updated shouldEqual Seq(Item("k1", "v11")) }
  }
}
