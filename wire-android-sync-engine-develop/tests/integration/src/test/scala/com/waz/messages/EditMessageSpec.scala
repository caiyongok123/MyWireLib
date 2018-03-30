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
import android.net.Uri
import com.waz.api.Message.Part
import com.waz.api._
import com.waz.provision.ActorMessage._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}

class EditMessageSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with ProvisionedApiSpec with ThreadActorSpec { test =>

  override val provisionFile = "/two_users_connected.json"

  lazy val conversations = api.getConversations
  lazy val self = provisionedUserId("auto1")
  lazy val otherUser = provisionedUserId("auto2")
  lazy val conv = conversations.get(0)
  lazy val msgs = listMessages(conv.id)

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

  feature("Edit text messages") {

    scenario("Send text message and edit it") {
      zmessaging.convsUi.sendMessage(conv.id, "test msg to edit")
      withDelay {
        msgs should have size 2
        msgs.last.state shouldEqual Message.Status.SENT
      }

      val msg = msgs.last

      zmessaging.convsUi.updateMessage(conv.id, msg.id, "edited message")

      withDelay {
        msgs should have size 2
        getMessage(msg.id) shouldBe 'empty
        val last = msgs.last
        last.editTime.toEpochMilli should be > msg.time.toEpochMilli
        last.contentString shouldEqual "edited message"
        last.id should not equal msg.id
        last.state shouldEqual Message.Status.SENT
      }
    }

    scenario("Send text message and edit it right away ") {
      val service = zmessaging.convsUi
      val lock = zmessaging.syncRequests.scheduler.queue.acquire(conv.id).await() // block sync service

      val m = service.sendMessage(conv.id, "test msg to edit").await().get
      val m1 = service.updateMessage(conv.id, m.id, "updated").await()

      withDelay { msgs should have size 3 }

      msgs.last.id shouldEqual m.id

      lock.release() // start sync now

      withDelay {
        msgs should have size 3
        msgs.last.state shouldEqual Message.Status.SENT
      }

      val remote = (otherUserClient ? GetMessages(conv.data.remoteId)).await().asInstanceOf[ConvMessages].msgs.toSeq
      info(s"messages on remote: $remote")
      info(s"updated: $m1")
      info(s"last: ${msgs.last}")

      remote should have size msgs.size
      remote.last.id shouldEqual m.id

      msgs.last.contentString shouldEqual "updated"
    }

    scenario("Send emoji only message and edit it") {
      val count = msgs.size
      zmessaging.convsUi.sendMessage(conv.id, "\u263A")
      withDelay {
        msgs should have size (count + 1)
        msgs.last.msgType shouldEqual Message.Type.TEXT_EMOJI_ONLY
        msgs.last.state shouldEqual Message.Status.SENT
      }

      val msg = msgs.last

      zmessaging.convsUi.updateMessage(conv.id, msg.id, "\u263B")

      withDelay {
        msgs should have size (count + 1)
        getMessage(msg.id) shouldBe 'empty
        val last = msgs.last
        last.editTime.toEpochMilli should be > msg.time.toEpochMilli
        last.msgType shouldEqual Message.Type.TEXT_EMOJI_ONLY
        last.contentString shouldEqual "\u263B"
        last.id should not equal msg.id
        last.state shouldEqual Message.Status.SENT
      }
    }

    scenario("Receive text message and edit from other user") {
      val count = msgs.size
      otherUserClient ? SendText(conv.data.remoteId, "test message 1") should eventually(be(Successful))
      withDelay {
        msgs should have size (count + 1)
        msgs.last.contentString shouldEqual "test message 1"
      }

      val msg = msgs.last

      zmessaging.convsUi.sendMessage(conv.id, "test")

      withDelay(msgs should have size (count + 2))

      otherUserClient ? UpdateText(msg.id, "updated") should eventually(be(Successful))

      withDelay {
        getMessage(msg.id) shouldBe 'empty
        msgs(count).contentString shouldEqual "updated"
        msgs(count).state shouldEqual Message.Status.SENT
        msgs(count).editTime.toEpochMilli should be > msg.time.toEpochMilli
      }
    }

    scenario("Edit on two devices in the same time") {
      val count = msgs.size
      secondClient ? SendText(conv.data.remoteId, "test message 2") should eventually(be(Successful))
      withDelay {
        msgs should have size (count + 1)
        withClue(msgs.map(m => (m.msgType, m.contentString)).mkString(", ")) {
          msgs.last.contentString shouldEqual "test message 2"
        }
      }

      val msg = msgs.last
      secondClient ? UpdateText(msg.id, "updated on remote") //should eventually(be(Successful))
      zmessaging.convsUi.updateMessage(conv.id, msg.id, "updated locally")

      withDelay {
        getMessage(msg.id) shouldBe 'empty
        val last = msgs.last
        last.state shouldEqual Message.Status.SENT
        last.editTime.toEpochMilli should be > msg.time.toEpochMilli
      }

      val remote = (secondClient ? GetMessages(conv.data.remoteId)).await().asInstanceOf[ConvMessages].msgs

      info(s"last msg: ${msgs.last}")
      remote.last.id shouldEqual msgs.last.id
      remote.last.time shouldEqual msgs.last.time
    }
  }

