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
import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.model.ConversationData.{ConversationType, Link}
import com.waz.model._
import com.waz.sync.client.ConversationsClient.ConversationResponse.{ConversationIdsResponse, ConversationsResult}
import com.waz.threading.Threading
import com.waz.utils.JsonEncoder.{encodeAccess, encodeAccessRole}
import com.waz.utils.{Json, JsonDecoder, JsonEncoder, returning}
import com.waz.znet.ContentEncoder.JsonContentEncoder
import com.waz.znet.Response.{HttpStatus, Status, SuccessHttpStatus}
import com.waz.znet.ZNetClient._
import com.waz.znet._
import org.json
import org.json.{JSONArray, JSONObject}
import org.threeten.bp.Instant

import scala.concurrent.duration._
import scala.util.control.NonFatal

class ConversationsClient(netClient: ZNetClient) {
  import Threading.Implicits.Background
  import com.waz.sync.client.ConversationsClient._

  def loadConversationIds(start: Option[RConvId] = None): ErrorOrResponse[ConversationIdsResponse] =
    netClient.withErrorHandling(s"loadConversationIds(start = $start)", Request.Get(conversationIdsQuery(start))) {
      case Response(SuccessHttpStatus(), ConversationIdsResponse(ids, hasMore), _) => ConversationIdsResponse(ids, hasMore)
    }

  def loadConversations(start: Option[RConvId] = None, limit: Int = ConversationsPageSize): ErrorOrResponse[ConversationsResult] =
    netClient.withErrorHandling(s"loadConversations(start = $start)", Request.Get(conversationsQuery(start, limit))) {
      case Response(SuccessHttpStatus(), ConversationsResult(conversations, hasMore), _) => ConversationsResult(conversations, hasMore)
    }

  def loadConversations(ids: Seq[RConvId]): ErrorOrResponse[Seq[ConversationResponse]] =
    netClient.withErrorHandling(s"loadConversations(ids = $ids)", Request.Get(conversationsQuery(ids = ids))) {
      case Response(SuccessHttpStatus(), ConversationsResult(conversations, _), _) => conversations
    }

  def loadConversation(id: RConvId): ErrorOrResponse[ConversationResponse] =
    netClient.withErrorHandling(s"loadConversation($id)", Request.Get(s"$ConversationsPath/$id")) {
      case Response(SuccessHttpStatus(), ConversationsResult(Seq(conversation), _), _) => conversation
    }

  def postName(convId: RConvId, name: String): ErrorOrResponse[Option[RenameConversationEvent]] =
    netClient.withErrorHandling("postName", Request.Put(s"$ConversationsPath/$convId", Json("name" -> name))) {
      case Response(SuccessHttpStatus(), EventsResponse(event: RenameConversationEvent), _) => Some(event)
    }

  def postConversationState(convId: RConvId, state: ConversationState): ErrorOrResponse[Boolean] =
    netClient.withErrorHandling("postConversationState", Request.Put(s"$ConversationsPath/$convId/self", state)(ConversationState.StateContentEncoder)) {
      case Response(SuccessHttpStatus(), _, _) => true
    }

  def postMemberJoin(conv: RConvId, members: Set[UserId]): ErrorOrResponse[Option[MemberJoinEvent]] =
    netClient.withErrorHandling("postMemberJoin", Request.Post(s"$ConversationsPath/$conv/members", Json("users" -> Json(members)))) {
      case Response(SuccessHttpStatus(), EventsResponse(event: MemberJoinEvent), _) => Some(event)
      case Response(HttpStatus(Status.NoResponse, _), EmptyResponse, _) => None
    }

  def postMemberLeave(conv: RConvId, user: UserId): ErrorOrResponse[Option[MemberLeaveEvent]] =
    netClient.withErrorHandling("postMemberLeave", Request.Delete(s"$ConversationsPath/$conv/members/$user")) {
      case Response(SuccessHttpStatus(), EventsResponse(event: MemberLeaveEvent), _) => Some(event)
      case Response(HttpStatus(Status.NoResponse, _), EmptyResponse, _) => None
    }

