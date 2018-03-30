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
package com.waz.mocked.connections

import com.waz.api.{IConversation, MockedClientApiSpec}
import com.waz.api.impl.ErrorResponse
import com.waz.mocked.{SystemTimeline, MockBackend}
import com.waz.model.UserData._
import com.waz.model._
import com.waz.testutils.Implicits._
import com.waz.testutils.UpdateSpy
import com.waz.threading.CancellableFuture
import com.waz.znet.ZNetClient._
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}

import scala.concurrent.Promise
import scala.concurrent.duration._

class IncomingConnectionSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with MockBackend with MockedClientApiSpec {
  import DefaultPushBehaviour.Implicit

  lazy val incoming = api.getConversations.getIncomingConversations

  feature("Listing incoming connection requests - without sync") {
    scenario("3 other users request to connect") {
      threeIncomingRequests()
    }

    scenario("ignoring all incoming requests") {
      ignoreAllThreeRequests()
    }
  }

  feature("Listing incoming connection requests - with sync") {
    scenario("3 other users request to connect") {
      syncAction = createConnectionEvent
      threeIncomingRequests()
    }

    scenario("ignoring all incoming requests") {
      ignoreAllThreeRequests()
    }
  }

  def threeIncomingRequests(): Unit = {
    addIncomingConnectionRequest(time = SystemTimeline)
    withDelay(incoming should have size 1)

    addIncomingConnectionRequest(time = SystemTimeline)
    withDelay(incoming should have size 2)

    addIncomingConnectionRequest(time = SystemTimeline)
    withDelay(incoming should have size 3)
  }

  def ignoreAllThreeRequests(): Unit = {
    awaitUi(2.seconds)

    ignore(_.last, 2)
    ignore(_.head, 1)
    ignore(_.last, 0)
  }

  def ignore(select: Vector[IConversation] => IConversation, expect: Int) = {
    val spy = UpdateSpy(incoming)
    select(incoming.asScala).getOtherParticipant.ignoreConnection()
    withDelay {
      spy.numberOfTimesCalled shouldEqual 1
      incoming should have size expect
    }
    awaitUi(1.second)
    spy.numberOfTimesCalled shouldEqual 1
  }

  var syncAction: (UserId, ConnectionStatus) => ErrorOrResponse[Option[UserConnectionEvent]] = (_, _) => never[Either[ErrorResponse, Option[UserConnectionEvent]]]

  override def updateConnection(user: UserId, status: ConnectionStatus): ErrorOrResponse[Option[UserConnectionEvent]] = syncAction(user, status)

  def never[A] = CancellableFuture.lift(Promise[A]().future)
  lazy val createConnectionEvent = (user: UserId, status: ConnectionStatus) => CancellableFuture.successful(Right(Some(UserConnectionEvent(RConvId(user.str), selfUserId, user, None, ConnectionStatus.Ignored, SystemTimeline.next()))))
}
