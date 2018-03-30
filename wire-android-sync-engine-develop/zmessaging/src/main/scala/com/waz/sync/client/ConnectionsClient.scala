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
import com.waz.ZLog.ImplicitTag._
import com.waz.api.impl.ErrorResponse
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.Json
import com.waz.utils.JsonDecoder._
import com.waz.znet.Response.{HttpStatus, Status, SuccessHttpStatus}
import com.waz.znet.ZNetClient._
import com.waz.znet._

import scala.util.Try
import scala.util.control.NonFatal

class ConnectionsClient(netClient: ZNetClient) {
  import Threading.Implicits.Background
  import com.waz.sync.client.ConnectionsClient._

  def loadConnections(start: Option[UserId] = None, pageSize: Int = PageSize): ErrorOrResponse[Seq[UserConnectionEvent]] =
    netClient.chainedWithErrorHandling("loadConnections", Request.Get(Request.query(ConnectionsPath, start.map { start => Seq("start" -> start.str) }.getOrElse(Seq.empty) :+ ("size" -> pageSize.toString) :_*))) {
      case Response(SuccessHttpStatus(), ConnectionsResponseExtractor(connections, hasMore), _) =>
        if (!hasMore) CancellableFuture.successful(Right(connections): Either[ErrorResponse, Seq[UserConnectionEvent]])
        else loadConnections(Some(connections.last.to), pageSize) map { _.right.map(connections ++ _) }
    }

  def loadConnection(id: UserId): ErrorOrResponse[UserConnectionEvent] =
    netClient.withErrorHandling(s"loadConnection($id)", Request.Get(s"$ConnectionsPath/$id")) {
      case Response(SuccessHttpStatus(), ConnectionResponseExtractor(evt), _) => evt
    }

  def createConnection(user: UserId, name: String, message: String): ErrorOrResponse[UserConnectionEvent] = {
    val jsonData = Json("user" -> user.toString, "name" -> name, "message" -> message)
    netClient.withErrorHandling("createConnection", Request.Post(ConnectionsPath, jsonData)) {
      case Response(SuccessHttpStatus(), ConnectionResponseExtractor(event), _) =>
        verbose(s"createConnection response: $event")
        event
    }
  }

  def updateConnection(user: UserId, status: ConnectionStatus): ErrorOrResponse[Option[UserConnectionEvent]] =
    netClient.withErrorHandling("updateConnection", Request.Put(s"$ConnectionsPath/$user", Json("status" -> status.code))) {
      case Response(SuccessHttpStatus(), ConnectionResponseExtractor(event), _) =>
        verbose(s"updateConnection response: $event")
        Some(event)
      case Response(HttpStatus(Status.NoResponse, _), _, _) =>
        warn(s"updateConnection request successful, but returned empty response")
        None
    }
}

object ConnectionsClient {
  val ConnectionsPath = "/connections"
  val PageSize = 100

  object ConnectionResponseExtractor {
    def unapply(resp: ResponseContent): Option[UserConnectionEvent] = resp match {
      case JsonObjectResponse(js) => Try(UserConnectionEvent.Decoder(js)).toOption
      case _ =>
        warn(s"unknown response format for connection response: $resp")
        None
    }
  }

  object ConnectionsResponseExtractor {
    def unapply(response: ResponseContent): Option[(List[UserConnectionEvent], Boolean)] = try {
      response match {
        case JsonObjectResponse(js) =>
          Some((if (js.has("connections")) array[UserConnectionEvent](js.getJSONArray("connections")).toList else Nil, decodeBool('has_more)(js)))
        case _ =>
          warn(s"unknown response format for connections response: $response")
          None
      }
    } catch {
      case NonFatal(e) =>
        warn(s"couldn't parse connections response: $response", e)
        None
    }
  }
}
