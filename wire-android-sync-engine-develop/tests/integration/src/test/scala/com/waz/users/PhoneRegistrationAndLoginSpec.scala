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

import com.waz.api.ZMessagingApi.{PhoneConfirmationCodeRequestListener, PhoneNumberVerificationListener, RegistrationListener}
import com.waz.api._
import com.waz.api.impl.AccentColor
import com.waz.model._
import com.waz.provision.InternalBackendClient
import com.waz.utils._
import com.waz.testutils.randomPhoneNumber
import com.waz.testutils.Implicits._
import org.scalatest.{FeatureSpec, OptionValues}

import com.waz.testutils.Matchers._
import scala.concurrent.Await
import scala.concurrent.duration._

class PhoneRegistrationAndLoginSpec extends FeatureSpec with OptionValues with ApiSpec { test =>
  override val autoLogin = false

  lazy val userId = AccountId()

  lazy val phone = randomPhoneNumber
  override lazy val email = s"android.test+${UUID.randomUUID}@wire.com"

  lazy val internalBackendClient = new InternalBackendClient(globalModule.client, testBackend)

  var confirmationCodeSent: Boolean = false
  var passwordExists = false
  var confirmationCode = Option.empty[ConfirmationCode]

  lazy val confirmationCodeListener = new PhoneConfirmationCodeRequestListener {
    override def onPasswordExists(kindOfAccess: KindOfAccess): Unit = passwordExists = true
    override def onConfirmationCodeSent(kindOfAccess: KindOfAccess): Unit = confirmationCodeSent = true
    override def onConfirmationCodeSendingFailed(kindOfAccess: KindOfAccess, code: Int, message: String, label: String): Unit = {
      info(s"unable to request confirmation code: $kindOfAccess, $code, $message, $label")
      confirmationCodeSent = false
    }
  }

  var credentialsUpdated: Boolean = false
  lazy val credentialsListener = new CredentialsUpdateListener {
    override def onUpdated(): Unit = credentialsUpdated = true
    override def onUpdateFailed(code: Int, message: String, label: String): Unit = {
      info(s"unable to update credentials: $code, $message, $label")
      credentialsUpdated = false
    }
  }

  var loggedInSelf = Option.empty[Self]
  lazy val loginListener = new LoginListener {
    override def onSuccess(user: Self): Unit = loggedInSelf = Some(user)
    override def onFailed(code: Int, message: String, label: String): Unit = {
      info(s"unable to login: $code, $message, $label")
      loggedInSelf = None
    }
  }

