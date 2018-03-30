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
package com.waz.conv

import akka.pattern.ask
import com.waz.api._
import com.waz.model.ConversationData.ConversationType
import com.waz.provision.ActorMessage._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.utils._
import com.waz.utils.events.EventContext
import org.scalatest.{FeatureSpec, Matchers}

class TypingSpec extends FeatureSpec with Matchers with ProvisionedApiSpec with ThreadActorSpec {

  override val provisionFile = "/three_users_group_conv.json"

  lazy val conversations = api.getConversations
  lazy val conv = withDelay {
      returning(conversations.find(_.getType == ConversationType.Group)) { _ should be ('defined) }
  }.get

  lazy val self = api.getSelf

  lazy val inputState = conv.getInputStateIndicator
  lazy val typingUsers = inputState.getTypingUsers

  lazy val auto2 = registerDevice("TypingSpec_auto2")
  lazy val auto3 = registerDevice("TypingSpec_auto3")

  feature("Send typing events") {
    implicit val ev = EventContext.Global

    scenario("send typing state changes") {
      withDelay { typingUsers should be (empty) }

      inputState.textChanged()
      withDelay { typingUsers should be (empty) }

      inputState.textCleared()
      withDelay { typingUsers should be (empty) }
    }
  }

  feature("Receive typing events") {
    scenario("init auto2") {
      auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    }

    scenario("init auto3") {
      auto3 ? Login(provisionedEmail("auto3"), "auto3_pass") should eventually(be(Successful))
    }

    scenario("auto2 starts typing") {
      auto2 ? Typing(conv.data.remoteId) should eventually(be(Successful))
      withDelay { typingUsers should have size 1 }
    }

    scenario("auto3 starts typing") {
      auto3 ? Typing(conv.data.remoteId) should eventually(be(Successful))
      withDelay { typingUsers should have size 2 }
      typingUsers.map(_.getId) shouldEqual (List("auto2", "auto3") map provisionedUserId map (_.str))
    }

    scenario("auto2 starts typing again") {
      auto2 ? Typing(conv.data.remoteId) should eventually(be(Successful))
      withDelay { typingUsers should have size 2 }
      typingUsers.map(_.getId) shouldEqual (List("auto2", "auto3") map provisionedUserId map (_.str))
    }

    scenario("auto2 stops typing") {
      auto2 ? ClearTyping(conv.data.remoteId) should eventually(be(Successful))
      withDelay { typingUsers should have size 1 }
      typingUsers(0).getId shouldEqual provisionedUserId("auto3").str
    }
  }
}
