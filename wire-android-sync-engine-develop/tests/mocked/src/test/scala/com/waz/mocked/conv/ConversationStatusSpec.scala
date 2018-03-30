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
package com.waz.mocked.conv

import com.waz.RobolectricUtils
import com.waz.api.MockedClientApiSpec
import com.waz.mocked.{SystemTimeline, MockBackend}
import com.waz.model._
import com.waz.testutils.Implicits._
import org.scalatest.{FeatureSpec, Matchers, OptionValues}

import scala.concurrent.duration._

class ConversationStatusSpec extends FeatureSpec with Matchers with OptionValues with MockedClientApiSpec with MockBackend with RobolectricUtils {
  import DefaultPushBehaviour.Implicit

  lazy val user2 = UserId()
  lazy val user3 = UserId()
  val conv1 = RConvId("c1")
  val conv2 = RConvId("c2")

  override protected def beforeAll(): Unit = {
    addConnection(user2)
    addConnection(user3)
    addGroupConversation(Seq(selfUserId, user2, user3), id = conv1)
    addGroupConversation(Seq(selfUserId, user2, user3), id = conv2)

    leaveGroupConversation(conv1)
    super.beforeAll()
  }

  scenario("Initial sync of conversation that self has left before") {
    val convs = api.getConversations
    val archived = convs.getArchivedConversations

    convs.asScala.find(_.getId == conv1.str) shouldBe empty
    withDelay { archived.asScala.find(_.getId == conv1.str) shouldBe 'defined }
    33.times {
      awaitUi(100.millis)
      convs.asScala.find(_.getId == conv1.str) shouldBe empty
      archived.asScala.find(_.getId == conv1.str) shouldBe 'defined
    }
  }

  scenario("Leaving a conversation") {
    val convs = api.getConversations
    val archived = convs.getArchivedConversations

    withDelay { convs.asScala.find(_.getId == conv2.str) shouldBe 'defined }
    val conv = convs.asScala.find(_.getId == conv2.str).value

    conv.leave()

    withDelay {
      convs.asScala.find(_.getId == conv2.str) shouldBe empty
      archived.asScala.find(_.getId == conv2.str) shouldBe 'defined
      conv.isMemberOfConversation shouldEqual false
    }

    zmessaging.sync.syncConversations()

    66.times {
      awaitUi(50.millis)
      convs.asScala.find(_.getId == conv2.str) shouldBe empty
      archived.asScala.find(_.getId == conv2.str) shouldBe 'defined
    }
  }

  scenario("Other user adds us back to conv") {
    val convs = api.getConversations
    val archived = convs.getArchivedConversations

    addMembersToGroupConversation(conv2, Seq(selfUserId), from = user2, time = SystemTimeline)

    withDelay {
      convs.map(_.data.remoteId) should contain(conv2)
      archived.asScala.find(_.getId == conv2.str) shouldBe empty
    }

    val conv = convs.find(_.getId == conv2.str).value

    conv.isMemberOfConversation shouldEqual true
  }

  scenario("Other user removes us from conv") {
    val convs = api.getConversations
    val conv = convs.asScala.find(_.getId == conv2.str).value

    removeUsersFromGroupConversation(Seq(selfUserId), conv2, from = user2, time = SystemTimeline)

    withDelay {
      convs.map(_.data.remoteId) should contain(conv2) // conv is not hidden in that case
      conv.isMemberOfConversation shouldEqual false
    }
  }
}
