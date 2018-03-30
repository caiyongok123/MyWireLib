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

import com.waz.api.{UsernameValidation, UsernameValidationError}
import com.waz.model.Handle
import com.waz.threading.Threading
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.znet.Response.SuccessHttpStatus
import com.waz.znet._
import com.waz.znet.ZNetClient._

import scala.util.Try
import scala.util.control.NonFatal

object HandlesClient {
  val checkMultipleAvailabilityPath = "/users/handles"
  val checkSingleAvailabilityPath = "/users/handles/"
  val handlesQuery = "handles"
  val MAX_HANDLES_TO_POST = 50
}

class HandlesClient(netClient: ZNetClient)  {
  def getHandlesValidation(handles: Seq[Handle]): ErrorOrResponse[Option[Seq[UsernameValidation]]] = {
    val data = JsonEncoder { o =>
      o.put("handles", JsonEncoder.arrString(handles.take(HandlesClient.MAX_HANDLES_TO_POST).map(_.toString)))
      o.put("return", 10)
    }
    netClient.withErrorHandling("getHandlesValidation", Request.Post(HandlesClient.checkMultipleAvailabilityPath, data)) {
      case Response(SuccessHttpStatus(), UsersHandleResponseContent(availableHandles), headers) =>
        Some(handles
          .filter(u => availableHandles.contains(u.toString))
          .map(u => UsernameValidation(u.toString, UsernameValidationError.NONE)))
    }(Threading.Background)
  }
}

object UsersHandleResponseContent {
  def unapply(response: ResponseContent): Option[Seq[String]] = {
    try {
      response match {
        case JsonArrayResponse(js) => Try(JsonDecoder.array(js, _.getString(_))).toOption
        case _ => None
      }
    } catch {
      case NonFatal(_) =>  None
    }
  }
}
