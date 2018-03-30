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

import com.waz.RobolectricUtils
import com.waz.model.{Handle, UserId}
import com.waz.testutils.{MockUiModule, MockZMessaging}
import org.scalatest.{OptionValues, _}

import scala.util.Random

@Ignore class UsernamesSpec extends FeatureSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with OptionValues with RobolectricTests with RobolectricUtils {

  lazy val selfId = UserId()
  lazy val zmessaging = new MockZMessaging(selfUserId = selfId)
  implicit lazy val ui = new MockUiModule(zmessaging)
  var usernames: Usernames = null

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    ui.onCreate(testContext)
    ui.onResume()

    usernames = new Usernames()(ui)
  }

  Random.setSeed(0)
  
  scenario ("Username generation with latin characters only") {
    val genName = usernames.generateUsernameFromName("Wire", null)
    genName should be("wire")
  }

  scenario ("Username generation with latin characters and space") {
    val genName = usernames.generateUsernameFromName("Wire Wireson", null)
    genName should be("wirewireson")
  }

  scenario ("Username generation with latin characters from extended alphabet") {
    val genName = usernames.generateUsernameFromName("Æéÿüíøšłźçñ", null)
    genName should be("aeeyuioslzcn")
  }

  scenario ("Username generation with emojis only") {
    val genName = usernames.generateUsernameFromName("\uD83D\uDE3C", null)
    genName should be("")
  }

  scenario ("Username generation with cyrillic characters") {
    val genName = usernames.generateUsernameFromName("Даша", null)
    genName should be("dasa")
  }

  scenario ("Username generation with arabic characters") {
    val genName = usernames.generateUsernameFromName("داريا", null)
    genName should be("darya")
  }

  scenario ("Username generation with chinese characters") {
    val genName = usernames.generateUsernameFromName("孟利", null)
    genName should be("mengli")
  }

  scenario("Querying for usernames with @") {
    val handle = Handle("abcd")
    handle.startsWithQuery("@AbC") should be(true)
  }

  scenario("Querying for usernames without @") {
    val handle = Handle("abcd")
    handle.startsWithQuery("AbC") should be(true)
  }
}
