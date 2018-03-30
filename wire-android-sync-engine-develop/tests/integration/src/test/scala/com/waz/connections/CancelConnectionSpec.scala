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

import akka.pattern.ask
import com.waz.api._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserData.ConnectionStatus
import com.waz.provision.ActorMessage._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.duration._

class CancelConnectionSpec extends FeatureSpec with Matchers with ProvisionedApiSpec with ThreadActorSpec {
  val provisionFile = "/two_users.json"

  override val initBehaviour: InitBehaviour = InitManually

  lazy val auto2 = registerDevice("ConnectionSpec_auto2")

  scenario("auto2: initial sync") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    awaitUi(1.second)
  }

  scenario("Logging in again should not un-hide a cancelled conversation") {
    withInitializedApi {
      val convs = api.getConversations
      val user = api.getUser(provisionedUserId("auto2").str)
      val conv = user.connect("hello?")

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.PendingFromUser
        conv.getType shouldEqual ConversationType.WaitForConnection
        convs should contain(conv)
        conv.getName shouldEqual "auto2 user"
      }

      user.cancelConnection()

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.Cancelled
        convs shouldBe empty
      }
    }

    awaitUi(1.second)
    3.times(System.gc())

    withInitializedApi {
      val convs = api.getConversations
      val user = api.getUser(provisionedUserId("auto2").str)

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.Cancelled
        convs shouldBe empty
      }

      awaitUi(2.seconds)

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.Cancelled
        convs shouldBe empty
      }
    }
  }
}
