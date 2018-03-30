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
package com.waz.model

import java.util.Locale

import com.waz.utils.Locales.bcp47
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject

case class Invitation(id: ContactId, method: Either[EmailAddress, PhoneNumber], nameOfInvitee: String, nameOfInviter: String, message: String, locale: Option[Locale])

object Invitation extends ((ContactId, Either[EmailAddress, PhoneNumber], String, String, String, Option[Locale]) => Invitation) {
  implicit lazy val InvitationEncoder: JsonEncoder[Invitation] = new JsonEncoder[Invitation] {
    override def apply(inv: Invitation): JSONObject = JsonEncoder { o =>
      o.put("id", inv.id.str)
      inv.method match {
        case Left(email) => o.put("emailAddress", email.str)
        case Right(phone) => o.put("phoneNumber", phone.str)
      }
      o.put("nameOfInvitee", inv.nameOfInvitee)
      o.put("nameOfInviter", inv.nameOfInviter)
      o.put("message", inv.message)
      inv.locale.foreach(l => o.put("locale", bcp47.languageTagOf(l)))
    }
  }

  import com.waz.utils.JsonDecoder._

  implicit lazy val InvitationDecoder: JsonDecoder[Invitation] = new JsonDecoder[Invitation] {
    override def apply(implicit js: JSONObject): Invitation = Invitation(
      decodeId[ContactId]('id),
      decodeOptEmailAddress('emailAddress).toLeft(decodePhoneNumber('phoneNumber)),
      'nameOfInvitee,
      'nameOfInviter,
      'message,
      decodeLocale('locale))
  }
}
