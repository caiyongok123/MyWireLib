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
import com.waz.model.SearchQuery.{Recommended, RecommendedHandle, TopPeople}
import com.waz.model._
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.JsonDecoder
import com.waz.znet.Response.Status.NotFound
import com.waz.znet.Response.{Status, SuccessHttpStatus}
import com.waz.znet.ZNetClient.ErrorOrResponse
import com.waz.znet.{JsonObjectResponse, _}
import org.json.JSONObject

import scala.util.control.NonFatal

class UserSearchClient(netClient: ZNetClient) {
  import Threading.Implicits.Background
  import UserSearchClient._

  def getContacts(query: SearchQuery, limit: Int = DefaultLimit): ErrorOrResponse[Seq[UserSearchEntry]] = {
    debug(s"graphSearch('$query', $limit)")

    query match {
      case Recommended(prefix)       => extractUsers(s"Recommended($prefix)", Request.Get(contactsQuery(prefix, limit, Relation.Third.id, useDirectory = true)))
      case RecommendedHandle(prefix) => extractUsers(s"RecommendedHandle($prefix)", Request.Get(contactsQuery(prefix, limit, Relation.Third.id, useDirectory = true)))
      case TopPeople                 =>
        warn("A request to /search/top was made - this is now only handled locally")
        CancellableFuture.successful(Right(Seq.empty))
    }

  }

  def contactsQuery(query: String, limit: Int, level: Int, useDirectory: Boolean): String =
    Request.query(ContactsPath, "q" -> query, "size" -> limit, "l" -> level, "d" -> (if (useDirectory) 1 else 0))

  private def extractUsers(name: String, req: Request[Unit]): ErrorOrResponse[Seq[UserSearchEntry]] = {
    val handling404s: PartialFunction[Response, Either[ErrorResponse, Seq[UserSearchEntry]]] = {
      case Response(SuccessHttpStatus(), UserSearchResponse(users), _) =>
        debug(s"user search received: $users")
        Right(users)
      case Response(status, _, _) if status.status == NotFound =>
        warn(s"search service doesn't know about us yet")
        Left(ErrorResponse(Status.RateLimiting, "user not found", "internal-error")) // convert to rate limited so that it will be retried...
    }

    netClient(req) map (handling404s orElse ZNetClient.errorHandling(name))
  }

  def exactMatchHandle(handle: Handle): ErrorOrResponse[Option[UserId]] = {
    val handling404s: PartialFunction[Response, Either[ErrorResponse, Option[UserId]]] = {
      case Response(SuccessHttpStatus(), ExactMatchHandleResponseContent(userId), _) =>
        debug(s"user id received: $userId, for the handle: $handle")
        Right(Some(userId))
      case Response(status, _, _) if status.status == NotFound =>
        debug(s"exact handle match not found for $handle")
        Right(None)
      case other =>
        warn(s"error while matching handle $handle : $other")
        Left(ErrorResponse.InternalError)
    }

    netClient(Request.Get(UserSearchClient.HandlesPath + "/" + Handle.stripSymbol(handle.string))) map (handling404s orElse ZNetClient.errorHandling("exactMatchHandle"))
  }
}

object UserSearchClient {
  val ContactsPath = "/search/contacts"
  val HandlesPath = "/users/handles"

  val DefaultLimit = 10

  case class UserSearchEntry(id: UserId, name: String, colorId: Option[Int], handle: Handle)

  object UserSearchEntry {
    import JsonDecoder._

    implicit lazy val Decoder: JsonDecoder[Option[UserSearchEntry]] = new JsonDecoder[Option[UserSearchEntry]] {
      override def apply(implicit js: JSONObject): Option[UserSearchEntry] =
        if (js.has("handle")) Some(UserSearchEntry('id, 'name, 'accent_id, 'handle)) else None
    }
  }

  object UserSearchResponse {

    def unapply(resp: ResponseContent): Option[Seq[UserSearchEntry]] = resp match {
      case JsonObjectResponse(js) if js.has("documents") =>
        try {
          Some(JsonDecoder.array[Option[UserSearchEntry]](js.getJSONArray("documents")).flatten)
        } catch {
          case NonFatal(ex) => warn(s"parsing failed", ex)
            None
        }
      case _ => None
    }

  }

  object ExactMatchHandleResponseContent {
    def unapply(response: ResponseContent): Option[UserId] = response match {
      case JsonObjectResponse(js) if js.has("user") => Some(UserId(js.getString("user")))
      case _ => None
    }
  }

}
