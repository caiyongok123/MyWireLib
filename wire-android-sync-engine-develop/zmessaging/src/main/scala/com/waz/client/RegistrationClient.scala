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
package com.waz.client

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.impl.{ErrorResponse, PhoneCredentials}
import com.waz.api.{KindOfAccess, KindOfVerification}
import com.waz.client.RegistrationClientImpl.ActivateResult
import com.waz.model._
import com.waz.service.BackendConfig
import com.waz.sync.client.InvitationClient.ConfirmedInvitation
import com.waz.sync.client.UsersClient.UserResponseExtractor
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.JsonEncoder
import com.waz.utils.Locales._
import com.waz.utils.wrappers.URI
import com.waz.znet.AuthenticationManager.Cookie
import com.waz.znet.ContentEncoder.JsonContentEncoder
import com.waz.znet.Response.Status._
import com.waz.znet.Response.{HttpStatus, Status, SuccessHttpStatus}
import com.waz.znet.ZNetClient.ErrorOrResponse
import com.waz.znet._
import org.json.JSONObject

import scala.concurrent.duration._

trait RegistrationClient {
  def register(account: AccountData, name: String, accentId: Option[Int]): ErrorOrResponse[(UserInfo, Option[Cookie])]
  def registerTeamAccount(account: AccountData): ErrorOrResponse[(UserInfo, Option[Cookie])]

  def verifyPhoneNumber(credentials: PhoneCredentials, kindOfVerification: KindOfVerification): ErrorOrResponse[Unit]
  def verifyEmail(email: EmailAddress, code: ConfirmationCode): ErrorOrResponse[Unit]
  def requestPhoneConfirmationCode(phone: PhoneNumber, kindOfAccess: KindOfAccess): CancellableFuture[ActivateResult]
  def requestEmailConfirmationCode(email: EmailAddress): CancellableFuture[ActivateResult]
  def requestPhoneConfirmationCall(phone: PhoneNumber, kindOfAccess: KindOfAccess): CancellableFuture[ActivateResult]
  def getInvitationDetails(token: PersonalInvitationToken): ErrorOrResponse[ConfirmedInvitation]
}

class RegistrationClientImpl(client: AsyncClient, backend: BackendConfig) extends RegistrationClient {
  import Threading.Implicits.Background
  import com.waz.client.RegistrationClientImpl._

  def register(account: AccountData, name: String, accentId: Option[Int]): ErrorOrResponse[(UserInfo, Option[Cookie])] = {
    val json = JsonEncoder { o =>
      o.put("name", name)
      accentId foreach (o.put("accent_id", _))
      o.put("locale", bcp47.languageTagOf(currentLocale))
      account.addToRegistrationJson(o)
    }

    val request = Request.Post(RegisterPath, JsonContentEncoder(json), baseUri = Some(backend.baseUrl), timeout = timeout)
    client(request) map {
      case resp @ Response(SuccessHttpStatus(), UserResponseExtractor(user), headers) =>
        debug(s"registration succeeded: $resp")
        Right((user, if (account.autoLoginOnRegistration) LoginClient.getCookieFromHeaders(headers) else None))
      case Response(_, ErrorResponse(code, msg, label), headers) =>
        info(s"register failed with error: ($code, $msg, $label), headers: $headers")
        Left(ErrorResponse(code, msg, label))
      case resp =>
        error(s"Unexpected response from register: $resp")
        Left(ErrorResponse(resp.status.status, resp.toString, "unknown"))
    }
  }

  //TODO merge this method with the above register method when safe to do so
  override def registerTeamAccount(account: AccountData) = {
    import account._
    (name, pendingTeamName, pendingEmail, password, code) match {
      case (Some(n), Some(teamName), Some(e), Some(p), Some(c)) =>
        val json = JsonEncoder { o =>
          o.put("name", n)
          o.put("locale", bcp47.languageTagOf(currentLocale))
          o.put("label", account.id.str)  // this label can be later used for cookie revocation
          o.put("email", e.str)
          o.put("password", p)
          o.put("email_code", c.str)
          o.put("team", JsonEncoder { o2 =>
            o2.put("icon", "abc")
            o2.put("name", teamName)
          })
        }

        val request = Request.Post(RegisterPath, JsonContentEncoder(json), baseUri = Some(backend.baseUrl), timeout = timeout)
        client(request) map {
          case resp @ Response(SuccessHttpStatus(), UserResponseExtractor(user), headers) =>
            debug(s"registration succeeded: $resp")
            Right((user, LoginClient.getCookieFromHeaders(headers)))
          case Response(_, ErrorResponse(code, msg, label), headers) =>
            info(s"register failed with error: ($code, $msg, $label), headers: $headers")
            Left(ErrorResponse(code, msg, label))
          case resp =>
            error(s"Unexpected response from register: $resp")
            Left(ErrorResponse(resp.status.status, resp.toString, "unknown"))
        }
      case _ => CancellableFuture.successful(Left(ErrorResponse(
        ErrorResponse.InternalErrorCode,
        "Email, name, password, confirmation code and a team name are needed to complete account registration for a team",
        "insufficient team account information")))
    }
  }

  def requestPhoneConfirmationCode(phone: PhoneNumber, kindOfAccess: KindOfAccess): CancellableFuture[ActivateResult] =
    postActivateSend(kindOfAccess) {
      JsonEncoder { o =>
        o.put("phone", phone.str)
        if (kindOfAccess == KindOfAccess.LOGIN_IF_NO_PASSWD) o.put("force", false)
        if (kindOfAccess == KindOfAccess.REGISTRATION) o.put("locale", bcp47.languageTagOf(currentLocale))
      }
    }

