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

import akka.pattern.ask
import com.waz.api._
import com.waz.model.ConversationData.ConversationType
import com.waz.provision.ActorMessage.{Login, SendText, Successful}
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.testutils._
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

import scala.concurrent.duration._

class PhoneEmailLoginSpec extends FeatureSpec with Matchers with GivenWhenThen with ProvisionedApiSpec with ProcessActorSpec { test =>

  override val provisionFile = "/two_users_connected.json"
  override val autoLogin = false

  lazy val phone = randomPhoneNumber
  lazy val convs = api.getConversations
  lazy val conv = convs.get(0)
  def msgs = listMessages(conv.id)
  lazy val self = api.getSelf

  lazy val auto2 = registerDevice("auto2")

  scenario("Add phone to user") {
    userProvs("auto1").addPhone(phone).await() shouldBe 'right
  }

  scenario("login with phone") {
    accounts.requestPhoneConfirmationCode(phone, KindOfAccess.LOGIN).await()
    val Right(code) = userProvs("auto1").internalBackend.getPhoneLoginCode(phone).await()

    login(CredentialsFactory.phoneCredentials(phone.str, code.str)) shouldEqual true
    withDelay {
      convs.size shouldEqual 1
      self.isLoggedIn shouldEqual true
    }
    conv.getType shouldEqual ConversationType.OneToOne
  }

  scenario("receive message from other user") {
    (auto2 ? Login(provisionedEmail("auto2"), "auto2_pass")).await()(patience(10.seconds)) shouldEqual Successful

    auto2 ? SendText(conv.data.remoteId, "test message")

    withDelay {
      msgs should not be empty
      msgs.last.contentString shouldEqual "test message"
    }
  }

  scenario("logout") {
    api.logout()
    withDelay {
      self.isLoggedIn shouldEqual false
      convs shouldBe empty
    }
    awaitUi(3.seconds)
  }

  scenario("login with email") {
    login() shouldEqual true

    lazy val convs = api.getConversations
    lazy val conv = convs.get(0)

    withDelay {
      self.isLoggedIn shouldEqual true
      convs should not be empty
      listMessages(conv.id) should not be empty
      lastMessage(conv.id).get.contentString shouldEqual "test message"
    }
  }
}