  def createLink(conv: RConvId): ErrorOrResponse[Link] =
    netClient.withErrorHandling("createLink", Request.Post(s"$ConversationsPath/$conv/code", {}, timeout = 3.seconds)) {
      case Response(HttpStatus(Status.Success, _), JsonObjectResponse(js), _) if js.has("uri") => Link(js.getString("uri"))
      case Response(HttpStatus(Status.Created, _), JsonObjectResponse(js), _) if js.getJSONObject("data").has("uri") => Link(js.getJSONObject("data").getString("uri"))
    }

  def removeLink(conv: RConvId): ErrorOrResponse[Unit] =
    netClient.withErrorHandling("removeLink", Request.Delete(s"$ConversationsPath/$conv/code", timeout = 3.seconds)) {
      case Response(SuccessHttpStatus(), _, _) => // nothing to return
    }

  def getLink(conv: RConvId): ErrorOrResponse[Option[Link]] =
    netClient.withErrorHandling("getLink", Request.Get(s"$ConversationsPath/$conv/code")) {
      case Response(SuccessHttpStatus(), JsonObjectResponse(js), _) if js.has("uri") => Option(Link(js.getString("uri")))
      case Response(HttpStatus(Status.NotFound, "no-conversation-code"), _, _) => None
    }

  def postAccessUpdate(conv: RConvId, access: Set[Access], accessRole: AccessRole): ErrorOrResponse[Unit] =
    netClient.withErrorHandling("postAccessUpdate",
      Request.Put(
        accessUpdatePath(conv),
        Json(
          "access" -> encodeAccess(access),
          "access_role" -> encodeAccessRole(accessRole)),
        timeout = 3.seconds)) {
      case Response(SuccessHttpStatus(), _, _) | Response(HttpStatus(Status.NoResponse, _), _, _) => //no op
    }

  def postConversation(users: Set[UserId], name: Option[String] = None, team: Option[TeamId], access: Set[Access], accessRole: AccessRole): ErrorOrResponse[ConversationResponse] = {
    debug(s"postConversation($users, $name)")
    val payload = JsonEncoder { o =>
      o.put("users", Json(users))
      name.foreach(o.put("name", _))
      team.foreach(t => o.put("team", returning(new json.JSONObject()) { o =>
        o.put("teamid", t.str)
        o.put("managed", false)
      }))
      o.put("access", encodeAccess(access))
      o.put("access_role", encodeAccessRole(accessRole))
    }
    netClient.withErrorHandling("postConversation", Request.Post(ConversationsPath, payload)) {
      case Response(SuccessHttpStatus(), ConversationsResult(Seq(conv), _), _) => conv
    }
  }
}

object ConversationsClient {
  val ConversationsPath = "/conversations"
  val ConversationIdsPath = "/conversations/ids"
  val ConversationsPageSize = 100
  val ConversationIdsPageSize = 1000
  val IdsCountThreshold = 32

  def accessUpdatePath(id: RConvId) = s"$ConversationsPath/${id.str}/access"

  def conversationsQuery(start: Option[RConvId] = None, limit: Int = ConversationsPageSize, ids: Seq[RConvId] = Nil): String = {
    val args = (start, ids) match {
      case (None, Nil) =>   Seq("size" -> limit)
      case (Some(id), _) => Seq("size" -> limit, "start" -> id.str)
      case _ =>             Seq("ids" -> ids.mkString(","))
    }
    Request.query(ConversationsPath, args: _*)
  }

  def conversationIdsQuery(start: Option[RConvId]): String =
    Request.query(ConversationIdsPath, ("size", ConversationIdsPageSize) :: start.toList.map("start" -> _.str) : _*)

  case class ConversationResponse(conversation: ConversationData, members: Seq[ConversationMemberData])

  object ConversationResponse {
    import com.waz.utils.JsonDecoder._

