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

import com.waz.ZLog.ImplicitTag._
import com.waz.model._
import com.waz.specs.AndroidFreeSpec
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.znet.Response.{HttpStatus, InternalError}
import com.waz.znet.{JsonObjectResponse, Request, Response, ZNetClient}
import com.waz.znet.ZNetClient.errorHandling
import org.json.JSONObject

import scala.concurrent.{ExecutionContext, Future}

class IntegrationsClientSpec extends AndroidFreeSpec {
  import IntegrationsClientSpec._

  implicit val ctx = Threading.Background

  feature("integrations") {

    val zNetClient = mock[ZNetClient]
    (zNetClient.apply(_: Request[Unit]))
      .expects(*)
      .anyNumberOfTimes()
      .onCall { request: Request[Unit] =>

        val jsonOpt = request.resourcePath.get match {
          case path if path.contains("/providers") && path.contains("/services") => getIntegrationJson(path)
          case path if path.contains("/services") => searchIntegrationsJson(path)
          case _ => None
        }

        val response = jsonOpt match {
          case Some(json) => Response(HttpStatus(200, "HTTP/1.1 200 OK"), JsonObjectResponse(json))
          case None => Response(InternalError(s"Invalid request: $request"))
        }

        CancellableFuture { response }
      }

    (zNetClient.withErrorHandling[Unit, Any](_: String, _: Request[Unit])(_: PartialFunction[Response, Any])(_: ExecutionContext))
      .expects(*, *, *, *)
      .anyNumberOfTimes()
      .onCall { (name: String, request: Request[Unit], pf: PartialFunction[Response, Any], ec: ExecutionContext) =>
        zNetClient.apply(request).map(pf.andThen(Right(_)).orElse(errorHandling(name)))(ec)
      }

    def searchIntegrations(startWith: String) = new IntegrationsClientImpl(zNetClient).searchIntegrations(startWith).future.flatMap {
      case Right(data) => Future.successful(data)
      case Left(error) => Future.failed(new Exception(error.message))
    }

    def getIntegration(providerId: ProviderId, integrationId: IntegrationId) = new IntegrationsClientImpl(zNetClient).getIntegration(providerId, integrationId).future.flatMap {
      case Right(data) => Future.successful(data)
      case Left(error) => Future.failed(new Exception(error.message))
    }

    scenario("get all integrations") {
      result(searchIntegrations("")).size shouldEqual 3
    }

    scenario("get collectionsbot") {
      val res = result(searchIntegrations("collectionsbot"))

      res.size shouldEqual 1
      res.keys.head.name shouldEqual "collectionsbot"
    }

    scenario("get Echo bots") {
      val res = result(searchIntegrations("Echo"))

      res.size shouldEqual 2
      res.keys.forall(_.name.startsWith("Echo")) shouldEqual true
    }

    scenario("get Echo bots - letter case important") {
      result(searchIntegrations("echo")).isEmpty shouldEqual true
    }

    scenario("get integration by id") {
      result(getIntegration(ProviderId(providerId1), IntegrationId(integrationId1)))
    }

  }

  feature("providers") {

    val zNetClient = mock[ZNetClient]
    (zNetClient.apply(_: Request[Unit]))
      .expects(*)
      .anyNumberOfTimes()
      .onCall { request: Request[Unit] =>

        val jsonOpt = request.resourcePath.get match {
          case path if path.contains("/providers") => getProviderJson(path)
          case _ => None
        }

        val response = jsonOpt match {
          case Some(json) => Response(HttpStatus(200, "HTTP/1.1 200 OK"), JsonObjectResponse(json))
          case None => Response(InternalError(s"Invalid request: $request"))
        }

        CancellableFuture { response }
      }

    (zNetClient.withErrorHandling[Unit, ProviderData](_: String, _: Request[Unit])(_: PartialFunction[Response, ProviderData])(_: ExecutionContext))
      .expects(*, *, *, *)
      .anyNumberOfTimes()
      .onCall { (name: String, request: Request[Unit], pf: PartialFunction[Response, ProviderData], ec: ExecutionContext) =>
        zNetClient.apply(request).map(pf.andThen(Right(_)).orElse(errorHandling(name)))(ec)
      }

    def providerFuture(id: ProviderId) = new IntegrationsClientImpl(zNetClient).getProvider(id).future.flatMap {
      case Right(provider) => Future.successful(provider)
      case Left(error) => Future.failed(new Exception(error.message))
    }

    scenario("get provider") {
      result(providerFuture(ProviderId(providerId0))).name equals "Wire Swiss GmbH"
    }
  }

