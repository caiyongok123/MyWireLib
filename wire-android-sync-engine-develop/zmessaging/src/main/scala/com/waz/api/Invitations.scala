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
package com.waz.api

import android.net.Uri
import android.os.{Parcel, Parcelable}
import com.waz.model.PersonalInvitationToken
import com.waz.service.invitations.WebLink

object Invitations {

  trait GenericToken extends Parcelable
  object GenericToken {
    val CREATOR: Parcelable.Creator[GenericToken] = new Parcelable.Creator[GenericToken] {
      override def createFromParcel(source: Parcel): GenericToken = WebLink.Token.fromParcel(source)
      override def newArray(size: Int): Array[GenericToken] = Array.ofDim(size)
    }
  }

  trait PersonalToken extends Parcelable
  object PersonalToken {
    val CREATOR: Parcelable.Creator[PersonalToken] = new Parcelable.Creator[PersonalToken] {
      override def createFromParcel(source: Parcel): PersonalToken = PersonalInvitationToken.fromParcel(source)
      override def newArray(size: Int): Array[PersonalToken] = Array.ofDim(size)
    }
  }

  trait ConnectionCallback {
    def onConnectionRequested(conversation: IConversation): Unit
    def onRequestFailed(response: ErrorResponse): Unit
  }

  trait InvitationUriCallback {
    def onInvitationGenerated(invitationUri: Uri): Unit
    def onInvitationGenerationFailed(): Unit
  }

  trait InvitationDetailsCallback {
    def onEmailAdressRetrieved(nameOfInvitee: String, emailAddress: String): Unit
    def onPhoneNumberRetrieved(nameOfInvitee: String, phoneNumber: String): Unit
    def onRetrievalFailed(response: ErrorResponse): Unit
  }

  trait InvitationDetailsResponse
  case class EmailAddressResponse(nameOfInvitee: String, emailAddress: String) extends InvitationDetailsResponse
  case class PhoneNumberResponse(nameOfInvitee: String, emailAddress: String) extends InvitationDetailsResponse
  case class RetrievalFailed(response: ErrorResponse) extends InvitationDetailsResponse

}

trait Invitations {
  def generateInvitationUri(callback: Invitations.InvitationUriCallback): Unit
  def requestConnection(invitation: Invitations.GenericToken, message: String, callback: Invitations.ConnectionCallback): Unit
  def retrieveInvitationDetails(invitation: Invitations.PersonalToken, callback: Invitations.InvitationDetailsCallback): Unit
}
