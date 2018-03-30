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
import com.waz.provision.ActorMessage._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}

class LocationMessageSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with ProvisionedApiSpec with ThreadActorSpec { test =>

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

  feature("Send location") {

    scenario("Send location message") {
      val loc = new MessageContent.Location(-10.45f, 23.43f, "location name", 10)
      zmessaging.convsUi.sendMessage(conv.id, loc)
      withDelay {
        msgs should have size 2
        msgs.last.msgType shouldEqual Message.Type.LOCATION
        msgs.last.state shouldEqual Message.Status.SENT
      }
      val msg = msgs.last
      msg.location shouldEqual loc
    }
  }

  feature("Receive location") {

    scenario("Receive message from other user") {
      val fromBefore = msgs.size

      otherUserClient ? SendLocation(conv.data.remoteId, 52.5270968f, 13.4020788f, "test location", 9) should eventually(beMatching { case Successful(_) => true })

      withDelay {
        msgs should have size (fromBefore + 1)
        msgs.last.msgType shouldEqual Message.Type.LOCATION
        msgs.last.state shouldEqual Message.Status.SENT
      }
      val msg = msgs.last
      msg.location shouldEqual new MessageContent.Location(52.5270968f, 13.4020788f, "test location", 9)
    }

    scenario("Receive message sent from second client") {
      val fromBefore = msgs.size

      secondClient ? SendLocation(conv.data.remoteId, 1f, 2f, "self location", 9) should eventually(beMatching { case Successful(_) => true })

      withDelay {
        msgs should have size (fromBefore + 1)
        msgs.last.msgType shouldEqual Message.Type.LOCATION
        msgs.last.state shouldEqual Message.Status.SENT
      }
      val msg = msgs.last
      msg.location shouldEqual new MessageContent.Location(1f, 2f, "self location", 9)
    }
  }
}
