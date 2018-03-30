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
import com.waz.api.IConversation.Type._
import com.waz.api._
import com.waz.model.UserId
import com.waz.provision.ActorMessage._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.scalatest.{FeatureSpec, Matchers, OptionValues}

class AddMemberToGroupConvSpec extends FeatureSpec with Matchers with OptionValues with ProvisionedApiSpec with ThreadActorSpec { test =>

  override val provisionFile = "/four_users_partially_connected.json"

  lazy val auto2 = registerDevice("AddMemberToGroupConvSpec_auto2")
  lazy val auto3 = registerDevice("AddMemberToGroupConvSpec_auto3")
  lazy val auto4 = registerDevice("AddMemberToGroupConvSpec_auto4")

  lazy val conversations = api.getConversations
  lazy val self = api.getSelf
  lazy val groupConv = conversations.find(_.getType == GROUP).value
  lazy val participants = groupConv.getUsers
  def messages = listMessages(groupConv.id)

  scenario("Setup: local sync") {
    (conversations should have size 3).soon
  }

  scenario("Setup: other clients") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    auto3 ? Login(provisionedEmail("auto3"), "auto3_pass") should eventually(be(Successful))
    auto4 ? Login(provisionedEmail("auto4"), "auto4_pass") should eventually(be(Successful))
    Seq(auto2, auto3, auto4) foreach (_ ? UpdateProfileImage("/images/penguin.png") should eventually(be(Successful)))
  }

  scenario("Setup: add some messages to conversation") {
    soon {
      participants should have size 2
      participants(0).getName shouldEqual "user A"
      participants(0).getPicture.getWidth shouldEqual 480
      participants(1).getName shouldEqual "user C"
      participants(1).getPicture.getWidth shouldEqual 480
      messages should have size 1
    }

    auto2 ? SendText(groupConv.data.remoteId, "Hey evrywun!")
    zmessaging.convsUi.sendMessage(groupConv.id, "first msg", Set.empty[UserId])
    auto3 ? SendText(groupConv.data.remoteId, "Sky.")

    soon {
      messages should have size 4
    }
  }

  scenario(
    """
      |  Connections:
      |
      |    A---B
      |    |\ /
      |    | X
      |    |/ \
      |    C   D
      |
      |  Local user (auto1) is B.
      |  User A (auto2) adds user D (auto4) to group conversation.
    """.stripMargin) {
    auto2 ? AddMembers(groupConv.data.remoteId, provisionedUserId("auto4")) should eventually(be(Successful))

    soon {
      messages should have size 5
      messages(4).members should have size 1
      messages(4).members.head shouldEqual provisionedUserId("auto4")

      participants should have size 3
      participants(2).getName shouldEqual "user D"
      participants(2).getPicture.getWidth shouldEqual 480
    }
  }
}
