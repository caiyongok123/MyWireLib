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
package com.waz.sync.otr

import com.waz.model.otr.ClientId
import com.waz.sync.client.OtrClient
import com.waz.sync.client.OtrClient.EncryptedContent
import com.waz.znet.ContentEncoder
import com.waz.znet.ContentEncoder.RequestContent
import com.wire.messages.nano.Otr

case class OtrMessage(sender: ClientId, recipients: EncryptedContent, blob: Option[Array[Byte]] = None, nativePush: Boolean = true)

object OtrMessage {
  implicit lazy val OtrMessageEncoder: ContentEncoder[OtrMessage] = new ContentEncoder[OtrMessage] {
    override def apply(m: OtrMessage): RequestContent = {
      val msg = new Otr.NewOtrMessage
      msg.sender = OtrClient.clientId(m.sender)
      msg.nativePush = m.nativePush
      msg.recipients = m.recipients.userEntries
      m.blob foreach { msg.blob = _ }

      ContentEncoder.protobuf(msg)
    }
  }
}
