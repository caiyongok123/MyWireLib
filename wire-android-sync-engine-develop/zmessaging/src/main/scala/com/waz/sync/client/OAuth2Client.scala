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
package com.waz.sync.client

import java.net.URLEncoder

import android.net.Uri
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.impl.ErrorResponse
import com.waz.sync.client.OAuth2Client._
import com.waz.threading.Threading
import com.waz.utils.JsonDecoder
import com.waz.znet.AuthenticationManager.Token
import com.waz.znet.ContentEncoder.{BinaryRequestContent, RequestContent}
import com.waz.znet.Response.SuccessHttpStatus
import com.waz.znet.ZNetClient.ErrorOr
import com.waz.znet.{JsonObjectResponse, _}
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.concurrent.Future

class OAuth2Client(netClient: ZNetClient)(implicit app: AppInfo) {
  import Threading.Implicits.Background

  @volatile private var accessToken = Option.empty[AccessToken] // cache this here so that we can automatically refresh it whenever necessary

  def clearAccessToken(): Unit = accessToken = None

  def getTokensFromCode(code: AuthorizationCode): ErrorOr[RefreshToken] = {
    netClient(Request.Post(app.tokenPath, code)).future.map {
      case Response(SuccessHttpStatus(), TokenResponse((access, refresh)), _) =>
        accessToken = Some(access)
        Right(refresh)
      case other => Left(ErrorResponse.internalError(s"unexpected response when retrieving Spotify tokens: $other"))
    }
  }

  def bearerHeader(accessToken: AccessToken): Map[String, String] = Map(Token.AuthorizationHeader -> s"Bearer ${accessToken.token}")

  def withFreshToken[A](refresh: Option[RefreshToken])(f: AccessToken => ErrorOr[A]): ErrorOr[A] = freshAccessToken(refresh) flatMap {
    case Right(token) => f(token)
    case Left(e) => Future.successful(Left(e))
  }

  def freshAccessToken(refresh: Option[RefreshToken]): ErrorOr[AccessToken] = (accessToken, refresh) match {
    case (Some(access), _) if access.isValid => Future.successful(Right(access))
    case (_, Some(refreshToken)) => requestAndCacheNewAccessToken(refreshToken) // no access token, or access token invalid, refresh it ("Authorization Code Flow")
    case (_, None) => requestAndCacheNewAccessToken(ClientCredentials)          // no valid access token, no refresh token, retrieve a new one ("Client Credentials Flow")
  }

  private def requestAndCacheNewAccessToken[A : ContentEncoder](data: A): ErrorOr[AccessToken] =
    netClient(Request.Post(app.tokenPath, data)).future map {
      case Response(SuccessHttpStatus(), AccessTokenResponse(newAccessToken), _) =>
        accessToken = Some(newAccessToken)
        Right(newAccessToken)
      case other =>
        Left(ErrorResponse(other.status.status, s"Unexpected response when requesting an access token: $other", "unexpected-spotify-response"))
    }
}

object OAuth2Client {
  import JsonDecoder._

  case class AppInfo(id: ClientId, tokenPath: String, redirectUri: Uri)
  case class ClientId(str: String) extends AnyVal

  case class AccessToken(token: String, expires: Instant) {
    def isValid: Boolean = Instant.now() isBefore expires
  }

  case object ClientCredentials
  case class RefreshToken(str: String) extends AnyVal
  case class AuthorizationCode(str: String) extends AnyVal

  implicit def AuthorizationCodeEncoder(implicit app: AppInfo): ContentEncoder[AuthorizationCode] = new ContentEncoder[AuthorizationCode] {
    override def apply(code: AuthorizationCode): RequestContent =
      FormUrlEncoder(Map("grant_type" -> "authorization_code", "code" -> code.str, "redirect_uri" -> app.redirectUri.toString))
  }

  implicit lazy val RefreshTokenRequestEncoder: ContentEncoder[RefreshToken] = new ContentEncoder[RefreshToken] {
    override def apply(token: RefreshToken): RequestContent = FormUrlEncoder(Map("grant_type" -> "refresh_token", "refresh_token" -> token.str))
  }

  implicit lazy val ClientCredentialsEncoder: ContentEncoder[ClientCredentials.type] = new ContentEncoder[ClientCredentials.type] {
    override def apply(data: ClientCredentials.type): RequestContent = FormUrlEncoder(Map("grant_type" -> "client_credentials"))
  }

  implicit lazy val FormUrlEncoder: ContentEncoder[Map[String, String]] = new ContentEncoder[Map[String, String]] {
    override def apply(values: Map[String, String]): RequestContent = new BinaryRequestContent((values map { case (k, v) =>
      URLEncoder.encode(k, "utf-8") + "=" + URLEncoder.encode(v, "utf-8")
    } mkString "&") getBytes "utf-8", "application/x-www-form-urlencoded")
  }

  object TokenResponse extends JsonObjectResponse[(AccessToken, RefreshToken)] {
    override def fromJson(implicit js: JSONObject): Option[(AccessToken, RefreshToken)] = AccessTokenResponse.fromJson map { (_, RefreshToken('refresh_token)) }
  }

  object AccessTokenResponse extends JsonObjectResponse[AccessToken] {
    override def fromJson(implicit js: JSONObject): Option[AccessToken] = Some(AccessToken(token = 'access_token, expires = Instant.now().plusSeconds(decodeLong('expires_in) - 30L)))
  }

  trait JsonObjectResponse[A] {
    def fromJson(implicit js: JSONObject): Option[A]

    def unapply(resp: ResponseContent): Option[A] = resp match {
      case JsonObjectResponse(js) => fromJson(js)
      case other =>
        warn(s"unknown response content: $resp")
        None
    }
  }
}