  //TODO combine with other code methods when safe to do so
  def requestEmailConfirmationCode(email: EmailAddress): CancellableFuture[ActivateResult] =
    postActivateSend(KindOfAccess.REGISTRATION) {
      JsonEncoder { o =>
        o.put("email", email.str)
        o.put("locale", bcp47.languageTagOf(currentLocale))
      }
    }

  def requestPhoneConfirmationCall(phone: PhoneNumber, kindOfAccess: KindOfAccess): CancellableFuture[ActivateResult] =
    postActivateSend(kindOfAccess) {
      JsonEncoder { o =>
        o.put("phone", phone.str)
        o.put("voice_call", true)
        if (kindOfAccess == KindOfAccess.LOGIN_IF_NO_PASSWD) o.put("force", false)
        if (kindOfAccess == KindOfAccess.REGISTRATION) o.put("locale", bcp47.languageTagOf(currentLocale))
      }
    }

  private def postActivateSend(kindOfAccess: KindOfAccess)(params: JSONObject): CancellableFuture[ActivateResult] = {
    val uri = kindOfAccess match {
      case KindOfAccess.LOGIN_IF_NO_PASSWD | KindOfAccess.LOGIN => LoginSendPath
      case KindOfAccess.REGISTRATION => ActivateSendPath
    }

    val request = Request.Post(uri, JsonContentEncoder(params), baseUri = Some(backend.baseUrl), timeout = timeout)
    client(request) map {
      case resp @ Response(SuccessHttpStatus(), _, _) =>
        debug(s"confirmation code requested: $resp")
        ActivateResult.Success
      case Response(_, ErrorResponse(Status.Forbidden, _, "password-exists"), headers) =>
        ActivateResult.PasswordExists
      case Response(_, ErrorResponse(code, msg, label), headers) =>
        warn(s"requestPhoneNumberConfirmation($kindOfAccess) failed with error: ($code, $msg, $label), headers: $headers")
        ActivateResult.Failure(ErrorResponse(code, msg, label))
      case other =>
        error(s"Unexpected response from requestPhoneNumberConfirmation($kindOfAccess): $other")
        ActivateResult.Failure(ErrorResponse(other.status.status, other.toString, "unknown"))
    }
  }

  override def verifyPhoneNumber(credentials: PhoneCredentials, kindOfVerification: KindOfVerification): ErrorOrResponse[Unit] = {
    val request = Request.Post(ActivatePath, activateRequestBody(credentials, kindOfVerification), baseUri = Some(backend.baseUrl), timeout = timeout)
    client(request) map {
      case resp @ Response(SuccessHttpStatus(), _, _) =>
        debug(s"phone number verified: $resp")
        Right(())
      case Response(_, ErrorResponse(code, msg, label), headers) =>
        warn(s"verifyPhoneNumber failed with error: ($code, $msg, $label), headers: $headers")
        Left(ErrorResponse(code, msg, label))
      case other =>
        error(s"Unexpected response from verifyPhoneNumber: $other")
        Left(ErrorResponse(other.status.status, other.toString, "unknown"))
    }
  }

  //TODO merge with phone verification to avoid duplication
  override def verifyEmail(email: EmailAddress, code: ConfirmationCode) = {

    val body = JsonContentEncoder(JsonEncoder { o =>
      o.put("email", email.str)
      o.put("code", code.str)
      o.put("dryrun", true)
    })

    val request = Request.Post(ActivatePath, body, baseUri = Some(backend.baseUrl), timeout = timeout)
    client(request) map {
      case resp @ Response(SuccessHttpStatus(), _, _) =>
        debug(s"Email number verified: $resp")
        Right(())
      case Response(_, ErrorResponse(code, msg, label), headers) =>
        warn(s"verifyEmail failed with error: ($code, $msg, $label), headers: $headers")
        Left(ErrorResponse(code, msg, label))
      case other =>
        error(s"Unexpected response from verifyEmail: $other")
        Left(ErrorResponse(other.status.status, other.toString, "unknown"))
    }
  }

  import com.waz.sync.client.InvitationClient._

  def getInvitationDetails(token: PersonalInvitationToken): ErrorOrResponse[ConfirmedInvitation] = {
    val path = infoPath(token)
    val request = Request.Get(path.toString, baseUri = Some(backend.baseUrl))
    client(request) map {
      case Response(HttpStatus(Success, _), ConfirmedInvitation(inv), _) =>
        debug(s"received invitation details for $token: $inv")
        Right(inv)
      case Response(HttpStatus(BadRequest, "invalid-invitation-code"), EmptyResponse, _) =>
        warn(s"invitation token not found: $token")
        Left(ErrorResponse(NotFound, "no such invitation code", "invalid-invitation-code"))
      case other =>
        ZNetClient.errorHandling("getInvitationInfo")("RegistrationClient")(other)
    }
  }

  private def infoPath(token: PersonalInvitationToken): URI = URI.parse(InvitationPath).buildUpon.appendPath("info").appendQueryParameter("code", token.code).build
}

object RegistrationClientImpl {
  val RegisterPath = "/register"
  val ActivatePath = "/activate"
  val ActivateSendPath = "/activate/send"
  val LoginSendPath = "/login/send"

  val timeout = 15.seconds

  sealed trait ActivateResult
  object ActivateResult {
    case object Success extends ActivateResult
    case object PasswordExists extends ActivateResult
    case class Failure(err: ErrorResponse) extends ActivateResult
  }


  def activateRequestBody(credentials: PhoneCredentials, kindOfVerification: KindOfVerification) = JsonContentEncoder(JsonEncoder { o =>
    o.put("phone", credentials.phone.str)
    credentials.code foreach { code => o.put("code", code.str) }
    o.put("dryrun", kindOfVerification == KindOfVerification.PREVERIFY_ON_REGISTRATION)
  })
}
