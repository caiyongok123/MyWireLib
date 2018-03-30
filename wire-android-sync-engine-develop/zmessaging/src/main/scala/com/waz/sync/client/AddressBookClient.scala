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

import com.waz.model.{AddressBook, ContactId, UserId}
import com.waz.threading.Threading
import com.waz.utils.JsonDecoder
import com.waz.znet.Response.SuccessHttpStatus
import com.waz.znet.ZNetClient._
import com.waz.znet._
import org.json.JSONObject

import scala.concurrent.duration._
import scala.util.Try

class AddressBookClient(netClient: ZNetClient) {
  import Threading.Implicits.Background
  import com.waz.sync.client.AddressBookClient._

  def postAddressBook(a: AddressBook): ErrorOrResponse[Seq[(UserId, Set[ContactId])]] =
    netClient.withErrorHandling("postAddressBook", Request.Post(AddressBookPath, a, timeout = 5.minutes)) {
      case Response(SuccessHttpStatus(), UsersListResponse(users @ _*), _) => users
    }
}

object AddressBookClient {
  val AddressBookPath = "/onboarding/v3"

  type UserAndContactIds = (UserId, Set[ContactId])

  implicit val Decoder: JsonDecoder[UserAndContactIds] = new JsonDecoder[UserAndContactIds] {
    import com.waz.utils.JsonDecoder._
    override def apply(implicit js: JSONObject): (UserId, Set[ContactId]) = ('id, array[ContactId]('cards)((arr, i) => ContactId(arr.getString(i))).toSet)
  }

  object UsersListResponse {
    def unapplySeq(resp: ResponseContent): Option[Seq[(UserId, Set[ContactId])]] = resp match {
      case JsonArrayResponse(js) =>
        Try(JsonDecoder.array[UserAndContactIds](js)).toOption
      case JsonObjectResponse(js) if js.has("results") =>
        val jsArr = js.getJSONArray("results")
        Try(JsonDecoder.array[UserAndContactIds](jsArr)).toOption
      case _ => None
    }
  }

  implicit val RequestEncoder: ContentEncoder[AddressBook] = ContentEncoder.gzipJson[AddressBook]
}
