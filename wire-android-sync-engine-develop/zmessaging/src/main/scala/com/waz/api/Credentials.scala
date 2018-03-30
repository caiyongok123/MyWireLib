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

import com.waz.model.{ConfirmationCode, PhoneNumber, EmailAddress}
import org.json.JSONObject
import com.waz.model.PersonalInvitationToken
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._

object CredentialsFactory {
  def emailCredentials(email: String, password: String): Credentials = impl.EmailCredentials(EmailAddress(email), Option(password))
  def phoneCredentials(phone: String, confirmationCode: String): Credentials = {
    assert(Option(confirmationCode).exists(_.nonEmpty), "Empty/missing phone confirmation code. Please always pass a phone confirmation code here.")
    impl.PhoneCredentials(PhoneNumber(phone), Some(ConfirmationCode(confirmationCode)))
  }
  def emailInvitationCredentials(email: String, password: String, invitation: Invitations.PersonalToken): Credentials =
    impl.EmailCredentials(EmailAddress(email), Option(password), token(invitation))
  def phoneInvitationCredentials(phone: String, invitation: Invitations.PersonalToken): Credentials =
    impl.PhoneCredentials(PhoneNumber(phone), None, token(invitation))

  private def token(invitation: Invitations.PersonalToken) = invitation match {
    case t: PersonalInvitationToken =>
      Some(t)
    case _ =>
      error("Use InvitationTokenFactory to create tokens.")
      None
  }
}

sealed trait Credentials

package impl {

  import com.waz.model.Handle

  sealed trait Credentials extends com.waz.api.Credentials {
    def canLogin: Boolean
    def autoLoginOnRegistration: Boolean
    def addToRegistrationJson(o: JSONObject): Unit
    def addToLoginJson(o: JSONObject): Unit

    def maybeEmail: Option[EmailAddress]
    def maybePhone: Option[PhoneNumber]
    def maybePassword: Option[String]
    def maybeUsername: Option[Handle]
  }

  object Credentials {
    val Empty = new Credentials {
      override def canLogin: Boolean = false
      override def maybeEmail: Option[EmailAddress] = None
      override def addToLoginJson(o: JSONObject): Unit = ()
      override def maybePhone: Option[PhoneNumber] = None
      override def maybePassword: Option[String] = None
      override def autoLoginOnRegistration: Boolean = false
      override def addToRegistrationJson(o: JSONObject): Unit = ()
      override def maybeUsername: Option[Handle] = None
    }
  }

  case class EmailCredentials(email: EmailAddress, password: Option[String], invitation: Option[PersonalInvitationToken] = None) extends Credentials {
    override def canLogin: Boolean = password.isDefined

    def autoLoginOnRegistration: Boolean = invitation.isDefined

    override def addToRegistrationJson(o: JSONObject): Unit = {
      o.put("email", email.str)
      password foreach (o.put("password", _))
      invitation foreach (i => o.put("invitation_code", i.code))
    }

    override def addToLoginJson(o: JSONObject): Unit = addToRegistrationJson(o)

    override def maybeEmail: Option[EmailAddress] = Some(email)
    override def maybePhone: Option[PhoneNumber] = None
    override def maybePassword: Option[String] = password
    override def maybeUsername: Option[Handle] = None

    override def toString: String = s"EmailBasedCredentials($email, ${password map (_.map(_ => '*'))})"
  }

  case class PhoneCredentials(phone: PhoneNumber, code: Option[ConfirmationCode], invitation: Option[PersonalInvitationToken] = None) extends Credentials {
    override def canLogin: Boolean = false

    def autoLoginOnRegistration: Boolean = true

    override def addToRegistrationJson(o: JSONObject): Unit = addToJson(o, "phone_code")
    override def addToLoginJson(o: JSONObject): Unit = addToJson(o, "code")

    private def addToJson(o: JSONObject, codeName: String): Unit = {
      o.put("phone", phone.str)
      code foreach { code => o.put(codeName, code.str) }
      invitation foreach (i => o.put("invitation_code", i.code))
    }

    override def maybeEmail: Option[EmailAddress] = None
    override def maybePhone: Option[PhoneNumber] = Some(phone)
    override def maybePassword: Option[String] = None
    override def maybeUsername: Option[Handle] = None

    override def toString: String = s"PhoneBasedCredentials($phone, ${code map (_.str.map(_ => '*'))})"
  }

  case class UsernameCredentials(handle: Handle, password: Option[String], invitation: Option[PersonalInvitationToken] = None) extends Credentials {
    override def canLogin: Boolean = password.isDefined

    def autoLoginOnRegistration: Boolean = invitation.isDefined

    override def addToRegistrationJson(o: JSONObject): Unit = {
      o.put("email", handle.string)
      password foreach (o.put("password", _))
      invitation foreach (i => o.put("invitation_code", i.code))
    }

    override def addToLoginJson(o: JSONObject): Unit = addToRegistrationJson(o)

    override def maybeEmail: Option[EmailAddress] = None
    override def maybePhone: Option[PhoneNumber] = None
    override def maybePassword: Option[String] = password
    override def maybeUsername: Option[Handle] = Some(handle)

    override def toString: String = s"UsernameBasedCredentials($handle, ${password map (_.map(_ => '*'))})"
  }
}
