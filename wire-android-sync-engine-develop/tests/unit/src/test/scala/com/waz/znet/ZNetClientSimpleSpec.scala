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
package com.waz.znet

import java.util.concurrent.TimeUnit

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.waz.service.{BackendConfig, ZMessaging}
import com.waz.znet.Response.HttpStatus
import org.json.JSONObject
import org.robolectric.Robolectric
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

@Ignore class ZNetClientSimpleSpec extends FeatureSpecLike with Matchers with BeforeAndAfter with RobolectricTests {

  val wireMockPort = 9000 + Random.nextInt(3000)
  val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
    .port(wireMockPort)
    .httpsPort(4433)
    .withRootDirectory(getClass.getResource("/ZNetClientSpec").getPath)
  )
  val email = "test@mail.com"
  val password = "password"

  var client: ZNetClient = _

  before {
    ZMessaging.context = Robolectric.application
    wireMockServer.start()
    configureFor("localhost", wireMockPort)
    client = new ZNetClientImpl(None, new AsyncClientImpl, BackendConfig("http://localhost:" + wireMockPort).baseUrl)
  }

  after {
    wireMockServer.stop()
    Await.ready(client.close(), 10.seconds)
  }

  def get(path: String, auth: Boolean = false) = Await.result(client(Request[Unit]("GET", Some(path), requiresAuthentication = auth)), Duration(1000, TimeUnit.MILLISECONDS))

  def post[A: ContentEncoder](path: String, data: A, auth: Boolean = false) = Await.result(client(Request("POST", Some(path), data = Some(data), requiresAuthentication = auth)), Duration(500, TimeUnit.MILLISECONDS))

  feature("GET request") {

    scenario("Empty http GET request - 200") {
      get("/get/empty200") match {
        case Response(HttpStatus(200, _), EmptyResponse, _) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(200))")
      }
    }

    scenario("Empty http GET request - 400") {
      get("/get/empty400")match {
        case Response(HttpStatus(400, _), EmptyResponse, _) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(400))")
      }
    }

    scenario("Perform json GET request") {
      val json = """{"result": "ok"}"""
      get("/get/json200") match {
        case Response(HttpStatus(200, _), JsonObjectResponse(js), _) if js.toString == new JSONObject(json).toString => //fine
        case r => fail(s"got: $r instead of Response(HttpStatus(200), JsonBody($json))")
      }
    }
  }

  feature("POST request") {

    scenario("Empty http POST request - 200") {
      post("/post/empty200", {}) match {
        case Response(HttpStatus(200, _), EmptyResponse, _) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(200))")
      }
    }

    scenario("Post json, receive empty 200") {
      post("/post/json_empty200", new JSONObject("""{ "key": "value"}""")) match {
        case Response(HttpStatus(200, _), EmptyResponse, _) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(200))")
      }
    }

    scenario("Post json, receive json 200") {
      val json = """{"result": "ok"}"""
      post("/post/json_json200", new JSONObject("""{ "key": "value"}""")) match {
        case Response(HttpStatus(200, _), JsonObjectResponse(js), _) if js.toString == new JSONObject(json).toString => //fine
        case r => fail(s"got: $r instead of Response(HttpStatus(200), JsonBody($json))")
      }
    }
  }

  feature("Authentication") {

    scenario("Call /self with access token") {
      get("/self", auth = true) match {
        case Response(HttpStatus(200, _), JsonObjectResponse(js), _) if js.getString("email") == "test@mail.com" => //fine
        case r => fail(s"got: $r instead of Response(HttpStatus(200), JsonBody({email: test@mail.com ..}))")
      }
    }
  }
}
