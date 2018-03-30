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

import com.waz.api._
import com.waz.api.impl.EmailCredentials
import com.waz.model.ConversationData.ConversationType
import com.waz.model.EmailAddress
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.utils._
import com.waz.znet.Response.SuccessHttpStatus
import com.waz.znet.{JsonObjectResponse, Request, Response}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

import scala.concurrent.duration._

class LoginLogoutSpec extends FeatureSpec with Matchers with GivenWhenThen with ProvisionedApiSpec { test =>
  implicit val timeout: Timeout = 30.seconds
  import com.waz.threading.Threading.Implicits.Background

  override val provisionFile = "/two_users_connected.json"
  override val autoLogin: Boolean = false

  lazy val convs = api.getConversations

  feature("login and logout") {

    scenario("login") {
      login()
      withDelay(convs.size shouldEqual 1)
      convs.get(0).getType shouldEqual ConversationType.OneToOne
    }

    scenario("logout") {
      api.logout()
      withDelay(convs should be(empty))
    }

    scenario("login as other user") {
      awaitUi(3.seconds)

      @volatile var email = ""
//      api.login(CredentialsFactory.emailCredentials(provisionedEmail("auto2"), "auto2_pass"), new LoginListener {
//        override def onSuccess(user: Self): Unit = email = user.getEmail
//        override def onFailed(code: Int, message: String, label: String): Unit = email = message
//      })
      withDelay {
        email shouldEqual provisionedEmail("auto2") // FIXME returns "auto1" sometimes
        convs.size should be > 0
      }

      convs.get(0).getType shouldEqual ConversationType.OneToOne
      api.getSelf.getUser.getId shouldEqual provisionedUserId("auto2").str

      val auto1Id = provisionedUserId("auto1").str
      withDelay {
        convs.find(_.getId == auto1Id).map(_.getType) shouldEqual Some(ConversationType.OneToOne)
      }
    }

    scenario("logout again") {
      api.logout()
      withDelay(convs should be(empty))
    }

    scenario("re-login as first user") {
      login()

      withDelay {
        convs.size shouldEqual 1
        convs.find(_.getId == provisionedUserId("auto2").str).map(_.getType) shouldEqual Some(ConversationType.OneToOne)
      }
    }

    scenario("force logout UI when cookie is invalidated on backend") {
      var logged: List[Boolean] = Nil
      val self = api.getSelf
      self.addUpdateListener(new UpdateListener {
        override def updated(): Unit = logged ::= self.isLoggedIn
      })

      val invalidateCookies = zmessaging.zNetClient(Request.Get("/cookies")) flatMap {
        case Response(SuccessHttpStatus(), JsonObjectResponse(js), _) =>
          val cs = js.getJSONArray("cookies")
          zmessaging.zNetClient(Request.Post("/cookies/remove", Json("email" -> email, "password" -> password, "ids" -> cs))) map {
            case Response(SuccessHttpStatus(), _, _) => true
          }
      }

      invalidateCookies.await() shouldEqual true

      accounts.storage.update(zmessaging.accountId, _.copy(password = None)).await()
      zmessaging.auth.invalidateToken()

      api.search().getRecommendedPeople("test", 10, Array.empty)

      withDelay {
        self.isLoggedIn shouldEqual false
        logged should contain(false)
      }
    }
  }
}