    def memberDecoder(convId: ConvId) = new JsonDecoder[Option[ConversationMemberData]] {
      override def apply(implicit js: JSONObject) = Some(ConversationMemberData('id, convId))
    }

    def conversationData(js: JSONObject, self: JSONObject) = {
      val (creator, name, team, id, convType, lastEventTime, access, accessRole, link) = {
        implicit val jsObj = js
        (
          decodeUserId('creator),
          decodeOptString('name),
          decodeOptId[TeamId]('team),
          decodeRConvId('id),
          ConversationType(decodeInt('type)),
          decodeISOInstant('last_event_time),
          decodeAccess('access),
          decodeOptAccessRole('access_role),
          decodeOptString('link).map(Link)
        )
      }
      val state = ConversationState.Decoder(self)
      //TODO Teams: how do we tell if a conversation is managed, currently defaulting to false
      val isManaged = team.map(_ => false)

      ConversationData(
        ConvId(id.str),
        id,
        name.filterNot(_.isEmpty),
        creator,
        convType,
        team,
        isManaged,
        lastEventTime,
        isActive = true,
        Instant.EPOCH,
        state.muted.getOrElse(false),
        state.muteTime.getOrElse(lastEventTime),
        state.archived.getOrElse(false),
        state.archiveTime.getOrElse(lastEventTime),
        access = access,
        accessRole = accessRole,
        link = link
      )
    }

    implicit lazy val Decoder: JsonDecoder[ConversationResponse] = new JsonDecoder[ConversationResponse] {
      override def apply(implicit js: JSONObject): ConversationResponse = {
        debug(s"decoding response: $js")
        val members = js.getJSONObject("members")
        val self = members.getJSONObject("self")
        val conversation = conversationData(js, self)
        ConversationResponse(conversation, array(members.getJSONArray("others"))(memberDecoder(conversation.id)).flatten)
      }
    }

    case class ConversationsResult(conversations: Seq[ConversationResponse], hasMore: Boolean)
    
    object ConversationsResult {

      def unapply(response: ResponseContent): Option[(List[ConversationResponse], Boolean)] = try {
        response match {
          case JsonObjectResponse(js) if js.has("conversations") =>
            Some((array[ConversationResponse](js.getJSONArray("conversations")).toList, decodeBool('has_more)(js)))
          case JsonArrayResponse(js) => Some((array[ConversationResponse](js).toList, false))
          case JsonObjectResponse(js) => Some((List(Decoder(js)), false))
          case _ => None
        }
      } catch {
        case NonFatal(e) =>
          warn(s"couldn't parse conversations response: $response", e)
          warn("json decoding failed", e)
          None
      }
    }

    case class ConversationIdsResponse(ids: Seq[RConvId], hasMore: Boolean)

    object ConversationIdsResponse {

      val idExtractor = { (arr: JSONArray, i: Int) => RConvId(arr.getString(i)) }

      def unapply(response: ResponseContent): Option[(Seq[RConvId], Boolean)] = try {
        response match {
          case JsonObjectResponse(js) if js.has("conversations") => Some((array[RConvId](js.getJSONArray("conversations"), idExtractor), decodeBool('has_more)(js)))
          case JsonArrayResponse(js) => Some((array[RConvId](js, idExtractor), false))
          case _ => None
        }
      } catch {
        case NonFatal(e) =>
          warn(s"couldn't parse conversations response: $response", e)
          None
      }
    }
  }

  object EventsResponse {
    import com.waz.utils.JsonDecoder._

    def unapplySeq(response: ResponseContent): Option[List[ConversationEvent]] = try {
      response match {
        case JsonObjectResponse(js) if js.has("events") => Some(array[ConversationEvent](js.getJSONArray("events")).toList)
        case JsonArrayResponse(js) => Some(array[ConversationEvent](js).toList)
        case JsonObjectResponse(js) => Some(List(implicitly[JsonDecoder[ConversationEvent]].apply(js)))
        case _ => None
      }
    } catch {
      case NonFatal(e) =>
        warn(s"couldn't parse events response $response", e)
        None
    }
  }
}