  feature("bots") {
   /* val zNetClient = mock[ZNetClient]

    (zNetClient.apply(_: Request[JSONObject]))
      .expects(*)
      .anyNumberOfTimes()
      .onCall { request: Request[JSONObject] =>

        val jsonOpt = request.getBody match {
          case JSONObject(js) => getAddBotJson(path)
          case _ => None
        }

        val response = jsonOpt match {
          case Some(json) => Response(HttpStatus(200, "HTTP/1.1 200 OK"), JsonObjectResponse(json))
          case None => Response(InternalError(s"Invalid request: $request"))
        }

        CancellableFuture { response }
      }

    (zNetClient.withErrorHandling[JSONObject, NewBotData](_: String, _: Request[JSONObject])(_: PartialFunction[Response, NewBotData])(_: ExecutionContext))
      .expects(*, *, *, *)
      .anyNumberOfTimes()
      .onCall { (name: String, request: Request[JSONObject], pf: PartialFunction[Response, NewBotData], ec: ExecutionContext) =>
        zNetClient.apply(request).map(pf.andThen(Right(_)).orElse(errorHandling(name)))(ec)
      }

    scenario("add bot to a conversation") {
      val f = new IntegrationsClientImpl(zNetClient).addBot(ConvId(conversationId), ProviderId(providerId1), IntegrationId(integrationId1)).future.flatMap {
        case Right(data) => Future.successful(data)
        case Left(error) => Future.failed(new Exception(error.message))
      }

      val data = result(f)

      data.id shouldEqual UserId(botId)
      data.assets.size shouldEqual 1
      data.assets.head.id shouldEqual AssetId(assetId)
      data.event.convId shouldEqual RConvId(conversationId)
      data.event.from shouldEqual UserId(selfId)
      data.event.userIds.size shouldEqual 1
      data.event.userIds.head shouldEqual UserId(botId)
    }*/

    scenario("deserialize user info with service") {
      import IntegrationsClientSpec._

      val infoJson = UserInfo.Decoder(new JSONObject(userInfoWithService))

      infoJson.service shouldEqual Some(UserInfo.Service(IntegrationId(integrationId1), ProviderId(providerId1)))
      infoJson.picture.nonEmpty shouldEqual true
      infoJson.picture.flatMap(_.head.remoteId) shouldEqual Some(RAssetId(assetId))
    }

    scenario("deserialize user info without service") {
      import IntegrationsClientSpec._

      val infoJson = UserInfo.Decoder(new JSONObject(userInfoWithoutService))

      infoJson.service shouldEqual None
      infoJson.picture.nonEmpty shouldEqual true
      infoJson.picture.flatMap(_.head.remoteId) shouldEqual Some(RAssetId(assetId))
    }
  }

}

object IntegrationsClientSpec {

  val providerId0 = "19c092cb-27cf-42f9-812e-a10de4f2dcae"
  val providerResponse0 =
    s"""
       |{
       |  "id": "$providerId0",
       |  "name": "Wire Swiss GmbH",
       |  "summary": "summary",
       |  "email": "support@wire.com",
       |  "url": "https://wire.com/",
       |  "description": "The Wire Team"
       | }
    """.stripMargin

  val providerId1 = "5671b6be-f958-4b85-aecc-a5fcffa856fa"
  val providerResponse1 =
    s"""
       |{
       |  "id": "$providerId1",
       |  "name": "Collections Corp",
       |  "summary": "summary",
       |  "email": "support@coco.com",
       |  "url": "https://coco.com/",
       |  "description": "The Collections Corp providing collectionsbot"
       | }
      """.stripMargin

  val providerId2 = "6ca9ae2b-b6ac-4e9b-a868-d648d51787a3"
  val providerResponse2 =
    s"""
       |{
       |  "id": "$providerId2",
       |  "name": "Echo Company",
       |  "summary": "summary",
       |  "email": "support@echo.com",
       |  "url": "https://echo.com/",
       |  "description": "Echo Company providing high-quality echo"
       | }
      """.stripMargin

  val providerId3 = "fd6ffe9f-d943-43d7-a691-9bb9e4d1b964"
  val providerResponse3 =
    s"""
       |{
       |  "id": "$providerId3",
       |  "name": "blah blah",
       |  "summary": "summary",
       |  "email": "support@blah.com",
       |  "url": "https://blah.com/",
       |  "description": "blah blah blah"
       | }
      """.stripMargin

  val providers = Map(
    providerId0 -> providerResponse0,
    providerId1 -> providerResponse1,
    providerId2 -> providerResponse2,
    providerId3 -> providerResponse3
  )

  val integrationId1 = "07653181-7c72-4a3a-8e76-39fcbf27fd17"
  val integrationName1 = "collectionsbot"
  val integrationResponse1 =
    s"""
       |    {
       |      "assets" : [
       |
      |      ],
       |      "provider" : "$providerId1",
       |      "enabled" : true,
       |      "id" : "$integrationId1",
       |      "description" : "Helps you fill your library",
       |      "name" : "$integrationName1",
       |      "tags" : [
       |        "tutorial"
       |      ]
       |    }
    """.stripMargin

