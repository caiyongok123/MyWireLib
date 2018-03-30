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
package com.waz.service.otr

import com.waz.RobolectricUtils
import com.waz.testutils._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

@Ignore class CryptoBoxServiceSpec extends FeatureSpec with Matchers with OptionValues with BeforeAndAfter with RobolectricTests with RobolectricUtils with ScalaFutures with DefaultPatienceConfig { test =>

  feature("Generating new prekeys") {

    lazy val zms = new MockAccountManager()
    lazy val service = zms.cryptoBox

    scenario("generate new prekeys when lastId pref is broken") {
      (service.lastPreKeyId := 5).futureValue

      val prekeys = service.generatePreKeysIfNeeded((50 to 99).toSeq :+ 0xFFFF).futureValue
      prekeys.map(_.id) shouldEqual (100 to 149).toSeq
      service.lastPreKeyId().futureValue shouldEqual 149
    }
  }
}
