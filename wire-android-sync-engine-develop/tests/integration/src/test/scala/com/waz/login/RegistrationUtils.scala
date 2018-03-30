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
package com.waz.login

import com.waz.api.ZMessagingApi.{PhoneNumberVerificationListener, PhoneConfirmationCodeRequestListener}
import com.waz.api._
import com.waz.model.{ConfirmationCode, PhoneNumber}
import com.waz.provision.InternalBackendClient

import scala.concurrent.Promise
import scala.util.{Failure, Success, Random}

trait RegistrationUtils { self: ApiSpec =>
  import com.waz.threading.Threading.Implicits.Background
  lazy val internalBackendClient = new InternalBackendClient(globalModule.client, testBackend)

  def randomPhoneNumber: PhoneNumber = PhoneNumber("+0" + (Random.nextInt(9) + 1) + Array.fill(13)(Random.nextInt(10)).mkString)

  def verifyPhoneNumber(phone: PhoneNumber, code: ConfirmationCode, kind: KindOfVerification = KindOfVerification.PREVERIFY_ON_REGISTRATION) = {
    val promise = Promise[Boolean]()
    api.verifyPhoneNumber(phone.str, code.str, kind, new PhoneNumberVerificationListener {
      override def onVerified(kindOfVerification: KindOfVerification): Unit = promise.success(true)
      override def onVerificationFailed(kindOfVerification: KindOfVerification, code: Int, message: String, label: String): Unit =
        promise.failure(new Exception(s"unable to verify phone number: $kindOfVerification, $code, $message, $label"))
    })
    promise.future
  }

  def fetchPhoneConfirmationCode(phone: PhoneNumber, kindOfAccess: KindOfAccess) = {
    val result = Promise[Option[ConfirmationCode]]()
    api.requestPhoneConfirmationCode(phone.str, kindOfAccess, new PhoneConfirmationCodeRequestListener {
      override def onConfirmationCodeSendingFailed(kindOfAccess: KindOfAccess, code: Int, message: String, label: String): Unit =
        result.failure(new Exception(s"request failed: $code, $message, $label"))

      override def onConfirmationCodeSent(kindOfAccess: KindOfAccess): Unit =
        {
          if (kindOfAccess == KindOfAccess.REGISTRATION) internalBackendClient.getPhoneActivationCode(phone)
          else internalBackendClient.getPhoneLoginCode(phone)
        } onComplete {
          case Success(Right(code)) => result.success(Some(code))
          case Success(Left(error)) => result.failure(new Exception(s"Could not fetch confirmation code: $error"))
          case Failure(ex) => result.failure(ex)
        }

      override def onPasswordExists(kindOfAccess: KindOfAccess): Unit = result.success(None)
    })
    result.future
  }

  def login(credentials: Credentials) = {
    val promise = Promise[Self]()
    api.login(credentials, new LoginListener {
      override def onSuccess(user: Self): Unit = promise.success(user)
      override def onFailed(code: Int, message: String, label: String): Unit =
        promise.failure(new Exception(s"login failed: $code, $message, $label"))
    })
    promise.future
  }

}
