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
package com.waz.conv

import com.waz.api.ProvisionedApiSpec
import com.waz.threading.CancellableFuture
import com.waz.utils.Json
import com.waz.znet.Request._
import com.waz.znet.Response.{HttpStatus, SuccessHttpStatus}
import com.waz.znet._
import org.json.JSONObject
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class ConversationSyncSpec extends FeatureSpec with Matchers with ProvisionedApiSpec {
  implicit val timeout = 10.seconds: Timeout

  override val provisionFile = "/two_users.json"

  scenario("Create connection and sync conversation") {
    val conversations = api.getConversations

    val client2 = znetClientFor(provisionedEmail("auto2"), "auto2_pass")
    val id1 = getId(netClient)
    val id2 = getId(client2)

    callJs(netClient.apply(Post("/self/connections", Json("message" -> "hello", "user" -> id2, "name" -> "conn1", "status" -> "accepted", "nonce" -> "test"))))
    callAny(client2(Put(s"/self/connections/$id1", Json("status" -> "accepted"), requiresAuthentication = true)))

    withDelay {
      conversations should not be empty
    }
    info(s"count: ${conversations.size()} conversations: ${conversations.get(0)}")
    // TODO: assert create conversation received
  }

  def callJs(f: CancellableFuture[Response]) = Await.result(f, timeout) match {
    case Response(SuccessHttpStatus(), JsonObjectResponse(js), _) => info(s"got response: $js")
    case resp => fail(s"received unexpected response: $resp")
  }

  def callAny(f: CancellableFuture[Response]) = Await.result(f, timeout) match {
    case resp @ Response(SuccessHttpStatus(), _, _) => info(s"got response: $resp")
    case resp => fail(s"received unexpected response: $resp")
  }

  def getId(client: ZNetClient) = Await.result(client(Get("/self", requiresAuthentication = true)), 5.seconds) match {
    case Response(HttpStatus(200, _), JsonObjectResponse(SelfResponse(id, _, _)), _) => id
    case resp => fail(s"received unexpected response for /self: $resp")
  }

  case class SelfResponse(id: String, name: String, email: String)
  object SelfResponse {
    def unapply(js: JSONObject) = Some((js.getString("id"), js.getString("name"), js.getString("email")))
  }
}
