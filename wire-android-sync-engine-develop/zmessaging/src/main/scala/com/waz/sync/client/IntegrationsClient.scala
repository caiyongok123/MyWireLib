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
import com.waz.ZLog._
import com.waz.model.AssetMetaData.Image
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model._
import com.waz.sync.client.ConversationsClient.ConversationsPath
import com.waz.threading.Threading
import com.waz.utils.{Json, JsonDecoder}
import com.waz.znet.Response.SuccessHttpStatus
import com.waz.znet.ZNetClient.ErrorOrResponse
import com.waz.znet._
import org.json.JSONObject

import scala.util.Try

trait IntegrationsClient {
  def searchIntegrations(startWith: String): ErrorOrResponse[Map[IntegrationData, Option[AssetData]]]
  def getIntegration(providerId: ProviderId, integrationId: IntegrationId): ErrorOrResponse[(IntegrationData, Option[AssetData])]

  def getProvider(id: ProviderId): ErrorOrResponse[ProviderData]
  def addBot(rConvId: RConvId, pId: ProviderId, iId: IntegrationId): ErrorOrResponse[ConversationEvent]
  def removeBot(rConvId: RConvId, botId: UserId): ErrorOrResponse[ConversationEvent]
}

class IntegrationsClientImpl(netClient: ZNetClient) extends IntegrationsClient {
  import IntegrationsClient._
  import Threading.Implicits.Background

  def searchIntegrations(startWith: String) =
    netClient.withErrorHandling("searchIntegrations", Request.Get(integrationsSearchPath(startWith))) {
      case Response(SuccessHttpStatus(), IntegrationsSearchResponse(data), _) => data
    }

  def getIntegration(pId: ProviderId, iId: IntegrationId) =
    netClient.withErrorHandling("getIntegration", Request.Get(integrationPath(pId, iId))) {
      case Response(SuccessHttpStatus(), IntegrationResponse(data, assetData), _) => (data, assetData)
    }

  def getProvider(pId: ProviderId) =
    netClient.withErrorHandling("getProvider", Request.Get(providerPath(pId))) {
      case Response(SuccessHttpStatus(), ProviderResponse(data), _) => data
    }

  def addBot(rConvId: RConvId, pId: ProviderId, iId: IntegrationId) = {
    debug(s"addBot: rConvId: $rConvId, providerId: $pId, integrationId: $iId")
    netClient.withErrorHandling("addBot", Request.Post(s"$ConversationsPath/${rConvId.str}/bots", Json("provider" -> pId.str, "service" -> iId.str))) {
      case Response(SuccessHttpStatus(), AddRemoveBotResponse(data), _) => data
    }
  }

  def removeBot(rConvId: RConvId, botId: UserId) = {
    debug(s"removeBot: convId: $rConvId, botId: $botId")

    import com.waz.znet.ContentEncoder.RequestContentEncoder

    netClient.withErrorHandling("addBot", Request.Delete(s"$ConversationsPath/${rConvId.str}/bots/$botId")) {
      case Response(SuccessHttpStatus(), AddRemoveBotResponse(data), _) => data
    }
  }
}

object IntegrationsClient {
  import JsonDecoder._
  import com.waz.model.ConversationEvent.ConversationEventDecoder

  def apply(netClient: ZNetClient): IntegrationsClient = new IntegrationsClientImpl(netClient)

  val IntegrationsSearchPath = "/services"
  val DefaultTag = "integration"
  val ProvidersPath = "/providers"
  val IntegrationConvPath = "/conversations"

  def integrationsSearchPath(startWith: String): String =
    Request.query(IntegrationsSearchPath, "tags" -> DefaultTag, "start" -> startWith)

  def integrationPath(providerId: ProviderId, integrationId: IntegrationId): String = s"$ProvidersPath/$providerId/services/$integrationId"

  def providerPath(id: ProviderId): String = s"$ProvidersPath/$id"

  object IntegrationsSearchResponse {
    def unapply(resp: ResponseContent): Option[Map[IntegrationData, Option[AssetData]]] = resp match {
      case JsonObjectResponse(js) if js.has("services") =>
        Try(decodeSeq('services)(js, IntegrationDecoder).toMap).toOption
      case response =>
        warn(s"Unexpected response: $response")
        None
    }
  }

  object IntegrationResponse {
    def unapply(resp: ResponseContent): Option[(IntegrationData, Option[AssetData])] = resp match {
      case JsonObjectResponse(js) =>
        Try(IntegrationDecoder(js)).toOption
      case response =>
        warn(s"Unexpected response: $response")
        None
    }
  }

  object ProviderResponse {
    def unapply(resp: ResponseContent): Option[ProviderData] = resp match {
      case JsonObjectResponse(js) => Try(ProviderData.Decoder(js)).toOption
      case response =>
        warn(s"Unexpected response: $response")
        None
    }
  }

  object AddRemoveBotResponse {
    def unapply(resp: ResponseContent): Option[ConversationEvent] = resp match {
      case JsonObjectResponse(js) if js.has("event") => Try(ConversationEventDecoder(js.getJSONObject("event"))).toOption
      case response =>
        warn(s"Unexpected response: $response")
        None
    }
  }

  implicit lazy val IntegrationDecoder: JsonDecoder[(IntegrationData, Option[AssetData])] = new JsonDecoder[(IntegrationData, Option[AssetData])] {
    override def apply(implicit js: JSONObject): (IntegrationData, Option[AssetData]) = {
      val asset = getCompleteAsset
      (IntegrationData(
        decodeId[IntegrationId]('id),
        decodeId[ProviderId]('provider),
        decodeString('name),
        decodeString('summary),
        decodeString('description),
        asset.map(_.id),
        decodeStringSeq('tags),
        decodeBool('enabled)
      ), asset)
    }
  }

  def getCompleteAsset(implicit js: JSONObject): Option[AssetData] = fromArray(js, "assets") flatMap { assets =>
    Seq.tabulate(assets.length())(assets.getJSONObject).map { js =>
      AssetData(
        remoteId = decodeOptRAssetId('key)(js),
        metaData = Some(AssetMetaData.Image(Dim2(0, 0), Image.Tag(decodeString('size)(js))))
      )
    }.collectFirst { case a@AssetData.IsImageWithTag(Tag.Medium) => a } //discard preview
  }

  private def fromArray(js: JSONObject, name: String) = Try(js.getJSONArray(name)).toOption.filter(_.length() > 0)

}
