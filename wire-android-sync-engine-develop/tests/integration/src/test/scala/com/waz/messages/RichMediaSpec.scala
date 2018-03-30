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

import java.util

import akka.pattern.ask
import com.waz.api.MediaAsset.StreamingCallback
import com.waz.api.Message.Part
import com.waz.api._
import com.waz.provision.ActorMessage.{AwaitSyncCompleted, Login, SendText, Successful}
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.utils.returning
import com.waz.ZLog.ImplicitTag._
import com.waz.utils.wrappers.URI
import org.scalatest.{BeforeAndAfter, EitherValues, FeatureSpec, Matchers}

import scala.collection.JavaConverters._

class RichMediaSpec extends FeatureSpec with Matchers with EitherValues with BeforeAndAfter with ProvisionedApiSpec with ThreadActorSpec { test =>

  override val provisionFile = "/two_users_connected.json"

  lazy val conversations = api.getConversations
  lazy val self = api.getSelf
  lazy val conv = {
    withDelay { conversations should not be empty }
    conversations.find(_.getId == auto2Id.str).get
  }
  var lastKnock = None: Option[Message]

  def msgs = listMessages(conv.id)

  lazy val auto2 = registerDevice("auto2")
  lazy val auto2Id = provisionedUserId("auto2")

  scenario("init local") {
    withDelay {
      conversations should not be empty
      msgs should not be empty
      zmessaging.syncContent.syncJobs.currentValue.map(_.isEmpty) shouldEqual Some(true)
    }
  }

