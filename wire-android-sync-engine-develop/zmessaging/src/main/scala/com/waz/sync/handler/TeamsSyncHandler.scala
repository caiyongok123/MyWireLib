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
package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse
import com.waz.model._
import com.waz.service.teams.TeamsService
import com.waz.sync.SyncResult
import com.waz.sync.client.TeamsClient
import com.waz.threading.Threading

import scala.concurrent.Future
import scala.util.control.NoStackTrace

trait TeamsSyncHandler {
  def syncTeam(): Future[SyncResult]
  def syncMember(id: UserId): Future[SyncResult]
}

class TeamsSyncHandlerImpl(teamId: Option[TeamId], client: TeamsClient, service: TeamsService) extends TeamsSyncHandler {

  import Threading.Implicits.Background

  override def syncTeam(): Future[SyncResult] = teamId match {
    case Some(id) => client.getTeamData(id).future.flatMap {
      case Right(data) => client.getTeamMembers(id).future.flatMap {
        case Left(errorResponse) => Future.successful(SyncResult(errorResponse))
        case Right(members) =>  service.onTeamSynced(data, members).map(_ => SyncResult.Success)
      }
      case Left(error) => Future.successful(SyncResult(error))
    }
    case None => Future.successful(SyncResult.Success)
  }

  override def syncMember(uId: UserId) = teamId match {
    case Some(tId) =>
      client.getPermissions(tId, uId).future.flatMap {
        case Right(p) => service.onMemberSynced(uId, p).map(_ => SyncResult.Success)
        case Left(e)  => Future.successful(SyncResult(e))
      }
    case _ => Future.successful(SyncResult.Success)
  }

}

object TeamsSyncHandler {

  def apply(teamId: Option[TeamId], client: TeamsClient, service: TeamsService): TeamsSyncHandler = new TeamsSyncHandlerImpl(teamId, client, service)

  case class SyncException(msg: String, err: ErrorResponse) extends Exception(msg) with NoStackTrace
}
