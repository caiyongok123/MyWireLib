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
package com.waz.mocked.messages

import com.waz.RobolectricUtils
import com.waz.api._
import com.waz.api.impl.ErrorResponse
import com.waz.mocked.{MockBackend, SystemTimeline}
import com.waz.model._
import com.waz.service.Timeouts
import com.waz.sync.client.MessagesClient.OtrMessage
import com.waz.sync.client.OtrClient.{ClientMismatch, MessageResponse}
import com.waz.threading.CancellableFuture
import com.waz.threading.Threading.Implicits.Background
import com.waz.znet.ZNetClient.ErrorOrResponse
import org.scalatest._
import com.waz.ZLog.ImplicitTag._
import com.waz.testutils.Implicits._

import scala.concurrent.duration._

class PostMessageSpec extends FeatureSpec with Matchers with Inside with BeforeAndAfter with MockedClientApiSpec with MockBackend with RobolectricUtils { test =>
  import DefaultPushBehaviour.Implicit

  val userId = UserId()
  lazy val convId = addConnection(userId).convId
  lazy val convs  = api.getConversations
  lazy val conv   = convs.getConversation(userId.str)

  var sendingTimeout = 30.seconds

  override lazy val timeouts: Timeouts = new Timeouts() {
    override val messages: Messages = new Messages() {
      override def sendingTimeout: Timeout = test.sendingTimeout
    }
  }

  val successResponse = { msg: OtrMessage => Right(MessageResponse.Success(ClientMismatch(SystemTimeline.next()))) }

  var postRequests = Seq.empty[OtrMessage]
  var postMessageResponse: OtrMessage => Either[ErrorResponse, MessageResponse] = successResponse

  before {
    zmessaging.network.networkMode ! NetworkMode.WIFI
    postRequests = Nil
    postMessageResponse = successResponse
    sendingTimeout = 30.seconds
  }

  scenario("init") {
    addMessageEvents(convId, count = 5)

    withDelay {
      convs should not be empty
      listMessages(conv.id) should have size 8
    }
  }

  feature("Message retrying") {
    val serverError = { _: OtrMessage => Left(ErrorResponse(500, "server error", "error")) }

    scenario("Retry when post fails with server error") {
      postMessageResponse = serverError
      zmessaging.convsUi.sendMessage(conv.id, "test")

      withDelay { listMessages(conv.id) should have size 9 }

      val msg = lastMessage(conv.id).get
      msg.contentString shouldEqual "test"
      msg.state shouldEqual Message.Status.PENDING

      withDelay {
        postRequests.length should be >= 2
      } (15.seconds)

      lastMessage(conv.id).get.state shouldEqual Message.Status.PENDING

      postMessageResponse = successResponse
      withDelay {
        lastMessage(conv.id).get.state shouldEqual Message.Status.SENT
      } (20.seconds)
    }

    scenario("Mark message failed after sending timeout") {
      sendingTimeout = 5.seconds
      postMessageResponse = serverError
      zmessaging.convsUi.sendMessage(conv.id, "test 1")

      withDelay { listMessages(conv.id) should have size 10 }

      val msg = lastMessage(conv.id).get
      msg.contentString shouldEqual "test 1"
      msg.state shouldEqual Message.Status.PENDING

      withDelay {
        postRequests should not be empty
        lastMessage(conv.id).get.state shouldEqual Message.Status.FAILED
      } (15.seconds)
    }

    scenario("Mark message failed when sent in offline") {
      zmessaging.network.networkMode ! NetworkMode.OFFLINE
      postMessageResponse = serverError
      val inputState = conv.getInputStateIndicator
      inputState.textChanged()
      awaitUi(1.second)
      zmessaging.convsUi.sendMessage(conv.id, "test 2")
      inputState.textCleared()

      withDelay { listMessages(conv.id) should have size 11 }

      lastMessage(conv.id).get.contentString shouldEqual "test 2"

      withDelay {
        postRequests should not be empty
        lastMessage(conv.id).get.state shouldEqual Message.Status.FAILED
      }
      postRequests = Nil

      zmessaging.network.networkMode ! NetworkMode.WIFI
      awaitUi(5.seconds)
      lastMessage(conv.id).get.state shouldEqual Message.Status.FAILED
      postRequests shouldBe empty
    }
  }

  override def postMessage(convId: RConvId, msg: OtrMessage, ignoreMissing: Boolean): ErrorOrResponse[MessageResponse] = {
    postRequests = postRequests :+ msg
    CancellableFuture.delayed(clientDelay)(postMessageResponse(msg))
  }

  override def updateTypingState(id: RConvId, isTyping: Boolean): ErrorOrResponse[Unit] =
    if (zmessaging.network.networkMode.currentValue.contains(NetworkMode.OFFLINE))
      CancellableFuture.delayed(clientDelay)(Left(ErrorResponse(500, "", "")))
    else super.updateTypingState(id, isTyping)
}
