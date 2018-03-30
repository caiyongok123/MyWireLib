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

import android.util.Base64
import com.waz.model.UserId
import com.waz.model.otr.ClientId

import com.waz.sync.client.OtrClient._
import com.waz.sync.otr.OtrMessage
import com.waz.sync.otr.OtrMessage.OtrMessageEncoder
import com.waz.utils._
import com.waz.znet.ContentEncoder.{BinaryRequestContent, GzippedRequestContent, JsonContentEncoder}
import com.waz.znet.JsonObjectResponse
import org.json.JSONObject
import org.robolectric.shadows.ShadowLog
import org.scalatest._

import scala.concurrent.duration._
import scala.util.Random

@Ignore class OtrClientSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {
  implicit val timeout = 10.seconds

  feature("Response parsing") {

    scenario("Decode user prekeys response") {
      val resp = UserPreKeysResponse.unapply(JsonObjectResponse(new JSONObject(userPreKeysResponse)))
      resp should be('defined)
      val (user, clients) = resp.get

      user shouldEqual UserId("3d0df135-e4f3-482e-bfe3-de0fddec1618")
      clients should have size 2
      clients.map(_._1) shouldEqual Seq(ClientId("e2103ece208ccc39"), ClientId("e489b472207f9875"))
      clients.map(_._2.id) shouldEqual Seq(1, 1)
      clients.map(_._2.data.toSeq) shouldEqual Seq(
        Base64.decode("AAEAAAAAAAAAIG/S8TM6HF1aXMEUw/1ttFqWBwaTirgM+XUdFDjMnbCjAAAAAAAAACAs1X6b4OPOwjdw7vFmQ1N46hCQN8jqsIgI46AC1znNzA==", 0).toSeq,
        Base64.decode("AAEAAAAAAAAAIGcbflL8SQWsv9y2UDSK0p8sZuUfnAbYtxdBqfJE3dLtAAAAAAAAACC6robbP47ltV48HK/SI7acUCzq683hahq5dY/+9bA2vw==", 0).toSeq)
    }

    scenario("Decode /users/prekeys response") {
      val resp = PreKeysResponse.unapply(JsonObjectResponse(new JSONObject(preKeysResponse)))
      resp shouldBe 'defined
      resp.get should have size 1
      resp.get.head._1 shouldEqual UserId("3d0df135-e4f3-482e-bfe3-de0fddec1618")
      val cs = resp.get.head._2
      cs.map(_._1.str).toSet shouldEqual Set("e2103ece208ccc39", "e489b472207f9875")
      cs.map(_._2.id) shouldEqual Seq(1, 1)
      cs.map(_._2.data.toSeq).toSet shouldEqual Set(
        Base64.decode("AAEAAAAAAAAAIG/S8TM6HF1aXMEUw/1ttFqWBwaTirgM+XUdFDjMnbCjAAAAAAAAACAs1X6b4OPOwjdw7vFmQ1N46hCQN8jqsIgI46AC1znNzA==", 0).toSeq,
        Base64.decode("AAEAAAAAAAAAIGcbflL8SQWsv9y2UDSK0p8sZuUfnAbYtxdBqfJE3dLtAAAAAAAAACC6robbP47ltV48HK/SI7acUCzq683hahq5dY/+9bA2vw==", 0).toSeq)
    }

    scenario("Decode clients missing response") {
      ShadowLog.stream = System.out
      val resp = OtrClient.ClientMismatchResponse.unapply(JsonObjectResponse(new JSONObject(ClientsMissingResponse)))
      resp shouldBe 'defined
      resp.get.missing should have size 9
      resp.get.missing.values foreach { _ should have size 1 }
    }
  }

  feature("Message request encoding") {

    val content = EncryptedContent(Seq.fill(12)(UserId()) .map { userId =>
      userId -> Seq.fill(8)(ClientId() -> returning(Array.ofDim[Byte](16)) { Random.nextBytes } ) .toMap
    } .toMap)

    scenario("Encode request as json") {
      val data = JsonEncoder { o =>
        o.put("sender", ClientId().str)
        o.put("recipients", EncryptedContentEncoder(content))
      }
      val body = JsonContentEncoder(data)
      info(s"json request size: ${body.sourceData.length}")
      info(s"gzipped json request size: ${body.data.length}")
    }

    scenario("Encode request as protobuf") {
      val body = OtrMessageEncoder(OtrMessage(ClientId(), content)).asInstanceOf[BinaryRequestContent]
      info(s"protobuf request size: ${body.data.length}")
      info(s"gzipped protobuf request size: ${new GzippedRequestContent(body.data, "application/x-protobuf").data.length}")
    }
  }

  val userPreKeysResponse =
    """
      |{
      |  "user": "3d0df135-e4f3-482e-bfe3-de0fddec1618",
      |  "clients": [
      |    {
      |      "prekey": {
      |        "key": "AAEAAAAAAAAAIG/S8TM6HF1aXMEUw/1ttFqWBwaTirgM+XUdFDjMnbCjAAAAAAAAACAs1X6b4OPOwjdw7vFmQ1N46hCQN8jqsIgI46AC1znNzA==",
      |        "id": 1
      |      },
      |      "client": "e2103ece208ccc39"
      |    },
      |    {
      |      "prekey": {
      |        "key": "AAEAAAAAAAAAIGcbflL8SQWsv9y2UDSK0p8sZuUfnAbYtxdBqfJE3dLtAAAAAAAAACC6robbP47ltV48HK/SI7acUCzq683hahq5dY/+9bA2vw==",
      |        "id": 1
      |      },
      |      "client": "e489b472207f9875"
      |    }
      |  ]
      |}
    """.stripMargin

  val preKeysResponse =
    """
      |{
      |  "3d0df135-e4f3-482e-bfe3-de0fddec1618": {
      |    "e2103ece208ccc39": {
      |      "key": "AAEAAAAAAAAAIG/S8TM6HF1aXMEUw/1ttFqWBwaTirgM+XUdFDjMnbCjAAAAAAAAACAs1X6b4OPOwjdw7vFmQ1N46hCQN8jqsIgI46AC1znNzA==",
      |      "id": 1
      |    },
      |    "e489b472207f9875": {
      |      "key": "AAEAAAAAAAAAIGcbflL8SQWsv9y2UDSK0p8sZuUfnAbYtxdBqfJE3dLtAAAAAAAAACC6robbP47ltV48HK/SI7acUCzq683hahq5dY/+9bA2vw==",
      |      "id": 1
      |    },
      |    "e489b472207f9877": null
      |  },
      |  "5e0f7aebe-7a19-3c4a-38b1aace-ee3c7e5": {
      |    "e489b472207f9876": null
      |  }
      |}
    """.stripMargin

  val ClientsMissingResponse =
    """{
      |   "missing":{
      |      "af6de542-08c7-4408-b22b-37d4476f76a7":[
      |         "8b9a5b5e3eb7f57"
      |      ],
      |      "a1fea457-6e85-49f3-9f9e-4074b43582bd":[
      |         "6ab242687a26bfd"
      |      ],
      |      "46d4f244-9bed-49f4-9330-5f27eed14138":[
      |         "4d89c1b98cd82596"
      |      ],
      |      "61bf5d81-a64d-4332-878d-05796ad2ef60":[
      |         "392e2762139331dc"
      |      ],
      |      "9d4a75bd-98dd-41cd-9bce-76d711f1a461":[
      |         "24640a36e8382da4"
      |      ],
      |      "9582a048-ebef-48c3-9d2b-02953abbc81d":[
      |         "61edc4b384336769"
      |      ],
      |      "1252ea46-6d00-4bc7-a93f-6ad512851eef":[
      |         "b822418632b3cac2"
      |      ],
      |      "c9be4104-aac3-41ba-ac33-3d33bd417373":[
      |         "965fe46bb068b05e"
      |      ],
      |      "ca623fe7-0c14-46b1-9a96-ea3509c565b8":[
      |         "95e4d246f88cf7cc"
      |      ]
      |   },
      |   "time":"2015-09-07T15:00:32.117Z",
      |   "redundant":{
      |
      |   },
      |   "deleted":{
      |
      |   }
      |}""".stripMargin
}
