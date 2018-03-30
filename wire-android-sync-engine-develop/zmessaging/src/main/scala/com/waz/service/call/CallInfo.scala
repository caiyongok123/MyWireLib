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
package com.waz.service.call

import com.sun.jna.Pointer
import com.waz.api.VideoSendState
import com.waz.api.VideoSendState._
import com.waz.model.{ConvId, GenericMessage, UserId}
import com.waz.service.ZMessaging.clock
import com.waz.service.call.Avs.VideoReceiveState.Stopped
import com.waz.service.call.Avs.{AvsClosedReason, VideoReceiveState}
import com.waz.service.call.CallInfo.CallState.{SelfConnected, SelfJoining}
import com.waz.service.call.CallInfo.{CallState, EndedReason}
import org.threeten.bp.Instant

case class CallInfo(convId:            ConvId,
                    caller:            UserId,
                    state:             Option[CallState]                 = None,
                    prevState:         Option[CallState]                 = None,
                    others:            Set[UserId]                       = Set.empty,
                    maxParticipants:   Int                               = 0, //maintains the largest number of users that were ever in the call (for tracking)
                    muted:             Boolean                           = false,
                    isCbrEnabled:      Boolean                           = false,
                    isVideoCall:       Boolean                           = false,
                    videoSendState:    VideoSendState                    = DONT_SEND,
                    videoReceiveState: VideoReceiveState                 = Stopped,
                    startTime:         Instant                           = clock.instant(), //the time we start/receive a call - always the time at which the call info object was created
                    joinedTime:        Option[Instant]                   = None, //the time the call was joined, if any
                    estabTime:         Option[Instant]                   = None, //the time that a joined call was established, if any
                    endTime:           Option[Instant]                   = None,
                    endReason:         Option[EndedReason]               = None,
                    outstandingMsg:    Option[(GenericMessage, Pointer)] = None) { //Any messages we were unable to send due to conv degradation

  override def toString: String =
    s"""
       |CallInfo:
       | convId:            $convId
       | caller:            $caller
       | state:             $state
       | prevState:         $prevState
       | others:            $others
       | maxParticipants:   $maxParticipants
       | muted:             $muted
       | isCbrEnabled:      $isCbrEnabled
       | isVideoCall:       $isVideoCall
       | videoSendState:    $videoSendState
       | videoReceiveState: $videoReceiveState
       | startTime:         $startTime
       | estabTime:         $estabTime
       | endTime:           $endTime
       | endedReason:       $endReason
       | hasOutstandingMsg: ${outstandingMsg.isDefined}
    """.stripMargin

  def updateState(callState: CallState): CallInfo = {
    val withState = copy(state = Some(callState), prevState = this.state)
    callState match {
      case SelfJoining => withState.copy(joinedTime = Some(clock.instant()))
      case SelfConnected => withState.copy(estabTime = Some(clock.instant()))
      case _ => withState
    }
  }

}

object CallInfo {

  sealed trait CallState

  object CallState {

    case object SelfCalling    extends CallState
    case object OtherCalling   extends CallState
    case object SelfJoining    extends CallState
    case object SelfConnected  extends CallState
    case object Ongoing        extends CallState
  }

  sealed trait EndedReason
  object EndedReason {
    case object SelfEnded extends EndedReason
    case object OtherEnded extends EndedReason
    case object GSMInterrupted extends EndedReason
    case class Dropped(avsReason: AvsClosedReason) extends EndedReason
  }
}
