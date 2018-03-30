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
package com.waz.messages

import akka.pattern.ask
import com.waz.api._
import com.waz.model.MessageData
import com.waz.provision.ActorMessage._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}

import scala.concurrent.duration._

class DeleteMessageSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with ProvisionedApiSpec with ThreadActorSpec { test =>

  override val provisionFile = "/two_users_connected.json"

  lazy val conversations = api.getConversations
  lazy val self = provisionedUserId("auto1")
  lazy val otherUser = provisionedUserId("auto2")
  lazy val conv = conversations.get(0)
  def msgs = listMessages(conv.id)

  lazy val otherUserClient = registerDevice("other_user")
  lazy val secondClient = registerDevice("second_client")

  scenario("Initial sync") {
    withDelay(msgs should have size 1)
  }

  scenario("Init remotes") {
    secondClient ? Login(email, password) should eventually(be(Successful))
    secondClient ? AwaitSyncCompleted should eventually(be(Successful))

    otherUserClient ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    otherUserClient ? AwaitSyncCompleted should eventually(be(Successful))
  }

  feature("Delete message") {

    scenario("Receive message from other user") {
      otherUserClient ? SendText(conv.data.remoteId, "test message") should eventually(be(Successful))
      withDelay(msgs should have size 2)
    }

    scenario("Delete received message") {
      zmessaging.convsUi.deleteMessage(conv.id, msgs.last.id)
      withDelay {
        msgs should have size 1
      }
    }

    scenario("Receive another message") {
      otherUserClient ? SendText(conv.data.remoteId, "test message") should eventually(be(Successful))
      withDelay(msgs should have size 2)
    }

    scenario("Delete message on remote device") {
      val msg = msgs.last
      secondClient ? DeleteMessage(conv.data.remoteId, msg.id) should eventually(be(Successful))
      withDelay {
        msgs should have size 1
      }
    }
  }

  feature("Recall message") {

    def checkLastMessageIsRecalled(msg: MessageData) = withDelay {
      val last = msgs.last
      last.msgType shouldEqual Message.Type.RECALLED
      last.editTime.isAfter(msg.time) shouldEqual true
      last.contentString shouldBe empty
      last.state shouldEqual Message.Status.SENT
    }

    scenario("Send new text message") {
      zmessaging.convsUi.sendMessage(conv.id, "test msg to recall")
      withDelay {
        msgs should have size 2
        msgs.last.state shouldEqual Message.Status.SENT
      }
    }

    scenario("Recall recently sent message") {
      zmessaging.convsUi.recallMessage(conv.id, msgs.last.id)
      withDelay {
        msgs should have size 1
      }
    }

    scenario("Send another message") {
      zmessaging.convsUi.sendMessage(conv.id, "second recall")
      withDelay {
        msgs should have size 2
        msgs.last.state shouldEqual Message.Status.SENT
      }
    }

    scenario("Receive recall from other device") {
      secondClient ? RecallMessage(conv.data.remoteId, msgs.last.id) should eventually(be(Successful))
      withDelay {
        msgs should have size 1
      }
    }

    scenario("Receive message from other user") {
      otherUserClient ? SendText(conv.data.remoteId, "test message") should eventually(be(Successful))
      withDelay(msgs should have size 2)
    }

    scenario("Receive recall from other user") {
      val msg = msgs.last
      otherUserClient ? RecallMessage(conv.data.remoteId, msgs.last.id) should eventually(be(Successful))
      checkLastMessageIsRecalled(msg)
    }

    scenario("Send and recall link message") {
      zmessaging.convsUi.sendMessage(conv.id, "http://wire.com")
      withDelay { msgs should have size 3 }
      val msg = msgs.last
      zmessaging.convsUi.recallMessage(conv.id, msgs.last.id)
      awaitUi(3.seconds)

      msgs should have size 2
    }

    scenario("Receive link message from other user") {
      otherUserClient ? SendText(conv.data.remoteId, "http://wire.com") should eventually(be(Successful))
      withDelay(msgs should have size 3)
    }

    scenario("Receive recall for link message") {
      val msg = msgs.last
      otherUserClient ? RecallMessage(conv.data.remoteId, msgs.last.id) should eventually(be(Successful))
      withDelay {
        msgs.last.msgType shouldEqual Message.Type.RECALLED
      }
      checkLastMessageIsRecalled(msg)
    }

    scenario("Delete system message for recall") {
      zmessaging.convsUi.deleteMessage(conv.id, msgs.last.id)
      withDelay {
        msgs should have size 2
      }
    }

    scenario("Delete system message for recall on second device") {
      val msg = msgs.last
      msg.msgType shouldEqual Message.Type.RECALLED

      secondClient ? DeleteMessage(conv.data.remoteId, msg.id) should eventually(be(Successful))

      withDelay {
        msgs should have size 1
      }
    }

    scenario("Receive two messages and recall for the first one") {
      otherUserClient ? SendText(conv.data.remoteId, "test 1") should eventually(be(Successful))
      otherUserClient ? SendText(conv.data.remoteId, "test 2") should eventually(be(Successful))

      withDelay {
        msgs should have size 3
        msgs.last.contentString shouldEqual "test 2"
      }

      val msg = msgs(1)

      otherUserClient ? RecallMessage(conv.data.remoteId, msg.id) should eventually(be(Successful))

      withDelay {
        withClue(msgs.map(m => (m.msgType, m.contentString))) {
          msgs(1).msgType shouldEqual Message.Type.RECALLED
        }
      }
    }
  }
}
