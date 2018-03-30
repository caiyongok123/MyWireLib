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

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.impl.ErrorResponse
import com.waz.model.{Invitation, TeamInvitation}
import com.waz.service.UserService
import com.waz.service.invitations.InvitationServiceImpl
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.Failure
import com.waz.sync.client.{ConnectionsClient, InvitationClient}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.znet.Response.Status

import scala.concurrent.Future

class InvitationSyncHandler(invitationService: InvitationServiceImpl, userService: UserService, userSync: UsersSyncHandler, client: InvitationClient, connections: ConnectionsClient) {
  import Threading.Implicits.Background

  def postInvitation(i: Invitation): Future[SyncResult] =
    client.postInvitation(i).future.flatMap {
      case Right(Right(c)) =>
        verbose(s"invitation successful: $c")
        invitationService.onInvitationSuccess(i, c.genesis).map(_ => SyncResult.Success)
      case Right(Left(u)) =>
        verbose(s"cannot invite contact ${i.id}; already exists as user $u; sync needed")
        userSync.syncUsers(u) flatMap {
          case SyncResult.Success =>
            connections.loadConnection(u).future flatMap {
              case Right(evt) =>
                invitationService.onInvitationOfRegisteredUser(i, evt).map(_ => SyncResult.Success)
              case Left(e) =>
                error(s"could not load connection to user: $u")
                invitationService.onInvitationFailure(i).map(_ => Failure(Some(e), false))
            }
          case failure =>
            error(s"could not sync user $u (matching contact ${i.id}): $failure")
            invitationService.onInvitationFailure(i).map(_ => Failure(failure.error, false))
        }
      case Left(e) =>
        invitationService.onInvitationFailure(i).map(_ => Failure(Some(e), shouldRetry(e)))
    }

  private def shouldRetry(e: ErrorResponse) = (e.code != Status.Created) && (e.code != Status.SeeOther) && ! e.isFatal

  def postTeamInvitations(invitations: Seq[TeamInvitation]): Future[SyncResult] = {
    CancellableFuture.sequence(invitations.map { i =>
      client.postTeamInvitation(i).map { response =>
        invitationService.onTeamInvitationResponse(i, response)
      }
    }).map(_ => SyncResult.Success).future
  }
}
