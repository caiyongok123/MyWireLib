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

import java.util.UUID

import akka.pattern.ask
import com.waz.api.OtrClient.DeleteCallback
import com.waz.api._
import com.waz.login.RegistrationUtils
import com.waz.model.AccountId
import com.waz.model.otr.ClientId
import com.waz.provision.ActorMessage.{DeleteAllOtherDevices, Login, RegisterPhone, Successful}
import com.waz.provision.EmailClientSuite
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.robolectric.annotation.Config
import org.scalatest.{FeatureSpec, OptionValues}

import scala.concurrent.duration._
import scala.util.Success

@Config(manifest = "tests/OtrAndroidManifest.xml")
class OtrPhoneLoginSpec extends FeatureSpec with OptionValues with ApiSpec with ProcessActorSpec with RegistrationUtils with EmailClientSuite { test =>
  override val autoLogin = false

  lazy val userId = AccountId()

  lazy val phone = randomPhoneNumber
  override lazy val email = s"android.test+${UUID.randomUUID}@wire.com"

  lazy val remote = registerDevice("second_device_remote")
  lazy val remote2 = registerDevice("email_remote")

  override def backendHostname: String = testBackend.baseUrl.toString.stripPrefix("https://")

  feature("Register by phone") {

    var prevClientId: ClientId = null

    scenario("Register on remote device") {
      val code = awaitUiFuture(fetchPhoneConfirmationCode(phone, KindOfAccess.REGISTRATION)).toOption.flatten
      code should not be empty

      awaitUiFuture(verifyPhoneNumber(phone, code.value, KindOfVerification.PREVERIFY_ON_REGISTRATION)) shouldEqual Success(true)

      remote ? RegisterPhone(phone.str, code.value.str, "name") should eventually(be(Successful))

      awaitUi(5.seconds)
    }

    scenario("Sign in on second device") {
      val code = awaitUiFuture(fetchPhoneConfirmationCode(phone, KindOfAccess.LOGIN_IF_NO_PASSWD)).toOption.flatten
      code should not be empty

      val self = awaitUiFuture(login(CredentialsFactory.phoneCredentials(phone.str, code.value.str))).toOption.value

      lazy val otrClient = self.getOtrClient
      lazy val otherClients = self.getOtherOtrClients

      withDelay {
        self.isLoggedIn shouldEqual true
        self.getClientRegistrationState shouldEqual ClientRegistrationState.REGISTERED

        otrClient should not be empty
        otherClients should have size 1
      }
      zmessaging.otrClientsStorage.get(zmessaging.selfUserId).await().flatMap(_.clients.get(zmessaging.clientId).flatMap(_.signalingKey)) shouldBe defined
      otrClient.get.asInstanceOf[com.waz.api.impl.otr.OtrClient].data.signalingKey shouldBe defined

      var passwordUpdated = false
      var emailUpdated = false

      self.setEmail(email, new CredentialsUpdateListener {
        override def onUpdateFailed(code: Int, message: String, label: String): Unit =
          fail(s"unable to update credentials: $code, $message, $label")
        override def onUpdated(): Unit = emailUpdated = true
      })
      self.setPassword(password, new CredentialsUpdateListener {
        override def onUpdated(): Unit = passwordUpdated = true
        override def onUpdateFailed(code: Int, message: String, label: String): Unit =
          fail(s"unable to update credentials: $code, $message, $label")
      })

      withDelay {
        emailUpdated shouldEqual true
        passwordUpdated shouldEqual true
      }
      emailClient.verifyEmail(email)

      withDelay {
        self.getClientRegistrationState shouldEqual ClientRegistrationState.REGISTERED
      }

      prevClientId = zmessaging.clientId
    }

    scenario("Requesting activation code returns password exists") {
      awaitUiFuture(fetchPhoneConfirmationCode(phone, KindOfAccess.LOGIN_IF_NO_PASSWD)).toOption shouldEqual Some(None)
    }

    scenario("Legacy activation code request is successfull") {
      awaitUiFuture(fetchPhoneConfirmationCode(phone, KindOfAccess.LOGIN)).toOption.flatten shouldBe defined
    }

    scenario("Remove current device on backend") {
      val self = api.getSelf
      (remote ? DeleteAllOtherDevices(password)).await() shouldEqual Successful

      withDelay {
        self.isLoggedIn shouldEqual false
      }
    }

    scenario("Sign back in with email") {
      val self = awaitUiFuture(login(CredentialsFactory.emailCredentials(email, password))).toOption.value

      withDelay {
        self.getClientRegistrationState shouldEqual ClientRegistrationState.REGISTERED
        self.isLoggedIn shouldEqual true
      }
      val clientId = zmessaging.clientId
      clientId should not be prevClientId
      api.account.get.clientId shouldEqual Some(clientId)
    }

    scenario("Remove device on backend again") {
      val self = api.getSelf
      (remote ? DeleteAllOtherDevices(password)).await() shouldEqual Successful

      withDelay {
        self.isLoggedIn shouldEqual false
      }
    }

    scenario("Remove remote device") {
      val self = awaitUiFuture(login(CredentialsFactory.emailCredentials(email, password))).toOption.value

      withDelay {
        self.getClientRegistrationState shouldEqual ClientRegistrationState.REGISTERED
        self.isLoggedIn shouldEqual true
      }

      (remote2 ? Login(email, password)).await()(patience(15.seconds))

      val clients = self.getOtherOtrClients
      withDelay { clients should have size 2 }

      clients.toVector foreach { client =>
        var deleted = Option.empty[OtrClient]
        client.delete(password, new DeleteCallback {
          override def onClientDeleted(client: OtrClient): Unit = deleted = Some(client)
          override def onDeleteFailed(error: String): Unit = {
            deleted = Some(null)
            fail(error)
          }
        })

        soon {
          deleted shouldBe defined
        }
      }

      awaitUi(15.seconds)
      withClue(clients.map(_.data).mkString) {
        clients shouldBe empty
      }
    }
  }
}
