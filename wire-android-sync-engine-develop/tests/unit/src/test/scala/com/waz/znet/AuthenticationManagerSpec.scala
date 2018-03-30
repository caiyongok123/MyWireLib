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
package com.waz.znet

import com.waz.api.impl.ErrorResponse
import com.waz.content.AccountsStorage
import com.waz.model.{AccountData, AccountId, EmailAddress}
import com.waz.specs.AndroidFreeSpec
import com.waz.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import com.waz.utils.events.EventContext
import com.waz.utils.returning
import com.waz.znet.AuthenticationManager.{Cookie, Token}
import com.waz.znet.LoginClient.InsufficientCredentials
import com.waz.znet.Response.{HttpStatus, Status}

import scala.concurrent.Future

class AuthenticationManagerSpec extends AndroidFreeSpec {

  val loginClient = mock[LoginClient]
  val accId       = AccountId()
  val accStorage  = mock[AccountsStorage]

  feature("Successful logins") {
    scenario("Return authentication token if valid") {

      val token = Token("token", "token", System.currentTimeMillis() + AuthenticationManager.ExpireThreshold + 1000)

      val account = AccountData(accId,
        email       = Some(EmailAddress("blah@blah.com")),
        password    = Some("password"),
        accessToken = Some(token)
      )

      (accStorage.get _).expects(accId).anyNumberOfTimes().returning(Future.successful(Some(account)))
      val manager = getManager

      result(manager.currentToken()) shouldEqual Right(token)
    }

    scenario("Request new token if old token is invalid") {

      val cookie = Cookie("cookie")

      val oldToken = Token("token", "token", System.currentTimeMillis())
      val newToken = Token("newToken", "token", System.currentTimeMillis() + 15 * 60 * 1000L)

      val oldAccount = AccountData(accId,
        email       = Some(EmailAddress("blah@blah.com")),
        password    = Some("password"),
        accessToken = Some(oldToken),
        cookie      = Some(cookie)
      )

      val newAccount = oldAccount.copy(accessToken = Some(newToken))

      (accStorage.get _).expects(accId).anyNumberOfTimes().returning(Future.successful(Some(oldAccount)))
      (accStorage.update _).expects(*, *).onCall { (id, updater) =>
        updater(oldAccount) shouldEqual newAccount
        Future.successful(Some((oldAccount, newAccount)))
      }

      (loginClient.access _).expects(cookie, Some(oldToken)).returning(CancellableFuture.successful(Right(newToken, None)))

      val manager = getManager

      result(manager.currentToken()) shouldEqual Right(newToken)
    }

    scenario("Cookie in access response should be saved") {

      val oldCookie = Cookie("oldCookie")
      val newCookie = Cookie("newCookie")

      val oldToken = Token("token", "token", System.currentTimeMillis())
      val newToken = Token("newToken", "token", System.currentTimeMillis() + 15 * 60 * 1000L)

      val updateDispatcher = new SerialDispatchQueue()

      var account = AccountData(accId,
        email       = Some(EmailAddress("blah@blah.com")),
        password    = Some("password"),
        accessToken = Some(oldToken),
        cookie      = Some(oldCookie)
      )

      (accStorage.get _).expects(accId).anyNumberOfTimes().onCall((_: AccountId) => updateDispatcher(Some(account)).future)
      (accStorage.update _).expects(*, *).onCall { (_, updater) =>
        updateDispatcher {
          val old = account
          account = updater(account)
          Some((old, account))
        }
      }

      (loginClient.access _).expects(oldCookie, Some(oldToken)).returning(CancellableFuture.successful(Right(newToken, Some(newCookie))))
      val manager = getManager

      result(manager.currentToken()) shouldEqual Right(newToken)

      account.accessToken shouldEqual Some(newToken)
      account.cookie      shouldEqual Some(newCookie)
    }

    scenario("Multiple calls to access should only trigger at most one request") {
      val cookie = Cookie("cookie")

      val oldToken = Token("token", "token", System.currentTimeMillis())
      val newToken = Token("newToken", "token", System.currentTimeMillis() + 15 * 60 * 1000L)

      val updateDispatcher = new SerialDispatchQueue()

      var account = AccountData(accId,
        email       = Some(EmailAddress("blah@blah.com")),
        password    = Some("password"),
        accessToken = Some(oldToken),
        cookie      = Some(cookie)
      )

      (accStorage.get _).expects(accId).anyNumberOfTimes().onCall((_: AccountId) => updateDispatcher(Some(account)).future)
      (accStorage.update _).expects(*, *).onCall { (id, updater) =>
        updateDispatcher {
          val oldAccount = account
          account = updater(account)
          Some((oldAccount, account))
        }
      }

      (loginClient.access _).expects(cookie, Some(oldToken)).once().returning(CancellableFuture.successful(Right(newToken, None)))

      val manager = getManager

      import Threading.Implicits.Background
      val futures = Future.sequence((1 to 10).map(_ => manager.currentToken()))

      result(futures).foreach(_ shouldEqual Right(newToken))
    }
  }

