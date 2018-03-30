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
import com.waz.ZLog._
import com.waz.api._
import com.waz.api.impl.{AccentColors, ImageAsset}
import com.waz.testutils.BitmapSpy
import com.waz.model.UserUpdateEvent
import com.waz.provision.ActorMessage._
import com.waz.testutils.Matchers._
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.duration._

class SelfUserSyncSpec extends FeatureSpec with Matchers with ProvisionedApiSpec with ThreadActorSpec {
  private implicit val logTag: LogTag = logTagFor[SelfUserSyncSpec]

  override val autoLogin: Boolean = false
  override val provisionFile = "/two_users_connected.json"

  var self: Self = null
  var trackingId: Option[String] = None

  lazy val auto2 = registerDevice("SelfUserSyncSpec_auto2")
  lazy val otherClient = registerDevice("SelfUserSyncSpec_otherClient")

  override def beforeAll(): Unit = {
    super.beforeAll()
    emailClient.deleteVerificationEmails()
  }

  scenario("self should be initialized on login") {
    api.login(CredentialsFactory.emailCredentials(email, password), new LoginListener {
      override def onSuccess(user: Self): Unit = {
        println(s"login success: ${user.getUser}")
        self = user
      }

      override def onFailed(code: Int, message: String, label: String): Unit = {
        fail("login failed")
      }
    })

    withDelay {
      self should not be null
    }
    withDelay {
      self.getName shouldEqual "auto1 user"
    }
    self.getEmail shouldEqual provisionedEmail("auto1")
    trackingId should be('defined)
    Option(self.getUser) should be('defined)

    val convs = api.getConversations
    withDelay(convs.size should be > 0)(30.seconds)
  }

  scenario("Init other client") {
    otherClient ? Login(email, password) should eventually(be(Successful))
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    awaitUi(5.seconds)
  }

  scenario("Update self user name and sync it to server") {
    otherClient ? UpdateProfileName("test") should eventually(be(Successful))
    withDelay(self.getName shouldEqual "test")
  }

  scenario("Receive update from other user name change") {
    auto2 ? UpdateProfileName("test2") should eventually(be(Successful))
    withDelay(self.getName shouldEqual "test2")
  }

  scenario("Update self user color and sync it to server") {
    val other = AccentColors.getColors.last

    otherClient ? UpdateProfileColor(other) should eventually(be(Successful))
    withDelay(self.getAccent shouldEqual other)
  }

  scenario("Update self user email and sync it to server") {
    @volatile var updated = false
    val email = provisionedEmail("auto3")

    self.setEmail(email, new CredentialsUpdateListener {
      override def onUpdateFailed(code: Int, message: LogTag, label: LogTag): Unit = ()

      override def onUpdated(): Unit = updated = true
    })

    withDelay {
      updated shouldEqual true
      self.getEmail shouldEqual provisionedEmail("auto1")
      self.accountActivated shouldEqual true // the old email address is still verified...
    }

    withDelay {
      emailClient.getVerificationLink(email) should be('defined)
    }(30.seconds)

    emailClient.verifyEmail(email)(email) shouldEqual true

    withDelay {
      self.getEmail shouldEqual email
      self.accountActivated shouldEqual true
    }

    login(email) shouldEqual true
  }

  scenario("post self picture") {
    val ia = self.getPicture
    implicit val timeout = 45.seconds: Timeout

    withPush {
      case UserUpdateEvent(_, _) => true
    } {
      otherClient ? UpdateProfileImage("/images/penguin.png") should eventually(be(Successful))
    }
    withDelay {
      self.getPicture should not be ia
    }

    val pic = self.getPicture
    withDelay(pic.getWidth should be > 0)

    val bitmap = new BitmapSpy(pic, 500)

    withDelay {
      bitmap.failed shouldEqual false
      bitmap.result shouldBe 'defined
      bitmap.result.get.getWidth should be > 128
    }

  }

  scenario("clear self picture") {
    withPush {
      case UserUpdateEvent(_, _) => true
    } {
      otherClient ? ClearProfileImage should eventually(be(Successful))
    }
    withDelay {
      self.getPicture.isEmpty shouldEqual true
      self.getPicture shouldEqual ImageAsset.Empty
    }
  }

  scenario("post user picture") {
    val user2 = api.getUser(provisionedUserId("auto2").str)
    implicit val timeout = 45.seconds: Timeout

    withPush {
      case UserUpdateEvent(_, _) => true
    } {
      auto2 ? UpdateProfileImage("/images/penguin.png") should eventually(be(Successful))
    }
    withDelay {
      user2.getPicture should not be ImageAsset.Empty
    }

    val pic = user2.getPicture
    withDelay(pic.getWidth should be > 0)

    val bitmap = new BitmapSpy(pic, 500)

    withDelay {
      bitmap.failed shouldEqual false
      bitmap.result shouldBe 'defined
      bitmap.result.get.getWidth should be > 128
    }
  }


  scenario("clear user picture") {
    val user2 = api.getUser(provisionedUserId("auto2").str)
    withDelay(user2.getPicture should not be ImageAsset.Empty)
    withPush {
      case UserUpdateEvent(_, _) => true
    } {
      auto2 ? ClearProfileImage should eventually(be(Successful))
    }
    withDelay {
      user2.getPicture.isEmpty shouldEqual true
      user2.getPicture shouldEqual ImageAsset.Empty
    }
  }
}
