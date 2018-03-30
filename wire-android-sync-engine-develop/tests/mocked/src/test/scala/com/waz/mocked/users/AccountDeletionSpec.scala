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
package com.waz.mocked.users

import com.waz.api._
import com.waz.mocked.MockBackend
import com.waz.model._
import com.waz.testutils.Matchers._
import com.waz.testutils._
import org.robolectric.annotation.Config
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers, OptionValues}
import com.waz.ZLog.ImplicitTag._

import scala.concurrent.duration._

class AccountDeletionSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with OptionValues with MockBackend with MockedClientApiSpec {
  import DefaultPushBehaviour.Implicit

  lazy val selfId = UserId(api.getSelf.getUser.getId)

  lazy val convs = api.getConversations

  override protected def beforeAll(): Unit = {
    addConnection(UserId("meep"))

    super.beforeAll()

    awaitUi(api.getSelf.isLoggedIn)
    awaitUi(convs.size == 1)(10.seconds)
  }

  feature("Delete a user account") {
    scenario("Contact deletes her account") {
      val meep = api.getUser("meep")

      soon { meep.isDeleted shouldEqual false }
      userDeleteEvent(UserId("meep"))
      soon { meep.isDeleted shouldEqual true }
    }

    scenario("Delete own account") {
      api.getSelf.isLoggedIn shouldEqual true
      val account = api.account.value
      account.accountData.head.await().cookie should be (defined)
      userDeleteEvent(selfId)
      soon {
        api.getSelf.isLoggedIn shouldEqual false
        account.accountData.currentValue shouldEqual None
      }
    }
  }

  def userDeleteEvent(user: UserId) = addNotification(UserDeleteEvent(user))
}