  val integrationId2 = "748bda63-7783-42d4-80c2-030e3daef5c7"
  val integrationName2 = "Echo"
  val integrationResponse2 =
    s"""
       |{
       |      "assets" : [
       |
      |      ],
       |      "provider" : "$providerId2",
       |      "enabled" : true,
       |      "id" : "$integrationId2",
       |      "description" : "Echo",
       |      "name" : "$integrationName2",
       |      "tags" : [
       |        "tutorial"
       |      ]
       |    }
    """.stripMargin

  val integrationId3 = "f21ef724-64cc-45e3-b78d-d1b18a7c02a5"
  val integrationName3 = "Echo_stage"
  val integrationResponse3 =
    s"""
       |{
       |      "assets" : [
       |
       |      ],
       |      "provider" : "$providerId3",
       |      "enabled" : true,
       |      "id" : "$integrationId3",
       |      "description" : "blah",
       |      "name" : "$integrationName3",
       |      "tags" : [
       |        "tutorial"
       |      ]
       |    }
    """.stripMargin

  val integrationNames = Map(
    integrationName1 -> integrationResponse1,
    integrationName2 -> integrationResponse2,
    integrationName3 -> integrationResponse3
  )

  val integrationIds = Map(
    (providerId1, integrationId1) -> integrationResponse1,
    (providerId2, integrationId2) -> integrationResponse2,
    (providerId3, integrationId3) -> integrationResponse3
  )

  private val startWithRegex = """.*start=([A-Za-z0-9_]+)""".r

  def searchIntegrationsJson(path: String): Option[JSONObject] =
    (path match {
      case startWithRegex(startWith) => Some(startWith)
      case _ => Some("")
    }).map(startWith => {

      val resps = integrationNames.flatMap {
        case (name, jsonStr) if name.startsWith(startWith) => Some(jsonStr)
        case _ => None
      }.mkString(",")

      val str =
        s"""
           |{
           |  "has_more" : false,
           |  "services" : [
           |    $resps
           |  ]
           |}
     """.stripMargin

      new JSONObject(str)
    })

  private val providerIdRegex = """.*/providers/([A-Za-z0-9\\-]+)""".r

  def getProviderJson(path: String): Option[JSONObject] =
    (path match {
      case providerIdRegex(pId) => Some(pId)
      case _ => None
    }).flatMap(pId => providers.get(pId)).map(new JSONObject(_))

  private val integrationIdRegex = """.*/providers/([A-Za-z0-9\\-]+)/services/([A-Za-z0-9\\-]+)""".r

  def getIntegrationJson(path: String): Option[JSONObject] = path match {
    case integrationIdRegex(pId, iId) if integrationIds.contains((pId, iId)) =>
      Option(new JSONObject(integrationIds((pId, iId))))
    case _ => None
  }

  private val addBotRegex = """.*/conversations/([A-Za-z0-9\\-]+)/bots/([A-Za-z0-9\\-]+)""".r

  val conversationId = "0fa26937-1c2b-4aef-8556-da0fb3ec882d"
  val botId = "d7473851-c0bf-4963-8df4-a0ed7c53d124"
  val selfId = "62c2157d-2081-46f0-b8aa-088d7a48142d"
  val assetId = "3-1-cb21f0ce-6bd4-400e-9776-26ad5dd7cd62"
  val assetIdPreview = "3-1-6117cc59-ea0f-456f-95e9-2a1159531789"

  def addBotResponse(convId: String, botId: String) =
    s"""
       | {
       |    "id": "$botId",
       |    "client": "a1b2c3b4d5",
       |    "name": "Otto",
       |    "accent_id": 1,
       |    "assets": [{"type": "image", "key": "$assetId"}],
       |    "event": {
       |       "type": "conversation.member-join",
       |       "conversation": "$convId",
       |       "from": "$selfId",
       |       "time": "2016-08-16T13:29:14.123Z",
       |       "data": {"user_ids": ["$botId"]}
       |    }
       | }
     """.stripMargin

  def getAddBotJson(path: String): Option[JSONObject] = path match {
    case addBotRegex(convId, botId) => Option(new JSONObject(addBotResponse(convId, botId)))
    case _ => None
  }

  val userInfoWithService =
    s"""
       |{
       |  "service": {
       |    "id": "$integrationId1",
       |    "provider": "$providerId1"
       |  },
       |  "accent_id": 0,
       |  "picture": [],
       |  "name": "Echo",
       |  "id": "$botId",
       |  "assets": [
       |    {
       |      "size": "preview",
       |      "key": "$assetIdPreview",
       |      "type": "image"
       |    },
       |    {
       |      "size": "complete",
       |      "key": "$assetId",
       |      "type": "image"
       |    }
       |  ]
       |}
     """.stripMargin

  val userInfoWithoutService =
    s"""
       |{
       |  "accent_id": 0,
       |  "picture": [],
       |  "name": "Me",
       |  "id": "$selfId",
       |  "assets": [
       |    {
       |      "size": "preview",
       |      "key": "$assetIdPreview",
       |      "type": "image"
       |    },
       |    {
       |      "size": "complete",
       |      "key": "$assetId",
       |      "type": "image"
       |    }
       |  ]
       |}
     """.stripMargin
}
