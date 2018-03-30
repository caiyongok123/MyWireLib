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

import java.text.SimpleDateFormat
import java.util.TimeZone

import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.CancellableFuture
import com.waz.znet.ContentEncoder.ByteArrayRequestContent
import com.waz.znet.Response.HttpStatus
import com.waz.znet.ZNetClient.EmptyClient
import com.waz.znet._
import org.json.JSONObject
import org.robolectric.Robolectric
import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@Ignore class ConnectionsClientSpec extends FeatureSpec with Matchers with BeforeAndAfter with OptionValues with RobolectricTests {

  before {
    ZMessaging.context = Robolectric.application
  }

  feature("Response parsing") {

    scenario("Decode JSONObject to UserConnection") {
      val jsonObject = new JSONObject(connectionResponse)

      val userConnection = UserConnectionEvent(
        RConvId("a129255b-ad8c-4a56-b630-b50daf54ea8e"),
        UserId("72733645-9170-499b-916b-aa3ce2adb8c2"),
        UserId("2eb5ef16-1c1c-40bb-a4c5-0ad1cca731cc"),
        Some("Hi Meep,\n\nLet's connect in Zeta.\n\nMoop"),
        UserData.ConnectionStatus.Accepted,
        getDateFormat.parse("2014-04-01T15:34:56.723Z")
      )

      Event.EventDecoder.connectionEvent(jsonObject, name = None) should be(userConnection)
    }

    scenario("Extract connection from response") {
      val response = JsonObjectResponse(new JSONObject(connectionResponse))
      ConnectionsClient.ConnectionResponseExtractor.unapply(response) should be('defined)
    }

    scenario("Extract connections from response") {
      val response = JsonObjectResponse(new JSONObject(connectionsResponse))
      val (conns, hasMore) = ConnectionsClient.ConnectionsResponseExtractor.unapply(response).value
      conns should have size 5
      hasMore shouldEqual true
    }

    scenario("Load connection from response") {

      class MockUsersClient extends ConnectionsClient(new EmptyClient {
        @volatile private var more = false

        override def apply[A](r: Request[A]): CancellableFuture[Response] = {
            val response = if (!more) {
              more = true
              Response(HttpStatus(200, "HTTP/1.1 200 OK"), JsonObjectResponse(new JSONObject(connectionsResponse)))
            } else
              Response(HttpStatus(200, "HTTP/1.1 200 OK"), JsonObjectResponse(new JSONObject(connectionsResponseMore)))
            CancellableFuture.successful(response)
          }
          override def close(): Future[Unit] = Future.successful(Unit)
        }
      )

      val client = new MockUsersClient
      val connections = Await.result(client.loadConnections(), 10.seconds)

      connections should be('right)
      connections.right.get should have size 7
    }

    scenario("Create connection") {

      var sendJson: Option[JSONObject] = None

      class MockUsersClient extends ConnectionsClient(new EmptyClient {
          override def apply[A](r: Request[A]): CancellableFuture[Response] = {

            r.getBody match {
              case c: ByteArrayRequestContent => sendJson = Some(new JSONObject(new String(c.sourceData, "UTF8")))
              case _ =>
            }

            val response = Response(HttpStatus(200, "HTTP/1.1 200 OK"))
            CancellableFuture.successful(response)
          }
          override def close(): Future[Unit] = Future.successful(Unit)
        }
      )

      val client = new MockUsersClient

      client.createConnection(UserId("123"), "Test", "Hello")

      sendJson should be('defined)
      sendJson.get.getString("user") should be("123")
      sendJson.get.getString("name") should be("Test")
      sendJson.get.getString("message") should be ("Hello")
    }
  }

  def getDateFormat = {
    val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    format.setTimeZone(TimeZone.getTimeZone("Zulu"))
    format
  }

  val connectionResponse =
    """{
      |    "status": "accepted",
      |    "conversation": "a129255b-ad8c-4a56-b630-b50daf54ea8e",
      |    "to": "2eb5ef16-1c1c-40bb-a4c5-0ad1cca731cc",
      |    "from": "72733645-9170-499b-916b-aa3ce2adb8c2",
      |    "last_update": "2014-04-01T15:34:56.723Z",
      |    "message": "Hi Meep,\n\nLet's connect in Zeta.\n\nMoop"
      |}""".stripMargin

  val connectionsResponse =
    """{
      |    "connections": [
      |        {
      |          "message":"Hey,\nwanna connect?\n<python client lame message>",
      |          "to":"0d2ef89b-0e4b-4fd2-86c1-d464c65cc3bf",
      |          "last_update":"2014-05-21T13:44:01.473Z",
      |          "status":"accepted",
      |          "conversation":"9f9e2f11-47b0-47bb-8787-d67111c36791",
      |          "from":"926f3676-3965-47e9-80c6-020d4b66a3d2"
      |        },
      |        {
      |          "message":"Hey,\nwanna connect?\n<python client lame message>",
      |          "to":"7c6fb830-f4a1-4dfc-a790-8212ffb218ec",
      |          "last_update":"2014-05-21T13:43:56.279Z",
      |          "status":"accepted",
      |          "conversation":"0edeeea6-2e06-45e5-a856-841f4b18bcbe",
      |          "from":"926f3676-3965-47e9-80c6-020d4b66a3d2"
      |        },
      |        {
      |          "message":"Hey,\nwanna connect?\n<python client lame message>",
      |          "to":"845571d3-f28b-492b-91fe-093f864d0915",
      |          "last_update":"2014-05-21T13:44:00.002Z",
      |          "status":"accepted",
      |          "conversation":"16c4a849-2bf0-4114-92c4-0b4cd1b3ace7",
      |          "from":"926f3676-3965-47e9-80c6-020d4b66a3d2"
      |        },
      |        {
      |          "message":"Hi from integration test",
      |          "to":"9e36737a-ae4d-4ecd-9a26-1df8cc8867d2",
      |          "last_update":"2014-05-21T13:44:12.020Z",
      |          "status":"sent",
      |          "conversation":"30a5a9f0-e7b2-46b6-9aec-200517ef0ba4",
      |          "from":"926f3676-3965-47e9-80c6-020d4b66a3d2"
      |        },
      |        {
      |          "message":"Hey,\nwanna connect?\n<python client lame message>",
      |          "to":"af547852-e53c-43bc-853e-b6bdd3f2a664",
      |          "last_update":"2014-05-21T13:43:58.497Z",
      |          "status":"accepted",
      |          "conversation":"41c3aec8-1ea1-4ba5-8604-b8ca1f594a36",
      |          "from":"926f3676-3965-47e9-80c6-020d4b66a3d2"
      |        }
      |    ],
      |    "has_more": true
      |}""".stripMargin

  val connectionsResponseMore =
    """{
      |   "connections": [
      |        {
      |          "message":"Hi from yet another test",
      |          "to":"9e36737a-ae4e-4ecd-9a26-1df8cc8867d2",
      |          "last_update":"2014-05-21T13:44:12.020Z",
      |          "status":"sent",
      |          "conversation":"30aba9f0-e7b2-46b6-9aec-200517ef0ba4",
      |          "from":"926f3676-3965-47e9-80c6-020d4b66a3d2"
      |        },
      |        {
      |          "message":"Meeeeeep!",
      |          "to":"af547852-e53f-43bc-853e-b6bdd3f2a664",
      |          "last_update":"2014-05-21T13:43:58.497Z",
      |          "status":"accepted",
      |          "conversation":"41c8aec8-1ea1-4ba5-8604-b8ca1f594a36",
      |          "from":"926f3676-3965-47e9-80c6-020d4b66a3d2"
      |        }
      |   ],
      |   "has_more": false
      |}""".stripMargin
}
