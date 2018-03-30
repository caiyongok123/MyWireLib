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
package com.waz.api.impl

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.Invitations._
import com.waz.client.RegistrationClient
import com.waz.model.PersonalInvitationToken
import com.waz.service.invitations.WebLink
import com.waz.sync.client.InvitationClient.ConfirmedInvitation
import com.waz.threading.Threading
import com.waz.ui.{Conversations, ZMessagingResolver}
import com.waz.utils._

import scala.util.{Failure, Success}

class Invitations(zms: ZMessagingResolver, convs: => Conversations, regClient: => RegistrationClient) extends com.waz.api.Invitations {

  override def generateInvitationUri(callback: InvitationUriCallback): Unit = zms.flatMapFuture(_.invitations.generateInvitationUri())(Threading.Background).map {
    case Some(uri) => callback.onInvitationGenerated(uri)
    case _ => callback.onInvitationGenerationFailed()
  }(Threading.Ui)

  override def requestConnection(invitation: GenericToken, message: String, callback: ConnectionCallback): Unit = invitation match {
    case token: WebLink.Token =>
      zms.flatMapFuture(_.invitations.connectToInvitingUser(token, message))(Threading.Background).map {
        case Some(conv) => callback.onConnectionRequested(convs.getConversation(conv))
        case None => callback.onRequestFailed(ErrorResponse(601, "Invite expired", "expired"))
      }(Threading.Ui).recoverWithLog()
  }

  override def retrieveInvitationDetails(invitation: PersonalToken, callback: InvitationDetailsCallback): Unit = invitation match {
    case token: PersonalInvitationToken =>
      regClient.getInvitationDetails(token).onComplete {
        case Success(Right(ConfirmedInvitation(_, name, Left(email), _))) =>
          callback.onEmailAdressRetrieved(name, email.str)
        case Success(Right(ConfirmedInvitation(_, name, Right(phone), _))) =>
          callback.onPhoneNumberRetrieved(name, phone.str)
        case Success(Left(r)) =>
          callback.onRetrievalFailed(r)
        case Failure(exc) =>
          error(s"retrieval of invitation details failed", exc)
          callback.onRetrievalFailed(ErrorResponse.internalError(exc.getMessage))
      }(Threading.Ui)
  }
}
