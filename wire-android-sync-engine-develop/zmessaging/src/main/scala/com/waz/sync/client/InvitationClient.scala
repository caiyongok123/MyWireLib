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

import com.waz.api.impl.ErrorResponse
import com.waz.model._
import com.waz.threading.CancellableFuture.successful
import com.waz.threading.Threading
import com.waz.utils.JsonEncoder
import com.waz.utils.Locales.{bcp47, currentLocale}
import com.waz.znet.ContentEncoder.json
import com.waz.znet.Response.HttpStatus
import com.waz.znet.Response.Status.{Created, SeeOther}
import com.waz.znet.ZNetClient.ErrorOrResponse
import com.waz.znet._
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.util.Try

class InvitationClient(netClient: ZNetClient) {
  import Threading.Implicits.Background
  import com.waz.sync.client.InvitationClient._

  def postInvitation(i: Invitation): ErrorOrResponse[Either[UserId, ConfirmedInvitation]] =
    netClient.chainedWithErrorHandling("postInvitation", Request.Post(InvitationPath.toString, i)(json(EncodeInvite))) {
      case Response(HttpStatus(Created, _), ConfirmedInvitation(inv), _) =>
        successful(Right(Right(inv)))
      case Response(HttpStatus(Created, _), EmptyResponse, headers) =>
        successful(locationFrom(headers).map(Left(_)).toRight(ErrorResponse(Created, "invitee already registered but no UserId in headers", "")))
      case Response(HttpStatus(SeeOther, _), EmptyResponse, headers) =>
        successful(locationFrom(headers).map(Left(_)).toRight(ErrorResponse(SeeOther, "invitee already registered and connected but no UserId in headers", "")))
    }

  private def locationFrom(headers: Response.Headers) = headers("location") collect { case RedirectToUser(id) => UserId(id) }

  def postTeamInvitation(invitation: TeamInvitation): ErrorOrResponse[ConfirmedTeamInvitation] =
    netClient.chainedWithErrorHandling("postTeamInvitation", Request.Post(teamInvitationPath(invitation.teamId), invitation)(json(EncodeTeamInvite))) {
      case Response(HttpStatus(Created, _), ConfirmedTeamInvitation(inv), _) =>
        successful(Right(inv))
    }
}

object InvitationClient {
  val InvitationPath = "/invitations"

  def teamInvitationPath(teamId: TeamId) = s"teams/$teamId/invitations"

  val RedirectToUser = "(?:/self)?/connections/([^/]+)".r

  implicit lazy val EncodeInvite: JsonEncoder[Invitation] = new JsonEncoder[Invitation] {
    def apply(i: Invitation): JSONObject = JsonEncoder { js =>
      i.method match {
        case Left(e) => js.put("email", e)
        case Right(p) => js.put("phone", p)
      }
      js.put("invitee_name", i.nameOfInvitee)
      js.put("inviter_name", i.nameOfInviter)
      js.put("message", i.message)
      js.put("locale", bcp47.languageTagOf(i.locale.getOrElse(currentLocale)))
    }
  }

  implicit lazy val EncodeTeamInvite: JsonEncoder[TeamInvitation] = new JsonEncoder[TeamInvitation] {
    def apply(i: TeamInvitation): JSONObject = JsonEncoder { js =>
      js.put("email", i.emailAddress)
      js.put("inviter_name", i.inviterName)
      js.put("locale", bcp47.languageTagOf(i.locale.getOrElse(currentLocale)))
    }
  }

  case class ConfirmedInvitation(id: InvitationId, nameOfInvitee: String, method: Either[EmailAddress, PhoneNumber], genesis: Instant)
  object ConfirmedInvitation {
    def unapply(resp: ResponseContent): Option[ConfirmedInvitation] = resp match {
      case JsonObjectResponse(js) =>
        Try(ConfirmedInvitation(
          InvitationId(js.getString("id")),
          js.getString("name"),
          if (js.has("email")) Left(EmailAddress(js.getString("email")))
          else Right(PhoneNumber(js.getString("phone"))),
          Instant.parse(js.getString("created_at"))
        )).toOption
      case _ => None
    }
  }

  case class ConfirmedTeamInvitation(id: InvitationId, emailAddress: EmailAddress, createdAt: Instant, teamId:TeamId)
  object ConfirmedTeamInvitation {
    import com.waz.utils.JsonDecoder._
    def unapply(resp: ResponseContent): Option[ConfirmedTeamInvitation] = resp match {
      case JsonObjectResponse(js) =>
        implicit val jObject: JSONObject = js
        Try(ConfirmedTeamInvitation('id, 'email, Instant.parse(js.getString("created_at")), 'team)).toOption
      case _ => None
    }
  }
}
