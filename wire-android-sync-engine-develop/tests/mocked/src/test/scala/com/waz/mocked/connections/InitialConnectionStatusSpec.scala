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
package com.waz.mocked.connections

import com.waz.api.MockedClientApiSpec
import com.waz.model.SearchQuery.TopPeople
import com.waz.mocked.MockBackend
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}
import scala.concurrent.duration._

class InitialConnectionStatusSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with MockBackend with MockedClientApiSpec { test =>
  import DefaultPushBehaviour.Implicit

  lazy val convs = api.getConversations

  override protected def beforeAll(): Unit = {
    1 to 200 foreach { _ => addConnection() }
    addSearchResults(TopPeople, numConnected = 10, numUnknownConnected = 20)

    super.beforeAll()
  }

  scenario("initial sync") {
    withDelay { api.getSelf.isLoggedIn shouldEqual true }

    awaitUi(1.second)
    val search = api.search()
    val users = search.getTopPeople(40, Array.empty)

    withDelay {
      users.getAll should have size 30
      all (users.getAll) shouldBe 'connected
    } (30.seconds)
    withDelay { convs should have size 200 } (10.seconds)
  }
}
