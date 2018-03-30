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
import com.waz.ZLog.warn
import com.waz.model.AccountData.PermissionsMasks
import com.waz.model._
import com.waz.sync.client.TeamsClient.TeamBindingResponse
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.JsonDecoder
import com.waz.znet.Response.SuccessHttpStatus
import com.waz.znet.ZNetClient.ErrorOrResponse
import com.waz.znet._
import org.json.JSONObject

import scala.util.Try

trait TeamsClient {
  def getTeamMembers(id: TeamId): ErrorOrResponse[Map[UserId, PermissionsMasks]]
  def getTeamData(id: TeamId): ErrorOrResponse[TeamData]

  def findSelfTeam(start: Option[TeamId] = None): ErrorOrResponse[Option[TeamData]]
  def getTeams(start: Option[TeamId]): ErrorOrResponse[TeamBindingResponse]
  def getPermissions(teamId: TeamId, userId: UserId): ErrorOrResponse[PermissionsMasks]
}

class TeamsClientImpl(zNetClient: ZNetClient) extends TeamsClient {
  import TeamsClient._
  import Threading.Implicits.Background

  override def getTeamMembers(id: TeamId) =
    zNetClient.withErrorHandling("loadTeamMembers", Request.Get(teamMembersPath(id))) {
      case Response(SuccessHttpStatus(), TeamMembersResponse(members), _) => members
    }

  override def getTeamData(id: TeamId) =
    zNetClient.withErrorHandling("loadTeamData", Request.Get(teamPath(id))) {
      case Response(SuccessHttpStatus(), TeamResponse(data), _) => data
    }

  override def findSelfTeam(start: Option[TeamId] = None): ErrorOrResponse[Option[TeamData]] = getTeams(start).flatMap {
    case Left(err) => CancellableFuture.successful(Left(err))
    case Right(TeamBindingResponse(teams, hasMore)) =>
      teams.find(_._2).map(_._1) match {
        case Some(teamId) => CancellableFuture.successful(Right(Some(teamId)))
        case None if hasMore => findSelfTeam(teams.lastOption.map(_._1.id))
        case None => CancellableFuture.successful(Right(None))
      }
  }

  override def getTeams(start: Option[TeamId]) =
    zNetClient.withErrorHandling("loadAllTeams", Request.Get(teamsPaginatedQuery(start))) {
      case Response(SuccessHttpStatus(), TeamBindingResponse(teams, hasMore), _) => TeamBindingResponse(teams, hasMore)
    }

  override def getPermissions(teamId: TeamId, userId: UserId) =
    zNetClient.withErrorHandling(s"getMember:$userId", Request.Get(memberPath(teamId, userId))) {
      case Response(SuccessHttpStatus(), TeamMemberResponse(_, p), _) => p
    }
}

object TeamsClient {

  def apply(zNetClient: ZNetClient): TeamsClient = new TeamsClientImpl(zNetClient)

  val TeamsPath = "/teams"
  val TeamsPageSize = 100

  def teamMembersPath(id: TeamId) = s"$TeamsPath/${id.str}/members"

  def teamsPaginatedQuery(start: Option[TeamId]): String =
    Request.query(TeamsPath, ("size", TeamsPageSize) :: start.toList.map("start" -> _.str) : _*)

  def teamsBatchQuery(ids: Set[TeamId]): String =
    Request.query(TeamsPath, ("ids", ids.mkString(",")))

  def teamPath(id: TeamId): String = s"$TeamsPath/${id.str}"

  def memberPath(teamId: TeamId, userId: UserId): String = s"${teamMembersPath(teamId)}/${userId.str}"

  import JsonDecoder._

  case class TeamBindingResponse(teams: Seq[(TeamData, Boolean)], hasMore: Boolean)

  object TeamBindingResponse {
    def unapply(response: ResponseContent): Option[(Seq[(TeamData, Boolean)], Boolean)] =
      response match {
        case JsonObjectResponse(js) if js.has("teams") =>
          Try(decodeSeq('teams)(js, TeamData.TeamBindingDecoder).map( t => t._1 -> t._2 ), decodeOptBoolean('has_more)(js).getOrElse(false)).toOption
        case _ =>
          warn(s"Unexpected response: $response")
          None
      }
  }

  object TeamMemberResponse {
    lazy val MemberDecoder = new JsonDecoder[(UserId, PermissionsMasks)] {
      override def apply(implicit js: JSONObject) = (decodeId[UserId]('user), (decodeInt('self)('permissions), decodeInt('copy)('permissions)))
    }

    def unapply(response: ResponseContent): Option[(UserId, PermissionsMasks)] =
      response match {
        case JsonObjectResponse(js) => Try(TeamMemberResponse.MemberDecoder(js)).toOption
        case _ =>
          warn(s"Unexpected response: $response")
          None
      }
  }

  object TeamMembersResponse {
    def unapply(response: ResponseContent): Option[Map[UserId, PermissionsMasks]] = {
      response match {
        case JsonObjectResponse(js) if js.has("members") =>
          Try(decodeSet('members)(js, TeamMemberResponse.MemberDecoder)).toOption.map(_.toMap)
        case _ =>
          warn(s"Unexpected response: $response")
          None
      }
    }
  }

  object TeamResponse {
    def unapply(response: ResponseContent): Option[TeamData] = response match {
      case JsonObjectResponse(js) => Try(TeamData.TeamBindingDecoder(js)._1).toOption
      case _ => None
    }
  }
}
