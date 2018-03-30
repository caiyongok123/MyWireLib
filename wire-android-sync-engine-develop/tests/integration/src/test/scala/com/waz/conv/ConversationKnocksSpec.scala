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

import java.lang.System.currentTimeMillis

import akka.pattern.ask
import com.waz.api.{Message => ApiMessage, _}
import com.waz.model.RConvId
import com.waz.provision.ActorMessage._
import com.waz.testutils.Matchers._
import com.waz.testutils.Implicits._
import com.waz.utils.events.EventContext
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.duration._

class ConversationKnocksSpec extends FeatureSpec with Matchers with ProvisionedApiSpec with ThreadActorSpec {
  override val provisionFile = "/two_users_connected.json"

  lazy val conversations = api.getConversations
  lazy val conv = {
    withDelay {
      conversations should have size 1
    }
    conversations.getConversation(provisionedUserId("auto2").str)
  }
  lazy val self = api.getSelf

  lazy val inputState = conv.getInputStateIndicator

  lazy val auto2 = registerDevice("ConversationKnocksSpec_auto2")

  feature("Send knock") {
    implicit val ev = EventContext.Global

    scenario("send single knock") {
      withDelay { listMessages(conv.id) should have size 1 }

      conv.knock()

      withDelay {
        lastMessage(conv.id).get.msgType shouldEqual ApiMessage.Type.KNOCK
        lastMessage(conv.id).get.state shouldEqual ApiMessage.Status.SENT
      }
    }
  }

  feature("Receive knocks") {

    scenario("Conversation incomingKnock should not contain self knocks") {
      conv.getIncomingKnock should be(null)
    }

    scenario("init remote process") {
      auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
      awaitUi(1.second)
    }

    scenario("Receive incoming knock message") {
      val count = listMessages(conv.id).size
      auto2 ? Knock(RConvId(self.getUser.getId)) should eventually(be(Successful))

      withDelay {
        listMessages(conv.id) should have size (count + 1)
        lastMessage(conv.id).get.msgType shouldEqual ApiMessage.Type.KNOCK
        lastMessage(conv.id).get.localTime.toEpochMilli should be(currentTimeMillis() +- 1000)
        conv.getIncomingKnock should not be null
        conv.getIncomingKnock.getLocalTime.toEpochMilli should be(currentTimeMillis() +- 1000)
      }
    }
  }
}
