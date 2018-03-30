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
import com.waz.utils.events.IntSignal
import org.robolectric.Robolectric
import org.scalatest._

import scala.concurrent.duration._

@Ignore class SignalLoadingSpec extends FeatureSpec with Matchers with GivenWhenThen with RobolectricTests with RobolectricUtils with BeforeAndAfter {

  lazy val valueSignal = new IntSignal(1)

  lazy val zMessaging = new MockZMessaging()

  implicit lazy val ui = new MockUiModule(zMessaging)

  class Item(var value: Int = -1) extends SignalLoading {
    val loader = addLoader({ _ => valueSignal }, 0) { value = _ }
  }

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

    scenario("Return default value when no zmessaging is available") {
      awaitUi(100.millis)
      ui.setCurrent(None)
      val item = new Item()
      withDelay(item.value shouldEqual 0)
      ui.setCurrent(Some(zMessaging))
      withDelay(item.value shouldEqual 1)
      ui.setCurrent(None)
      withDelay(item.value shouldEqual 0)
    }

    scenario("Update when signal value is changed") {
      val item = new Item()
      ui.setCurrent(Some(zMessaging))
      withDelay(item.value shouldEqual 1)
      valueSignal ! 2
      withDelay(item.value shouldEqual 2)
      valueSignal ! 3
      withDelay(item.value shouldEqual 3)
    }

    scenario("Don't update on signal changes when there is no zmessaging") {
      val item = new Item()
      withDelay(item.value shouldEqual 1)
      awaitUi(100.millis)
      ui.setCurrent(None)
      withDelay(item.value shouldEqual 0)
      valueSignal ! 2
      valueSignal ! 3
      awaitUi(100.millis)
      item.value shouldEqual 0
      ui.setCurrent(Some(zMessaging))
      withDelay(item.value shouldEqual 3)
    }
  }

  feature("UI lifecycle") {

    scenario("Don't update when UI is paused") {
      val item = new Item()
      withDelay(item.value shouldEqual 1)
      ui.onPause()
      valueSignal ! 2
      valueSignal ! 3
      awaitUi(100.millis)
      item.value shouldEqual 1
    }

    scenario("Update value on resume") {
      val item = new Item()
      withDelay(item.value shouldEqual 1)
      ui.onPause()
      valueSignal ! 2
      valueSignal ! 3
      ui.onResume()
      awaitUi(100.millis)
      withDelay(item.value shouldEqual 3)
    }
  }

  feature("SignalLoader cleanup") {
    import language.implicitConversions
    implicit def signalLoader(sub: LoaderSubscription): SignalLoader[_] = sub.asInstanceOf[SignalLoader[_]]

    def prepareLoader = {
      val item = new Item(0)
      withDelay { item.value shouldEqual 1 }
      item.loader
    }

    scenario("Destroy loader when reference is lost and reference queue is cleared") {
      val loader = prepareLoader

      withDelay {
        System.gc()
        loader.ref.get should be('empty)
        valueSignal.isWired shouldEqual true
        SignalLoader.dropQueue()
        valueSignal.isWired shouldEqual false
      }
    }

    scenario("Destroy loader when reference is lost and signal fired") {
      val loader = prepareLoader

      withDelay {
        System.gc()
        loader.ref.get should be('empty)
      }
      valueSignal.isWired shouldEqual true
      valueSignal ! 2
      withDelay {
        valueSignal.isWired shouldEqual false
      }
      SignalLoader.dropQueue() // does nothing
    }

    scenario("Destroy loader when zmessaging is changed after reference is lost") {
      val loader = prepareLoader

      withDelay {
        System.gc()
        loader.ref.get should be('empty)
      }
      valueSignal.isWired shouldEqual true
      ui.setCurrent(None)
      withDelay {
        valueSignal.isWired shouldEqual false
        SignalLoader.dropQueue() // does nothing
      }
    }
  }
}
