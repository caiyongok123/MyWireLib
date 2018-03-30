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
package com.waz.connections

import com.waz.api.ProvisionedApiSpec
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.duration._

class SelfConnectionSpec extends FeatureSpec with Matchers with ProvisionedApiSpec {
  val provisionFile = "/one_user.json"

  implicit val timeout: Timeout = 15.seconds

  lazy val convs = api.getConversations

  feature("Send request to self") {
    scenario("connect request to self should never be sent") {
      awaitUi(1.second)

      val conv = api.getSelf.getUser.connect("test")

      awaitUi(3.seconds)

      convs should be (empty)
    }
  }
}
