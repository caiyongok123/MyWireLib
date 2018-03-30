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
import com.waz.api.Subscriber
import com.waz.api.impl.UiSignal
import com.waz.testutils.{MockUiModule, MockZMessaging}
import com.waz.utils.events.IntSignal
import org.robolectric.Robolectric
import org.scalatest._

import scala.concurrent.duration._

@Ignore class UiSignalSpec extends FeatureSpec with Matchers with GivenWhenThen with RobolectricTests with RobolectricUtils with BeforeAndAfter {

  lazy val valueSignal = new IntSignal(1)

  lazy val zMessaging = new MockZMessaging()

  implicit lazy val ui = new MockUiModule(zMessaging)

  def uiSignal = UiSignal(_ => valueSignal)

  before {
    ui.onCreate(Robolectric.application)
    ui.onResume()

    valueSignal ! 1
  }

  after {
    while (ui.resumeCount > 0) ui.onPause()
    while (ui.createCount > 0) ui.onDestroy()
  }

  feature("Loading") {

    scenario("Signal is empty when no zmessaging is available, but is not cleared when zmessaging is removed") {
      ui.setCurrent(None)
      val signal = uiSignal
      signal should be(empty)
      ui.setCurrent(Some(zMessaging))
      withDelay(signal should not be empty)
      signal.get shouldEqual 1
      ui.setCurrent(None)
      awaitUi(100.millis)
      signal should not be empty
      signal.get shouldEqual 1
    }

    scenario("Update when signal value is changed") {
      val signal = uiSignal
      ui.setCurrent(Some(zMessaging))
      withDelay(signal.get shouldEqual 1)
      valueSignal ! 2
      withDelay(signal.get shouldEqual 2)
      valueSignal ! 3
      withDelay(signal.get shouldEqual 3)
    }

    scenario("Notify subscriber on value changes") {
      val signal = uiSignal
      var res = 0
      signal.subscribe(new Subscriber[Int] {
        override def next(value: Int): Unit = res = value;
      })

      ui.setCurrent(Some(zMessaging))
      withDelay(res shouldEqual 1)
      valueSignal ! 2
      withDelay(res shouldEqual 2)
      valueSignal ! 3
      withDelay(res shouldEqual 3)
    }
  }
}