  scenario("init clients") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    auto2 ? AwaitSyncCompleted should eventually(be(Successful))
  }

  feature("OpenGraph link") {

    scenario("Send message with single web link") {
      val count = msgs.size
      zmessaging.convsUi.sendMessage(conv.id, "http://www.wire.com")

      withDelay {
        msgs should have size (count + 1)
        val lastMsg = msgs.last
        lastMsg.state shouldEqual Message.Status.SENT
        lastMsg.msgType shouldEqual Message.Type.RICH_MEDIA
        lastMsg.content should have size 1
        val part = lastMsg.content.head
        part.tpe shouldEqual Message.Part.Type.WEB_LINK
      }
    }

    scenario("Send message with facebook link") {
      val count = msgs.size

      zmessaging.convsUi.sendMessage(conv.id, "Http://Facebook.com")

      withDelay {
        msgs should have size (count + 1)
        val lastMsg = msgs.last
        lastMsg.state shouldEqual Message.Status.SENT
        lastMsg.msgType shouldEqual Message.Type.RICH_MEDIA
        lastMsg.content should have size 1
        val part = lastMsg.content.head
        part.tpe shouldEqual Message.Part.Type.WEB_LINK
      }
    }

    scenario("Send message with text and link") {
      val count = msgs.size
      zmessaging.convsUi.sendMessage(conv.id, "test http://www.github.com")

      withDelay {
        msgs should have size (count + 1)
        val lastMsg = msgs.last
        lastMsg.state shouldEqual Message.Status.SENT
        lastMsg.msgType shouldEqual Message.Type.RICH_MEDIA
        lastMsg.content.map(_.tpe).toSeq shouldEqual Seq(Part.Type.TEXT, Part.Type.WEB_LINK)
      }
    }

    scenario("Receive message with link preview") {
      val count = msgs.size
      val link = "https://wire.com"

      auto2 ? SendText(conv.data.remoteId, link) should eventually(be(Successful))

      lazy val msg = returning(msgs.last) { m =>
        m.contentString shouldEqual link
        m.msgType shouldEqual Message.Type.RICH_MEDIA
      }

      withDelay {
        withClue(msgs.map(m => (m.msgType, m.contentString)).mkString(", ")) {
          msgs should have size (count + 1)
        }
        msg.contentString shouldEqual link
        msg.msgType shouldEqual Message.Type.RICH_MEDIA
        msg.content should not be empty
        msg.content.head.tpe shouldEqual Message.Part.Type.WEB_LINK
      }
    }

    scenario("Receive message with multiple link previews") {
      val count = msgs.size
      val text = "Test message https://wire.com and then http://github.com"

      auto2 ? SendText(conv.data.remoteId, text) should eventually(be(Successful))

      lazy val msg = returning(msgs.last) { m =>
        m.contentString shouldEqual text
        m.msgType shouldEqual Message.Type.RICH_MEDIA
      }

      withDelay {
        withClue(msgs.map(m => (m.msgType, m.contentString)).mkString(", ")) {
          msgs should have size (count + 1)
        }
        msg.contentString shouldEqual text
        msg.msgType shouldEqual Message.Type.RICH_MEDIA
        msg.content.map(_.tpe).toSeq shouldEqual Seq(Part.Type.TEXT, Part.Type.WEB_LINK, Part.Type.TEXT, Part.Type.WEB_LINK)
      }
    }

    scenario("Receive message with web and youtube link") {
      val count = msgs.size
      val text = "Test message https://wire.com https://www.youtube.com/watch?v=mTWfqi3-3qU"

      auto2 ? SendText(conv.data.remoteId, text) should eventually(be(Successful))

      lazy val msg = returning(msgs.last) { m =>
        m.contentString shouldEqual text
        m.msgType shouldEqual Message.Type.RICH_MEDIA
      }

      withDelay {
        withClue(msgs.map(m => (m.msgType, m.contentString)).mkString(", ")) {
          msgs should have size (count + 1)
        }
        msg.contentString shouldEqual text
        msg.msgType shouldEqual Message.Type.RICH_MEDIA
        msg.content.map(_.tpe).toSeq shouldEqual Seq(Part.Type.TEXT, Part.Type.WEB_LINK, Part.Type.YOUTUBE)
      }
    }
  }

  feature("Youtube") {

    scenario("Receive message with youtube link") {
      val link = "https://www.youtube.com/watch?v=mTWfqi3-3qU"
      auto2 ? SendText(conv.data.remoteId, link) should eventually(be(Successful))

      lazy val msg = returning(msgs.last) { m => m.contentString shouldEqual link }

      withDelay {
        msgs should not be empty
        msg.contentString shouldEqual "https://www.youtube.com/watch?v=mTWfqi3-3qU"
        msg.msgType shouldEqual Message.Type.RICH_MEDIA
        msg.content should not be empty
        msg.content.head.tpe shouldEqual Message.Part.Type.YOUTUBE
      }
    }
  }

  feature("SoundCloud") {

    scenario("Receive message with soundcloud link") {
      auto2 ? SendText(conv.data.remoteId, "https://soundcloud.com/lescourgettesgivrees/le-bois-du-casier") should eventually(be(Successful))

      var media: MediaAsset = null

      withDelay {
        msgs should not be empty
        val msg = msgs.last
        msg.contentString shouldEqual "https://soundcloud.com/lescourgettesgivrees/le-bois-du-casier"
        msg.msgType shouldEqual Message.Type.RICH_MEDIA
        msg.content should not be empty

        val part = msg.content.head
        withClue(msg.content.map { p => (p.tpe, p.content) }.mkString(", ")) {
          part.tpe shouldEqual Message.Part.Type.SOUNDCLOUD
        }
      }

      var uris = Option.empty[Seq[URI]]


      withDelay {
        uris shouldBe 'defined
        uris.get should not be empty
      }
    }
  }

  feature("Spotify") {

    scenario("Receive message with spotify link") {
      auto2 ? SendText(conv.data.remoteId, "https://open.spotify.com/track/0sUyqewVzwv0e5tK3hS6vJ") should eventually(be(Successful))

      var media: MediaAsset = null

      withDelay {
        msgs should not be empty
        val msg = msgs.last
        msg.contentString shouldEqual "https://open.spotify.com/track/0sUyqewVzwv0e5tK3hS6vJ"
        msg.msgType shouldEqual Message.Type.RICH_MEDIA
        msg.content should not be empty

        val part = msg.content.head
        withClue(msg.content.map { p => (p.tpe, p.content) }.mkString(", ")) {
          part.tpe shouldEqual Message.Part.Type.SPOTIFY
        }
      }

      var uris = Option.empty[Seq[URI]]


      withDelay {
        uris shouldBe 'defined
        uris.get should not be empty
      }
    }
  }
}

