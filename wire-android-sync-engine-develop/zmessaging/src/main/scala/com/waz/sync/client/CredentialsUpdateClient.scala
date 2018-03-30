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

import com.waz.ZLog._
import com.waz.model.{EmailAddress, Handle, PhoneNumber}
import com.waz.threading.Threading
import com.waz.utils.JsonEncoder
import com.waz.znet.ZNetClient.ErrorOrResponse
import com.waz.znet._

class CredentialsUpdateClient(netClient: ZNetClient) {
  import Threading.Implicits.Background
  private implicit val logTag: LogTag = logTagFor[CredentialsUpdateClient]

  def updateEmail(email: EmailAddress): ErrorOrResponse[Unit] =
    netClient.updateWithErrorHandling("updateEmail", Request.Put(CredentialsUpdateClient.emailPath, JsonEncoder { _.put("email", email.str) }))

  def clearEmail(): ErrorOrResponse[Unit] =
    netClient.updateWithErrorHandling("clearEmail", Request.Delete[Unit](CredentialsUpdateClient.emailPath))

  def updatePhone(phone: PhoneNumber): ErrorOrResponse[Unit] =
    netClient.updateWithErrorHandling("updatePhone", Request.Put(CredentialsUpdateClient.phonePath, JsonEncoder { _.put("phone", phone.str) }))

  def clearPhone(): ErrorOrResponse[Unit] =
    netClient.updateWithErrorHandling("clearPhone", Request.Delete[Unit](CredentialsUpdateClient.phonePath))

  def updatePassword(newPassword: String, currentPassword: Option[String]): ErrorOrResponse[Unit] =
    netClient.updateWithErrorHandling("updatePassword", Request.Put(CredentialsUpdateClient.passwordPath, JsonEncoder { o =>
      o.put("new_password", newPassword)
      currentPassword foreach (o.put("old_password", _))
    }))

  def updateHandle(handle: Handle): ErrorOrResponse[Unit] =
    netClient.updateWithErrorHandling("updateHandle", Request.Put(CredentialsUpdateClient.handlePath, JsonEncoder { _.put("handle", handle.toString) }))
}

object CredentialsUpdateClient {
  val passwordPath = "/self/password"
  val emailPath = "/self/email"
  val phonePath = "/self/phone"
  val handlePath = "/self/handle"
}
