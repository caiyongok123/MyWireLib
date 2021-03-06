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
package com.waz.service.tracking

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.call.CallInfo
import com.waz.service.call.CallInfo.CallState.{OtherCalling, SelfCalling, SelfConnected, SelfJoining}
import com.waz.service.tracking.ContributionEvent.fromMime
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.RichInstant
import com.waz.utils.events.{EventContext, EventStream}

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.Try

trait TrackingService {
  def events: EventStream[(Option[ZMessaging], TrackingEvent)]

  def track(event: TrackingEvent, accountId: Option[AccountId] = None): Unit

  def loggedOut(reason: String, accountId: AccountId): Unit = track(LoggedOutEvent(reason), Some(accountId))

  def optIn(): Unit = track(OptInEvent)
  def optOut(): Unit = track(OptOutEvent)

  def contribution(action: ContributionEvent.Action): Unit
  def assetContribution(assetId: AssetId, accountId: AccountId): Unit

  def exception(e: Throwable, description: String, accountId: Option[AccountId] = None)(implicit tag: LogTag): Unit
  def crash(e: Throwable): Unit

  def integrationAdded(integrationId: IntegrationId, convId: ConvId, method: IntegrationAdded.Method): Unit
  def integrationRemoved(integrationId: IntegrationId): Unit

  def trackCallState(account: AccountId, callInfo: CallInfo): Unit
}

class TrackingServiceImpl(zmsProvider: TrackingService.ZmsProvider = TrackingService.defaultZmsProvider) extends TrackingService {
  import TrackingService._

  val events = EventStream[(Option[ZMessaging], TrackingEvent)]()

  override def track(event: TrackingEvent, accountId: Option[AccountId] = None): Unit = (accountId match {
    case Some(id) => zmsProvider(id)
    case _        => zmsProvider.current
  }).map { events ! _ -> event }

  override def contribution(action: ContributionEvent.Action): Unit = zmsProvider.current.map {
    case Some(z) =>
      for {
        Some(convId) <- z.convsStats.selectedConversationId.head
        Some(conv)   <- z.convsStorage.get(convId)
        userIds      <- z.membersStorage.activeMembers(convId).head
        users        <- z.users.getUsers(userIds.toSeq)
        isGroup      <- z.conversations.isGroupConversation(convId)
      } {
        events ! Option(z) -> ContributionEvent(action, isGroup, conv.ephemeral, users.exists(_.isWireBot), !conv.isTeamOnly, conv.isMemberFromTeamGuest(z.teamId))
      }
    case _ => //
  }

  override def exception(e: Throwable, description: String, accountId: Option[AccountId] = None)(implicit tag: LogTag): Unit = {
    val cause = rootCause(e)
    track(ExceptionEvent(cause.getClass.getSimpleName, details(cause), description, throwable = Some(e))(tag), accountId)
  }

  override def crash(e: Throwable): Unit = {
    val cause = rootCause(e)
    track(CrashEvent(cause.getClass.getSimpleName, details(cause), throwable = Some(e)))
  }

  @tailrec
  private def rootCause(e: Throwable): Throwable = Option(e.getCause) match {
    case Some(cause) => rootCause(cause)
    case None => e
  }

  private def details(rootCause: Throwable) =
    Try(rootCause.getStackTrace).toOption.filter(_.nonEmpty).map(_(0).toString).getOrElse("")

  override def assetContribution(assetId: AssetId, accountId: AccountId): Unit = zmsProvider(accountId).map {
    case Some(z) =>
      for {
        Some(msg)   <- z.messagesStorage.get(MessageId(assetId.str))
        Some(conv)  <- z.convsContent.convById(msg.convId)
        Some(asset) <- z.assetsStorage.get(assetId)
        userIds     <- z.membersStorage.activeMembers(conv.id).head
        users       <- z.users.getUsers(userIds.toSeq)
        isGroup     <- z.conversations.isGroupConversation(conv.id)
      } yield track(ContributionEvent(fromMime(asset.mime), isGroup, msg.ephemeral, users.exists(_.isWireBot), !conv.isTeamOnly, conv.isMemberFromTeamGuest(z.teamId)), Some(accountId))
    case _ => //
  }

  override def integrationAdded(integrationId: IntegrationId, convId: ConvId, method: IntegrationAdded.Method) = zmsProvider.current.map {
    case Some(z) =>
      for {
        userIds <- z.membersStorage.activeMembers(convId).head
        users   <- z.users.getUsers(userIds.toSeq)
        (bots, people) = users.partition(_.isWireBot)
      } yield track(IntegrationAdded(integrationId, people.size, bots.filterNot(_.integrationId.contains(integrationId)).size + 1, method))
    case None =>
  }

  def integrationRemoved(integrationId: IntegrationId) = track(IntegrationRemoved(integrationId))

  override def trackCallState(account: AccountId, callInfo: CallInfo) =
    ((callInfo.prevState, callInfo.state) match {
      case (None, Some(SelfCalling))      => Some("initiated")
      case (None, Some(OtherCalling))     => Some("received")
      case (Some(_), Some(SelfJoining))   => Some("joined")
      case (Some(_), Some(SelfConnected)) => Some("established")
      case (Some(_), None)                => Some("ended")
      case _ =>
        warn(s"Unexpected call state change: ${callInfo.prevState} => ${callInfo.state}, not tracking")
        None
    }).foreach { eventName =>
      for {
        Some(z)  <- zmsProvider(account)
        isGroup  <- z.conversations.isGroupConversation(callInfo.convId)
        memCount <- z.membersStorage.activeMembers(callInfo.convId).map(_.size).head
        withService <- z.conversations.isWithService(callInfo.convId)
        withGuests  <-
          if (isGroup)
            z.convsStorage.get(callInfo.convId).collect { case Some(conv) => !conv.isTeamOnly }.map(Some(_))
          else Future.successful(None)
        uiActive <- ZMessaging.currentGlobal.lifecycle.uiActive.head
      } yield
        track(new CallingEvent(
          eventName,
          callInfo.isVideoCall,
          isGroup,
          memCount,
          withService,
          uiActive,
          callInfo.caller != z.selfUserId,
          withGuests,
          Option(callInfo.maxParticipants).filter(_ > 0),
          callInfo.estabTime.map(est => callInfo.joinedTime.getOrElse(est).until(est)),
          callInfo.endTime.map(end => callInfo.estabTime.getOrElse(end).until(end)),
          callInfo.endReason
        ))
    }
}

object TrackingService {
  implicit val dispatcher = new SerialDispatchQueue(name = "TrackingService")
  private[waz] implicit val ec: EventContext = EventContext.Global

  trait ZmsProvider {
    def current: Future[Option[ZMessaging]]
    def apply(accountId: AccountId): Future[Option[ZMessaging]]
  }

  val defaultZmsProvider = new ZmsProvider {
    override def apply(accountId: AccountId): Future[Option[ZMessaging]] = ZMessaging.accountsService.flatMap(_.zms(accountId).head)
    override def current: Future[Option[ZMessaging]] = ZMessaging.accountsService.flatMap(_.activeZms.head)
  }

  def exception(e: Throwable, description: String, accountId: Option[AccountId] = None)(implicit tag: LogTag): Unit =
    ZMessaging.globalModule.map(_.trackingService.exception(e, description, accountId)(tag))

  def track(event: TrackingEvent, accountId: Option[AccountId] = None): Unit =
    ZMessaging.globalModule.map(_.trackingService.track(event, accountId))

  trait NoReporting { self: Throwable => }

}
