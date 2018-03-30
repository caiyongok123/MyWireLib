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
package com.waz.service

import com.waz.ZLog.LogTag
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.content.Database
import com.waz.model._
import com.waz.service.AccountsService.InForeground
import com.waz.service.conversation.ConversationsService
import com.waz.service.invitations.InvitationServiceImpl
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.InvitationClient
import com.waz.sync.client.InvitationClient.ConfirmedTeamInvitation
import com.waz.threading.CancellableFuture
import com.waz.utils.BiRelation
import com.waz.utils.events.Signal
import com.waz.utils.wrappers.DB
import com.waz.znet.ZNetClient.ErrorOrResponse
import com.waz.znet.{Request, Response, ZNetClient}
import org.threeten.bp.Instant

import scala.concurrent.{ExecutionContext, Future}

class InvitationServiceSpec extends AndroidFreeSpec{
  import com.waz.threading.Threading.Implicits.Background

  private val teamId = TeamId("tid")

  feature("Inviting people to join team") {
    scenario("Inviting email adds an invitation to the list") {
      val email = EmailAddress("whisker_pants@wire.com")
      val name = "Admin"

      val confirmedTeamInvitation = ConfirmedTeamInvitation(InvitationId("iid"), email, Instant.EPOCH, teamId)

      (znet.chainedWithErrorHandling(_: String, _: Request[TeamInvitation])(_: PartialFunction[Response, ErrorOrResponse[ConfirmedTeamInvitation]])(_: ExecutionContext))
        .expects(*, *, *, *)
        .once()
        .returning(CancellableFuture.successful(Right(ConfirmedTeamInvitation(InvitationId("iid"), email, Instant.EPOCH, teamId))))

      val service = getInvitationService

      result(service.inviteToTeam(email, Some(name)).future.flatMap { _ =>
        service.invitedToTeam.head.map {
          _.get(TeamInvitation(teamId, email, name, None)).flatten.contains(Right(confirmedTeamInvitation))
        }
      }).shouldBe(true)
    }
  }

  private val znet          = mock[ZNetClient]

  private val database      = mock[Database]
  private val userService   = mock[UserService]
  private val connections   = mock[ConnectionService]
  private val contacts      = mock[ContactsService]
  private val conversations = mock[ConversationsService]
  private val sync          = mock[SyncServiceHandle]
  private val timeouts      = new Timeouts()
  private val client        = new InvitationClient(znet)

  def getInvitationService: InvitationServiceImpl = {
    (accounts.accountState _).expects(*).anyNumberOfTimes().onCall { id: AccountId =>
      accountStates.map(_.getOrElse(id, InForeground))
    }
    (database.read[Set[ContactId]](_ : (DB) => Set[ContactId])).expects(*).anyNumberOfTimes().returning(Future.successful(Set.empty))
    (database.apply[Unit](_: (DB) => Unit)(_: LogTag)).expects(*, *).anyNumberOfTimes().returning(CancellableFuture.successful(()))

    (contacts.contactsOnWire _).expects().anyNumberOfTimes().returning(Signal.const(BiRelation.empty))

    new InvitationServiceImpl(database, userService, connections, contacts, conversations, sync, timeouts, client, Some(teamId))
  }

}
