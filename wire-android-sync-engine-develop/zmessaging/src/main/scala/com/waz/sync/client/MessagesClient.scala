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
import com.waz.model._
import com.waz.sync.client.OtrClient.{ClientMismatchResponse, MessageResponse}
import com.waz.sync.otr.OtrMessage
import com.waz.utils._
import com.waz.znet.Response.{HttpStatus, Status, SuccessHttpStatus}
import com.waz.znet.ZNetClient._
import com.waz.znet.{Request, Response, ZNetClient}

class MessagesClient(netClient: ZNetClient) {
  import MessagesClient._
  import com.waz.threading.Threading.Implicits.Background

  def postMessage(conv: RConvId, content: OtrMessage, ignoreMissing: Boolean, receivers: Option[Set[UserId]] = None): ErrorOrResponse[MessageResponse] = {
    netClient.withErrorHandling("postOtrMessage", Request.Post(ConvMessagesPath(conv, ignoreMissing, receivers), content)) {
      case Response(SuccessHttpStatus(), ClientMismatchResponse(mismatch), _) => MessageResponse.Success(mismatch)
      case Response(HttpStatus(Status.PreconditionFailed, _), ClientMismatchResponse(mismatch), _) => MessageResponse.Failure(mismatch)
    }
  }
}

object MessagesClient {
  private implicit val tag: LogTag = logTagFor[MessagesClient]

  def ConvMessagesPath(conv: RConvId, ignoreMissing: Boolean, receivers: Option[Set[UserId]] = None) = {
    val base = s"/conversations/$conv/otr/messages"
    if (ignoreMissing) s"$base?ignore_missing=true"
    else receivers.fold2(base, uids => s"$base?report_missing=${uids.iterator.map(_.str).mkString(",")}")
  }
}
