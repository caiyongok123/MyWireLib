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
package com.waz.api

import com.waz.api.impl.EmailCredentials
import com.waz.model.{AccountData, EmailAddress}
import com.waz.service.ZMessaging
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._
import com.waz.ZLog.ImplicitTag._

class ZMessagingApiSpec extends FeatureSpec with OptionValues with Matchers with ProvisionedApiSpec {

  override val provisionFile = "/two_users.json"
  override val autoLogin = false

  implicit val timeout = 10.seconds: Timeout

  feature("Logging in") {

    scenario("Create ZMessagingApi and wait for init") {
    }

    scenario("Execute login") {
      val logged = login()

      logged shouldEqual true
    }

    scenario("Stay logged in after ZMessagingApi finishes") {
      login()
      pauseApi()
      api.onDestroy()

      api = new impl.ZMessagingApi()
      api.onCreate(context)
      initApi() shouldEqual true
    }

    scenario("Re-login with saved credentials") {
      login()
      pauseApi()
      api.onDestroy()

      // make sure current user data is saved in db (just to check that Robolectric is behaving correctly)
      Await.result(globalModule.accountsStorage.find(EmailCredentials(EmailAddress(email), Some(password))), timeout) match {
        case Some(data) =>
          data.email shouldEqual Some(EmailAddress(email))
        case res => fail(s"did not find current user $res")
      }

      awaitUi(3.seconds)

      ZMessaging.onCreate(context) // restarting
      api = new impl.ZMessagingApi()
      api.onCreate(context)
      initApi() shouldEqual true
    }

    scenario("call login twice with the same credentials") {
      login() shouldEqual true
      api.getSelf.isLoggedIn shouldEqual true

      login() shouldEqual true
      api.getSelf.isLoggedIn shouldEqual true
    }
  }

  feature("Logging out") {

    scenario("logout current user") {
      login() shouldEqual true
      val self = api.getSelf
      self.isLoggedIn shouldEqual true

      awaitUi(5.seconds)

      api.logout()

      withDelay { self.isLoggedIn shouldEqual false }
    }


    scenario("call logout and login right after it") {
      login() shouldEqual true
      api.getSelf.isLoggedIn shouldEqual true

      api.logout()
      withDelay(api.getSelf.isLoggedIn shouldEqual false)

      login() shouldEqual true
      api.getSelf.isLoggedIn shouldEqual true
    }

    scenario("logout and login as different user") {
      login() shouldEqual true
      api.getSelf.isLoggedIn shouldEqual true

      api.logout()
      withDelay(api.getSelf.isLoggedIn shouldEqual false)

      api.getSelf.isLoggedIn shouldEqual true
      api.getSelf.getName shouldEqual "auto2 user"
    }
  }

  feature("Lifecycle") {

    scenario("disconnect websocket client when paused") {
      login() shouldEqual true

      api.onPause()

      try {
        withDelay {
          zmessaging.websocket.connected.currentValue shouldEqual Some(false)
        }
      } finally {
        api.onResume()
      }
    }

    scenario("after pause and resume, self should not be updated when logged out") {
      api.logout()
      val self = api.getSelf

      withDelay {
        self.isLoggedIn shouldEqual false
        self.getUser shouldEqual null
      }

      @volatile var wasUpdated = false
      self.addUpdateListener(new UpdateListener {
        override def updated(): Unit = {
          wasUpdated = true
        }
      })
      api.onPause()
      awaitUi(1.second)

      wasUpdated = false
      api.onResume()

      awaitUi(1.second)
      wasUpdated shouldEqual false
    }

    scenario("destroy and reinitialize") {
      var api2 = ZMessagingApiFactory.getInstance(context)
      api2.onCreate(context)

      var self2: Option[Self] = None
      api2.onInit(new InitListener {
        override def onInitialized(user: Self): Unit = self2 = Option(user)
      })
      withDelay { self2 should be(defined) }
      api2.onResume()

      var loggedIn2 = false
      api2.login(CredentialsFactory.emailCredentials(email, password), new LoginListener {
        override def onFailed(code: Int, message: String, label: String): Unit = loggedIn2 = false
        override def onSuccess(user: Self): Unit = loggedIn2 = true
      })
      withDelay { loggedIn2 shouldEqual true }

      api2.onPause()
      api2.onDestroy()

      api2 = ZMessagingApiFactory.getInstance(context)
      api2.onCreate(context)

      @volatile var loggedIn = false
      api2.onInit(new InitListener {
        override def onInitialized(user: Self): Unit = {
          loggedIn = user.isLoggedIn
          self2 = Option(user)
        }
      })
      withDelay { loggedIn shouldEqual true }
      withDelay { self2.value.isLoggedIn shouldEqual true }

      api2.onDestroy()
    }
  }
}
