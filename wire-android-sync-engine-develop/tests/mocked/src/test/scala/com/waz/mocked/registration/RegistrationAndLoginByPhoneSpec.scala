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
package com.waz.mocked.registration

import com.waz.api.ZMessagingApi.{RegistrationListener, PhoneNumberVerificationListener, PhoneConfirmationCodeRequestListener}
import com.waz.api.impl.{PhoneCredentials, AccentColors}
import com.waz.api._
import com.waz.mocked.MockBackend
import com.waz.model._
import com.waz.znet.ZNetClient.ErrorOrResponse
import org.scalatest.FeatureSpec

import scala.concurrent.duration._

class RegistrationAndLoginByPhoneSpec extends FeatureSpec with MockedClientApiSpec with MockBackend {
  import DefaultPushBehaviour.Implicit

  override val autoLogin = false
  lazy val self = api.getSelf

  var requestPhoneNumber = Option.empty[PhoneNumber]

  object PhoneVerificationSpy extends PhoneConfirmationCodeRequestListener {
    var confirmationSent = Option.empty[KindOfAccess]
    override def onConfirmationCodeSendingFailed(kindOfAccess: KindOfAccess, code: Int, message: String, label: String): Unit = ()
    override def onConfirmationCodeSent(kindOfAccess: KindOfAccess): Unit = confirmationSent = Some(kindOfAccess)
    override def onPasswordExists(kindOfAccess: KindOfAccess): Unit = ()
  }

  override def requestPhoneConfirmationCode(phone : PhoneNumber, kindOfAccess : KindOfAccess) = {
    requestPhoneNumber = Some(phone)
    super.requestPhoneConfirmationCode(phone, kindOfAccess)
  }

  override def verifyPhoneNumber(credentials: PhoneCredentials, kindOfVerification: KindOfVerification): ErrorOrResponse[Unit] = {
    requestPhoneNumber = Some(credentials.phone)
    super.verifyPhoneNumber(credentials, kindOfVerification)
  }

  feature("Registration by phone number") {
    scenario("Send confirmation code to phone number") {
      api.requestPhoneConfirmationCode("+491234567890", KindOfAccess.REGISTRATION, PhoneVerificationSpy)
      withDelay { PhoneVerificationSpy.confirmationSent shouldEqual Some(KindOfAccess.REGISTRATION) }
    }

    scenario("Normalize phone number when sending confirmation code") {
      api.requestPhoneConfirmationCode("+4901234567890", KindOfAccess.REGISTRATION, PhoneVerificationSpy)
      withDelay { requestPhoneNumber shouldEqual Some(PhoneNumber("+491234567890"))}
    }

    scenario("Sending confirmation code falls back to non-normalized phone when normalization fails") {
      api.requestPhoneConfirmationCode("+01234567890", KindOfAccess.REGISTRATION, PhoneVerificationSpy)
      withDelay { requestPhoneNumber shouldEqual Some(PhoneNumber("+01234567890"))}
    }

    scenario("Verify confirmation code") {
      @volatile var verified = false
      api.verifyPhoneNumber("+491234567890", "123456", KindOfVerification.PREVERIFY_ON_REGISTRATION, new PhoneNumberVerificationListener {
        override def onVerified(kindOfVerification: KindOfVerification): Unit = verified = true
        override def onVerificationFailed(kindOfVerification: KindOfVerification, code: Int, message: String, label: String): Unit = ()
      })
      withDelay { verified shouldEqual true }
    }

    scenario("Normalize phone number when verifying code") {
      @volatile var verified = false
      api.verifyPhoneNumber("+4901234567890", "123456", KindOfVerification.PREVERIFY_ON_REGISTRATION, new PhoneNumberVerificationListener {
        override def onVerified(kindOfVerification: KindOfVerification): Unit = verified = true
        override def onVerificationFailed(kindOfVerification: KindOfVerification, code: Int, message: String, label: String): Unit = ()
      })
      withDelay { requestPhoneNumber shouldEqual Some(PhoneNumber("+491234567890"))}
    }

    scenario("Verifying phone number falls back to non-normalized phone when normalization fails") {
      @volatile var verified = false
      api.verifyPhoneNumber("+01234567890", "123456", KindOfVerification.PREVERIFY_ON_REGISTRATION, new PhoneNumberVerificationListener {
        override def onVerified(kindOfVerification: KindOfVerification): Unit = verified = true
        override def onVerificationFailed(kindOfVerification: KindOfVerification, code: Int, message: String, label: String): Unit = ()
      })
      withDelay { requestPhoneNumber shouldEqual Some(PhoneNumber("+01234567890"))}
    }

    scenario("Register user") {
      @volatile var registered = false
      api.register(CredentialsFactory.phoneCredentials("+491234567890", "123456"), "name", AccentColors.defaultColor, new RegistrationListener {
        override def onRegistrationFailed(code: Int, message: String, label: String): Unit = ()
        override def onRegistered(user: Self): Unit = registered = true
      })
      withDelay {
        registered shouldEqual true
        self.isLoggedIn shouldEqual true
        self.getName shouldEqual "name"
        self.getUser.getPhone shouldEqual "+491234567890"
        self.getUser.getEmail shouldEqual ""
      }
    }

    scenario("Log out") {
      api.logout()
      withDelay { self.isLoggedIn shouldEqual false }
    }
  }

  feature("Login by phone number") {
    scenario("Send confirmation code to phone number") {
      api.requestPhoneConfirmationCode("+491234567890", KindOfAccess.LOGIN, PhoneVerificationSpy)
      withDelay { PhoneVerificationSpy.confirmationSent shouldEqual Some(KindOfAccess.LOGIN) }
    }

    scenario("Normalize phone number when sending confirmation code") {
      api.requestPhoneConfirmationCode("+4901234567890", KindOfAccess.LOGIN, PhoneVerificationSpy)
      withDelay { requestPhoneNumber shouldEqual Some(PhoneNumber("+491234567890")) }
    }

    scenario("Login") {
      @volatile var loggedIn = false
      api.login(CredentialsFactory.phoneCredentials("+491234567890", "123456"), new LoginListener {
        override def onSuccess(user: Self): Unit = loggedIn = true
        override def onFailed(code: Int, message: String, label: String): Unit = ()
      })

      withDelay {
        loggedIn shouldEqual true
        self.isLoggedIn shouldEqual true
      }
    }

    scenario("Add password to account") {
      @volatile var updated = false
      self.setPassword("meep", new CredentialsUpdateListener {
        override def onUpdateFailed(code: Int, message: String, label: String): Unit = ()
        override def onUpdated(): Unit = updated = true
      })

      withDelay { updated shouldEqual true }
      awaitUi(2.seconds) // this test "spills" into the next otherwise
    }

    scenario("Add email to account") {
      @volatile var updated = false
      self.setEmail("meep@moop.org", new CredentialsUpdateListener {
        override def onUpdateFailed(code: Int, message: String, label: String): Unit = ()
        override def onUpdated(): Unit = updated = true
      })
      withDelay {
        updated shouldEqual true
        self.getEmail shouldEqual ""
        self.accountActivated shouldEqual true
      }

      addNotification(UserUpdateEvent(UserInfo(UserId(self.getUser.getId), email = Some(EmailAddress("meep@moop.org")), phone = None)))
      withDelay {
        self.getEmail shouldEqual "meep@moop.org"
        self.accountActivated shouldEqual true
      }
    }
  }
}
