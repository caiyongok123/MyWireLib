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

import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.sync.client.UsersClient.UserResponseExtractor
import com.waz.threading.CancellableFuture
import com.waz.znet.ZNetClient.EmptyClient
import com.waz.znet._
import org.json.{JSONArray, JSONObject}
import org.robolectric.Robolectric
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source

@Ignore class UsersClientSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {
  implicit val timeout = 10.seconds: FiniteDuration

  val userIdStr = "13962457-c316-4de1-9962-929c40f8cff4"
  val userId = UserId(userIdStr)
  val userResponse =
    s"""
      |{
      |  "email": "zbigniew@wearezeta.com",
      |  "phone": null,
      |  "accent": [
      |    0.14100000262260437,
      |    0.5519999861717224,
      |    0.8270000219345093,
      |    1
      |  ],
      |  "accent_id": 0,
      |  "picture": [
      |    {
      |      "content_length": 1608,
      |      "data": "UklGRkAGAABXRUJQVlA4IDQGAACQIACdASqAAIAAPpFAmkqlo6IiJ5VbaLASCWcG+GcASzJbyNm36zANN/NR8jeoR0rfRA/ao3KkZg2zIA8GwYHs5tunGTEPYI06xBd1Y/72HWS8uVOajUNzGpbKkaMXiz7xBZTZnqfd3kPAOBESxOvp34eFDeUGs3Pp3F/XZ5bHVilKeQmKSqKs+POu2bxTkK5qe7x2PCvHe37ewlR0SInoTFo8JFuLGoJxPa6mUy1cIzNQevNkzPbq2zUFiGWjmwY/Q380SPCAurlr1AUuPeFICSHH9gRCU+IFK+nGpZ0u6x99jvFbJaaDk9XazutQZ0+OUzWHahdrUNV5MLRMS0xW5yyVLXlEmrT3snTEMAD+93cjRuf1PzJOVZFvgpwTXwXnna4oyXh2Qyvs1gysHugXFTN/wMWdRA8kxSvuv3G7dsahy9D+siRt/zjM9ryaSDLVgFVWw6LfF7MbhvFbR8ZdQT8pFcYsDMwEW7sVmOPDHWc/l//Yn4f9UqVO8520Ve3b0h6Z9wupcgzFT4If8WD57NCntIJuztkefjBUTZHU9PoiR8igvRORFCi/vi5OsUFP9JMBD3SNn9dlAIjOdbMWYjAmWG+JO5gUnOMagW4M563rzOWmOMub3Rm6Yzr1z8BKpMZmm8K/K6GeFvz3+QJsFZbZ+A2igyljRiGzoHFJrl+jozSs6W0asdA7zmAoNkif87L3haiUrTLUhLQulm/Lx4QYt0enhzkIL69BwVoHL82qEEBDvKkO3uEd5gF4V6dkRjE/zUmunUPeAKqrZhd97HKXE8G1u9O1Bwwai8nUa6BIG6AQDsfykOJ5pxSEd6p0+juK8iKwChx3XETk5CuDEqzCg/vBOfsn++Gj+111FDGpJwORHZhtjDVWvsiyLWhzFJQfsOv8a3MwKzFLT4M57nXnwJYxSEBRL7f3Nt9cft5h7VhhlzcYoptxWb1ulgx4DSfeewwXQLDBSbFCHlF4XLqeW24zZm/+fPmY3zlLB++IZwqGHsrB9H9ainq/VnCQHSHe5TT3XowAuKTltZvvo+S5hIV9utacO1fTeLlEabxDK+C1NledEohCWauxMpc6ok6rlu1d9OVrZU6D6vX6zSP4xyGOZ+yuAg8jCiJBNF/+dNRm8u91N9WYsH/HAamWhARAsYSJzyQbf8V6nxJzbM0zOu8TbCcTxhiUFIIiY3ru8Y9QSgRngqGjr/stERpQYQTXDBlhAnF733Gv9ufnuRBLjKpflyZEA0x0YoFtPuxJbTqQ4UlcV3sRFFP2eoFhmtHsXpVdw779e8W1hrzRT+CWaVSP5PGHyL3xo/k1JF+nB+lmhSkjK3fLoDlTs9pVIM2Wbz04jiVP7m9wsPEnRAdKzA1WcOWQqwpJpv5xOeZ4wcOXAMeg7ea3d6v0iqXlUETITuZzLXEssWLtLN17jbyPKpusl7Er/su5+g3rXesqscdaBiazy66NqUdMmHDzz7HQAWJKJZQxnvg1Nyfv/+IqE9sqnqv5K9oc7wLCDmNLtJdvsCopaBjj0TraLPQP1ixswbsFQAoV+IyswfhOW5PgGdRkmDrA0Bp3KABSzDTM+WUdKGrXnxp5NMPoO06N2sI9peBPusU9RhbyiTBHyWvt2/r9SSKqsffRBxfp13s6/6YpjTFGIZbtsIaY6guNvDx2V9GYSlgy0jul0hosXV9+ChQgFHS3NHwzB05L4L0EiZEVmBABacz1HKFj0aVkEJVCnMPMRQ5rFgAc2yky42RO3gcEQe3djNE5HUYvbQ4Am5uvQ0+gm+EQn5b+C32y2xpzLySZa5eK2JKO3EnVa9RXhX6b/V+qQ3Cn0IIeBKgZfm0MaxwK7FYJMGHgJNPaSBW3val1WoS3jDxzhab9QRkjTVTsZ7xnK4UDLOrUoTkbfoM7QxqMbGvhrMa/7Y2gH6h/FVCu2cV/rWMSovgGsGie+Xow6oUzHgTevfSkQBYcZcvy41GIBvRUnaAEh553cOLVV8r5p3Nqd47lzV1NdEcdfNVX/GOt/94LFoDXq0MUyj6WDDeT05D3ijTJI3Hwd+ccvu2fZnwDDbKBbh7MLABu8pb6WAz2kPidghgskHgYC7/1HkolqpA+FwRjJ9oawrAA",
      |      "content_type": "image/webp",
      |      "id": "d86c5ca5-2388-5078-8980-4c49b16b6b27",
      |      "info": {
      |        "height": 128,
      |        "tag": "preview",
      |        "original_width": 512,
      |        "width": 128,
      |        "name": "TODO",
      |        "correlation_id": "5e0f7aebe-7a19-3c4a-38b1aace-ee3c7e5",
      |        "original_height": 512,
      |        "nonce": "33b061c0e-bac0-bc32-8714af73-7aa7c24",
      |        "public": true
      |      }
      |    },
      |    {
      |      "content_length": 10414,
      |      "data": "",
      |      "content_type": "image/webp",
      |      "id": "1b76f2bb-ed1b-5216-99b5-b64ec5548cd6",
      |      "info": {
      |        "height": 512,
      |        "tag": "medium",
      |        "original_width": 512,
      |        "width": 512,
      |        "name": "TODO",
      |        "correlation_id": "5e0f7aebe-7a19-3c4a-38b1aace-ee3c7e5",
      |        "original_height": 512,
      |        "nonce": "c24ab7a37-b2df-400e-9ea915c5-864de03",
      |        "public": true
      |      }
      |    }
      |  ],
      |  "name": "Foo",
      |  "id": $userIdStr
      |}""".stripMargin

