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

import com.waz.model.PushToken
import com.waz.model.otr.ClientId
import com.waz.threading.Threading
import com.waz.utils.{JsonDecoder, JsonEncoder}
import com.waz.znet.Response.SuccessHttpStatus
import com.waz.znet.ZNetClient.ErrorOrResponse
import com.waz.znet._
import org.json.JSONObject

import scala.util.Try

class PushTokenClient(netClient: ZNetClient) {
  import Threading.Implicits.Background
  import com.waz.sync.client.PushTokenClient._

  def postPushToken(token: PushTokenRegistration): ErrorOrResponse[PushTokenRegistration] =
    netClient.withErrorHandling("postPushToken", Request.Post(PushesPath, token)) {
      case Response(SuccessHttpStatus(), GcmResponseExtractor(res), _) => res
    }

  def deletePushToken(token: String): ErrorOrResponse[Unit] =
    netClient.updateWithErrorHandling("deletePushToken", Request.Delete[Unit](s"$PushesPath/$token"))
}

object PushTokenClient {
  val PushesPath = "/push/tokens"

  case class PushTokenRegistration(token: PushToken, senderId: String, clientId: ClientId, transport: String = "GCM")
  object PushTokenRegistration {

    implicit lazy val Decoder: JsonDecoder[PushTokenRegistration] = new JsonDecoder[PushTokenRegistration] {
      import com.waz.utils.JsonDecoder._
      override def apply(implicit js: JSONObject): PushTokenRegistration = PushTokenRegistration('token, 'app, decodeId[ClientId]('client), 'transport)
    }

    implicit lazy val Encoder: JsonEncoder[PushTokenRegistration] = new JsonEncoder[PushTokenRegistration] {
      override def apply(v: PushTokenRegistration): JSONObject = JsonEncoder { o =>
        o.put("token", v.token)
        o.put("app", v.senderId)
        o.put("transport", v.transport)
        o.put("client", v.clientId.str)
      }
    }

    implicit val TokenContentEncoder: ContentEncoder[PushTokenRegistration] = ContentEncoder.json[PushTokenRegistration]
  }

  object GcmResponseExtractor {
    def unapplySeq(resp: ResponseContent): Option[Seq[PushTokenRegistration]] = resp match {
      case JsonArrayResponse(js) => Try(JsonDecoder.array[PushTokenRegistration](js)).toOption
      case JsonObjectResponse(js) => Try(Seq(PushTokenRegistration.Decoder(js))).toOption
      case _ => None
    }
  }
}