  feature("Register by phone") {
    scenario("Request confirmation code") {
      api.requestPhoneConfirmationCode(phone.str, KindOfAccess.REGISTRATION, confirmationCodeListener)
      withDelay {
        confirmationCodeSent shouldEqual true
      }
    }

    scenario("Verify confirmation code") {
      confirmationCode = Await.result(internalBackendClient.getPhoneActivationCode(phone), 10.seconds).right.toOption

      var verified = false
      api.verifyPhoneNumber(phone.str, confirmationCode.value.str, KindOfVerification.PREVERIFY_ON_REGISTRATION, new PhoneNumberVerificationListener {
        override def onVerified(kindOfVerification: KindOfVerification): Unit = verified = true
        override def onVerificationFailed(kindOfVerification: KindOfVerification, code: Int, message: String, label: String): Unit = {
          info(s"unable to verify phone number: $kindOfVerification, $code, $message, $label")
          verified = false
        }
      })

      withDelay {
        verified shouldEqual true
      }
    }

    scenario("Register") {
      var self = Option.empty[Self]
      api.register(CredentialsFactory.phoneCredentials(phone.str, confirmationCode.value.str), "name", AccentColor(1), new RegistrationListener {
        override def onRegistered(user: Self): Unit = self = Some(user)
        override def onRegistrationFailed(code: Int, message: String, label: String): Unit = {
          info(s"unable to register: $code, $message, $label")
          self = None
        }
      })

      withDelay {
        self should be(defined)
        self.value.getName shouldEqual "name"
        self.value.getPhone shouldEqual phone.str
        self.value.isLoggedIn shouldEqual true
        self.value.accountActivated shouldEqual true
        self.value.getEmail should be(empty)
      }
    }

  }
  feature("Add email address and password afterwards") {
    scenario("Add a password") {
      val self = api.getSelf
      self.setPassword("initial", credentialsListener)
      withDelay {
        credentialsUpdated shouldEqual true
      }
    }

    scenario("Add an email address") {
      val self = api.getSelf
      credentialsUpdated = false
      self.setEmail(email, credentialsListener)
      withDelay {
        credentialsUpdated shouldEqual true
        self.getEmail should be (empty)
        self.getPhone shouldEqual phone.str
        self.accountActivated shouldEqual true
      }
    }

    scenario("Activate the added email address") {
      Await.result(internalBackendClient.activateEmail(EmailAddress(email)), 10.seconds) should be ('right)
      val self = api.getSelf
      withDelay {
        self.getEmail shouldEqual email
      }
    }

    scenario("Add picture") {
      val self = api.getSelf
      self.setPicture(ImageAssetFactory.getImageAsset(IoUtils.toByteArray(getClass.getResourceAsStream("/images/penguin.png"))))
      withDelay {
        self.getPicture.isEmpty shouldEqual false
        zmessaging.assetsStorage.get(AssetId(self.getPicture.getId)) should eventually(beMatching({
          case Some(AssetData.WithRemoteId(_)) => true
        }))
      }
    }

    scenario("Logout") {
      api.logout()
      withDelay {
        api.getSelf.isLoggedIn shouldEqual false
      }
    }
  }

  feature("Sign in by phone") {
    scenario("Request login confirmation code if no password") {
      confirmationCodeSent = false
      passwordExists = false
      api.requestPhoneConfirmationCode(phone.str, KindOfAccess.LOGIN_IF_NO_PASSWD, confirmationCodeListener)
      withDelay {
        passwordExists shouldEqual true
        confirmationCodeSent shouldEqual false
      }
    }

    scenario("Request login confirmation code") {
      confirmationCodeSent = false
      api.requestPhoneConfirmationCode(phone.str, KindOfAccess.LOGIN, confirmationCodeListener)
      withDelay {
        confirmationCodeSent shouldEqual true
      }
      confirmationCode = Await.result(internalBackendClient.getPhoneLoginCode(phone), 10.seconds).right.toOption
    }

    scenario("Login") {
      loggedInSelf = None
      api.login(CredentialsFactory.phoneCredentials(phone.str, confirmationCode.value.str), loginListener)

      lazy val self = loggedInSelf
      lazy val user = self.get.getUser

      withDelay {
        loggedInSelf shouldBe defined
        self should be (defined)
        self.value.isLoggedIn shouldEqual true
        self.value.getPhone shouldEqual phone.str
        self.value.getEmail shouldEqual email
        withClue(s"self data: ${self.value.asInstanceOf[com.waz.api.impl.Self].data}, user data: ${user.data}") {
          user.getEmail shouldEqual email
        }
        user.getPicture should not be empty
      }
    }

    scenario("Logout") {
      api.logout()
      withDelay {
        api.getSelf.isLoggedIn shouldEqual false
      }
    }

    scenario("Request login confirmation call") {
      confirmationCodeSent = false
      api.requestPhoneConfirmationCall(phone.str, KindOfAccess.LOGIN, confirmationCodeListener)
      withDelay {
        confirmationCodeSent shouldEqual true
      }
    }

    scenario("Request login confirmation call if no password") {
      confirmationCodeSent = false
      passwordExists = false
      api.requestPhoneConfirmationCall(phone.str, KindOfAccess.LOGIN_IF_NO_PASSWD, confirmationCodeListener)
      withDelay {
        passwordExists shouldEqual true
        confirmationCodeSent shouldEqual false
      }
    }
  }

  feature("Sign in with the email address that was added") {
    scenario("Login") {
      loggedInSelf = None
      api.login(CredentialsFactory.emailCredentials(email, "initial"), loginListener)
      withDelay {
        val self = loggedInSelf
        self should be (defined)
        self.value.isLoggedIn shouldEqual true
        self.value.getEmail shouldEqual email
        self.value.getPhone shouldEqual phone.str
      }
    }

    scenario("Logout") {
      api.logout()
      withDelay {
        api.getSelf.isLoggedIn shouldEqual false
      }
    }
  }
}
