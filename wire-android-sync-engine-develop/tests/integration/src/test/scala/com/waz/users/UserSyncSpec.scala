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
package com.waz.users

import com.waz.ZLog._
import com.waz.api._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.duration._

class UserSyncSpec extends FeatureSpec with Matchers with ProvisionedApiSpec {
  private implicit val logTag: LogTag = logTagFor[UserSyncSpec]

  override val provisionFile = "/four_users_connected.json"

  lazy val contacts = api.search().getConnectionsByName("a", 30, Array.empty)

  scenario("Sync contacts on start") {
    withDelay {
      contacts.getAll should have size 3
    }
  }

  scenario("Request sync if users were not updated recently") {
    val userIds = contacts.getAll.toSeq.map(_.id)

    zmessaging.usersStorage.updateAll2(userIds, _.copy(syncTimestamp = 0)).await()

    api.onPause()

    zmessaging.users.getUsers(userIds).await()

    withDelay {
      val users = zmessaging.usersStorage.listAll(userIds).await()
      users foreach { u => u.syncTimestamp should be > 0L }
    }

    awaitUi(2.seconds)

    zmessaging.notifStorage.list().await() shouldBe empty

    api.onResume()
  }

  scenario("Don't generate notifications when connections are loaded") {
    api.onPause()

    zmessaging.sync.syncConnectedUsers().await()
    zmessaging.sync.syncConnections(None).await()

    awaitUi(5.seconds)

    zmessaging.notifStorage.list().await() shouldBe empty

    api.onResume()
  }
}