  feature("Insufficient login information") {
    scenario("Logout after providing invalid credentials when new token is needed") {

      val cookie = Cookie("cookie")

      val oldToken = Token("token", "token", System.currentTimeMillis())

      val updateDispatcher = new SerialDispatchQueue()

      var account = AccountData(accId,
        email       = Some(EmailAddress("blah@blah.com")),
        password    = None,
        accessToken = Some(oldToken),
        cookie      = Some(cookie)
      )

      (accStorage.get _).expects(accId).anyNumberOfTimes().onCall((_: AccountId) => updateDispatcher(Some(account)).future)
      (accStorage.update _).expects(*, *).onCall { (id, updater) =>
        updateDispatcher {
          val oldAccount = account
          account = updater(account)
          Some((oldAccount, account))
        }
      }

      (loginClient.access _).expects(cookie, Some(oldToken)).returning(CancellableFuture.successful(Left((Option(""), ErrorResponse(Status.Forbidden, "", "")))))

      (loginClient.login _).expects(account.copy(cookie = None, accessToken = None)).once().returning(CancellableFuture.successful(Left((None, ErrorResponse.internalError(InsufficientCredentials)))))

      val manager = getManager

      var logoutCalled = false
      manager.onInvalidCredentials(_ => logoutCalled = true)(EventContext.Global)

      result(manager.currentToken()) shouldEqual Left(HttpStatus(499, LoginClient.InsufficientCredentials))
      logoutCalled shouldEqual true
    }
  }

  feature("Failures") {
    scenario("Retry if login not successful for unknown reasons") {

      val cookie = Cookie("cookie")

      val oldToken = Token("token", "token", System.currentTimeMillis())
      val newToken = Token("newToken", "token", System.currentTimeMillis() + 15 * 60 * 1000L)

      val updateDispatcher = new SerialDispatchQueue()

      var account = AccountData(accId,
        email       = Some(EmailAddress("blah@blah.com")),
        password    = None,
        accessToken = Some(oldToken),
        cookie      = Some(cookie)
      )

      (accStorage.get _).expects(accId).anyNumberOfTimes().onCall((_: AccountId) => updateDispatcher(Some(account)).future)
      (accStorage.update _).expects(*, *).onCall { (id, updater) =>
        updateDispatcher {
          val oldAccount = account
          account = updater(account)
          Some((oldAccount, account))
        }
      }

      var attempts = 0
      (loginClient.access _).expects(cookie, Some(oldToken)).anyNumberOfTimes().onCall { (cookie, token) =>
        returning(CancellableFuture.successful(attempts match {
          case 0|1|2 => Left((Option(""), ErrorResponse(500, "Some server error", "Some server error")))
          case 3     => Right((newToken, None))
          case _     => fail("Unexpected number of access attempts")
        }))(_ => attempts += 1)
      }

      val manager = getManager

      result(manager.currentToken()) shouldEqual Right(newToken)
    }
  }

  def getManager = new AuthenticationManager(accId, accStorage, loginClient, tracking)
}