  feature("Edit link message") {
    scenario("Send link with text and edit it") {
      val count = msgs.size
      zmessaging.convsUi.sendMessage(conv.id, "test wire.com edit")

      withDelay {
        msgs should have size (count + 1)
        msgs.last.state shouldEqual Message.Status.SENT
        msgs.last.msgType shouldEqual Message.Type.RICH_MEDIA
        msgs.last.content.map(_.tpe) shouldEqual Seq(Part.Type.TEXT, Part.Type.WEB_LINK, Part.Type.TEXT)
      }

      val msg = msgs.last
      zmessaging.convsUi.updateMessage(conv.id, msg.id, "edited message facebook.com")

      withDelay {
        msgs should have size (count + 1)
        val last = msgs.last
        last.editTime.toEpochMilli should be > msg.time.toEpochMilli
        last.contentString shouldEqual "edited message facebook.com"
        last.id should not equal msg.id
        last.state shouldEqual Message.Status.SENT
        last.msgType shouldEqual Message.Type.RICH_MEDIA

        last.content.map(_.tpe) shouldEqual Seq(Part.Type.TEXT, Part.Type.WEB_LINK)
        last.content(1).contentAsUri shouldEqual Uri.parse("http://facebook.com")
      }
    }

    scenario("Receive link with text and edit update") {
      val count = msgs.size
      otherUserClient ? SendText(conv.data.remoteId, "test message wire.com") should eventually(be(Successful))
      withDelay {
        msgs should have size (count + 1)
        msgs.last.contentString shouldEqual "test message wire.com"
      }

      val msg = msgs.last

      zmessaging.convsUi.sendMessage(conv.id, "test")

      withDelay(msgs should have size (count + 2))

      otherUserClient ? UpdateText(msg.id, "updated facebook.com test") should eventually(be(Successful))

      withDelay {
        msgs should have size (count + 2)
        val updated = msgs(count)
        updated.id should not equal msg.id
        updated.contentString shouldEqual "updated facebook.com test"
        updated.msgType shouldEqual Message.Type.RICH_MEDIA
        updated.content.map(_.tpe) shouldEqual Seq(Part.Type.TEXT, Part.Type.WEB_LINK, Part.Type.TEXT)
        updated.content(1).contentAsUri shouldEqual Uri.parse("http://facebook.com")
        updated.state shouldEqual Message.Status.SENT
        updated.editTime.toEpochMilli should be > msg.time.toEpochMilli
      }
    }
  }

  feature("editing scenarios") {

    scenario("post multiple, edit, post more") {
      val count = msgs.size

      otherUserClient ? SendText(conv.data.remoteId, "other 1") should eventually(be(Successful))
      otherUserClient ? SendText(conv.data.remoteId, "other 2") should eventually(be(Successful))
      otherUserClient ? SendText(conv.data.remoteId, "other 3") should eventually(be(Successful))
      otherUserClient ? SendText(conv.data.remoteId, "other 4") should eventually(be(Successful))

      zmessaging.convsUi.sendMessage(conv.id, "self 1")
      zmessaging.convsUi.sendMessage(conv.id, "self 2")
      zmessaging.convsUi.sendMessage(conv.id, "self 3")

      withDelay {
        msgs should have size (count + 7)
        msgs.drop(count).map(_.contentString) shouldEqual Seq("other 1", "other 2", "other 3", "other 4", "self 1", "self 2", "self 3")
      }

      val msg = msgs(count)

      otherUserClient ? UpdateText(msg.id, "updated 1") should eventually(be(Successful))

      withDelay {
        msgs should have size (count + 7)
        msgs.drop(count).map(_.contentString) shouldEqual Seq("updated 1", "other 2", "other 3", "other 4", "self 1", "self 2", "self 3")
      }

      otherUserClient ? SendText(conv.data.remoteId, "other 5") should eventually(be(Successful))
      otherUserClient ? SendText(conv.data.remoteId, "other 6") should eventually(be(Successful))

      withDelay {
        msgs should have size (count + 9)
        msgs.drop(count).map(_.contentString) shouldEqual Seq("updated 1", "other 2", "other 3", "other 4", "self 1", "self 2", "self 3", "other 5", "other 6")
      }
    }

    scenario("Edit emoji only message multiple times") {
      val count = msgs.size
      var msg = msgs.last

      otherUserClient ? SendText(conv.data.remoteId, "\u263A") should eventually(be(Successful))

      withDelay {
        msgs should have size (count + 1)
        msg = msgs.last
        msg.contentString shouldEqual "\u263A"
        msg.msgType shouldEqual Message.Type.TEXT_EMOJI_ONLY
        msgs should have size (count + 1)
      }


      Seq("\u263B", "\u263C", "\u263D") foreach { text =>
        otherUserClient ? UpdateText(msg.id, text) should eventually(be(Successful))

        withDelay {
          msgs should have size (count + 1)

          msgs.last.msgType shouldEqual Message.Type.TEXT_EMOJI_ONLY
          msgs.last.contentString shouldEqual text
        }
        msg = msgs.last
      }
    }
  }
}