  before {
    ZMessaging.context = Robolectric.application
  }

  feature("Response parsing") {

    scenario("Parse single user response") {
      val response = UserInfo.Decoder(new JSONObject(userResponse))

      response.id shouldEqual userId

      response.picture.flatMap(_.head.convId) shouldEqual Some(RConvId(userId.str))

      info(s"parsed response: $response")
    }

    scenario("Parse self response") {
      val response = """{"email":"zbigniew@wearezeta.com","phone":null,"tracking_id":"15e38d10-b0cf-4877-9906-0923a74da3b0","accent_id":2,"picture":[{"content_length":7286,"data":null,"content_type":"image\/jpeg","id":"e56d823d-6c33-5ea5-9965-21230d4dc985","info":{"height":280,"tag":"smallProfile","original_width":512,"width":280,"name":"","correlation_id":"308133e9-6fd8-4652-baf4-3db41904e912","original_height":512,"nonce":"308133e9-6fd8-4652-baf4-3db41904e912","public":true}},{"content_length":28940,"data":null,"content_type":"image\/jpeg","id":"f0718f9b-8f92-5a2f-815a-8f85fca52690","info":{"height":512,"tag":"medium","original_width":512,"width":512,"name":"","correlation_id":"308133e9-6fd8-4652-baf4-3db41904e912","original_height":512,"nonce":"308133e9-6fd8-4652-baf4-3db41904e912","public":true}}],"name":"Zbigniew Szymanski","id":"5604c40a-4fec-454f-a388-e3bbe1bb6e5b"}"""

      UserResponseExtractor.unapplySeq(new JsonObjectResponse(new JSONObject(response))) shouldBe defined
    }
  }

  def readResource(path: String) = Source.fromInputStream(getClass.getResourceAsStream(path), "utf8").getLines().mkString("\n")

  feature("Loading users") {
    scenario("Load a few users at once successfully") {
      val users = List.fill(45)(UserId())
      Await.result(successfulClient.loadUsers(users), timeout).right.map(_ map (_.id)) should be(Right(Seq(UserId(userIdStr))))
    }

    scenario("Load a lot of users at once successfully") {
      val users = List.fill(46)(UserId())
      Await.result(successfulClient.loadUsers(users), timeout).right.map(_ map (_.id)) should be(Right(Seq(UserId(userIdStr), UserId(userIdStr))))
    }

    scenario("Load a lot of users where requests fail") {
      val users = List.fill(181)(UserId())
      val responses = Await.result(failingClient.loadUsers(users), timeout)
      responses should be ('left)
    }
  }

  def successfulClient = mockedClient(Seq.fill(50)(oneUserResponse): _*)
  def failingClient = mockedClient(oneUserResponse, notFound, oneUserResponse, uriTooLong, oneUserResponse)

  def oneUserResponse: Response = Response(Response.HttpStatus(200), JsonArrayResponse(new JSONArray(s"[$userResponse]")))
  def notFound: Response = Response(Response.HttpStatus(404))
  def uriTooLong: Response = Response(Response.HttpStatus(414))

  def mockedClient(responses: Response*) = new UsersClient(new EmptyClient {
      private var idx = 0
      override def apply[A](r: Request[A]): CancellableFuture[Response] = {
        val resp = responses(idx)
        idx += 1
        CancellableFuture.successful(resp)
      }
    }
  )
}
