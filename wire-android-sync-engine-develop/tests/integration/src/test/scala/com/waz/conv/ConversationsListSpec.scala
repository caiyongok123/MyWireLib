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

import java.util.Date

import akka.pattern.ask
import com.waz.api.impl.{Conversation => Conv}
import com.waz.api.{IConversation, ProvisionedApiSpec, ThreadActorSpec}
import com.waz.model.GenericMessage.TextMessage
import com.waz.model._
import com.waz.provision.ActorMessage.{AcceptConnection, Login, Successful}
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class ConversationsListSpec extends FeatureSpec with Matchers with ProvisionedApiSpec with ThreadActorSpec {

  override val provisionFile = "/conversations.json"

  lazy val convs = api.getConversations
  lazy val conv = {
    withDelay(convs.size should be > 0)
    convs.getConversation(provisionedUserId("auto2").str)
  }
  lazy val self = api.getSelf

  lazy val Seq(unconnected1, unconnected2) = Seq("auto5", "auto6") map provisionedUserId

  lazy val auto4 = registerDevice("ConversationListSpec_auto4")


  feature("Conversation list ordering") {

    scenario("initial ordering descending by last event time") {
      withDelay { convs.size shouldEqual 5 }

      convs.get(0).getType should not be IConversation.Type.SELF
      convs.map(_.asInstanceOf[Conv].data.lastEventTime.toEpochMilli).reverse should be(sorted)
    }

    scenario("reorder after new message is received on push channel") {
      val lastConv = convs.last

      withDelay(listMessages(lastConv.id) should not be empty)(15.seconds)

      zmessaging.eventPipeline(Seq(GenericMessageEvent(lastConv.data.remoteId, new Date(), self.getUser.id, TextMessage("test message", Map.empty)).withCurrentLocalTime()))

      withDelay {
        lastMessage(lastConv.id).get.contentString shouldEqual "test message"
        lastConv.data.lastEventTime shouldEqual lastMessage(lastConv.id).get.time

        convs.get(0) shouldEqual lastConv
      }

      convs.map(_.data.lastEventTime.toEpochMilli).reverse should be(sorted)
    }

    scenario("reorder on otr message") {
      val lastConv = convs.last

      withDelay(listMessages(lastConv.id) should not be empty)(15.seconds)

      zmessaging.eventPipeline(Seq(GenericMessageEvent(lastConv.data.remoteId, new Date(), self.getUser.id, TextMessage("test message 1", Map.empty)).withCurrentLocalTime()))

      withDelay {
        lastMessage(lastConv.id).get.contentString shouldEqual "test message 1"
        lastConv.data.lastEventTime shouldEqual lastMessage(lastConv.id).get.time

        convs.get(0) shouldEqual lastConv
      }

      convs.map(_.data.lastEventTime.toEpochMilli).reverse should be(sorted)
    }

    scenario("incoming connection conversations") {
      withDelay(convs should not be empty)

      zmessaging.eventPipeline(Seq(UserConnectionEvent(RConvId("123"), self.getUser.id, unconnected1, Some("Want to connect"), UserData.ConnectionStatus.PendingFromOther, new Date())))

      val incoming = convs.getIncomingConversations

      withDelay {
        convs.map(_.data.remoteId) should not contain RConvId("123")
        incoming.map(_.data.remoteId) should contain(RConvId("123"))
      }

      zmessaging.eventPipeline(Seq(UserConnectionEvent(RConvId("321"), self.getUser.id, unconnected2, Some("Want to connect"), UserData.ConnectionStatus.PendingFromOther, new Date())))

      withDelay {
        convs.map(_.data.remoteId) should not contain RConvId("321")
        convs.getIncomingConversations.map(_.data.remoteId) should contain(RConvId("321"))
      }

      convs.map(_.data.lastEventTime.toEpochMilli).reverse should be(sorted)
      convs.getIncomingConversations.map(_.asInstanceOf[Conv].data.lastEventTime.toEpochMilli).reverse should be(sorted)
      convs.getIncomingConversations.map(_.data.remoteId).toSet shouldEqual Set(RConvId("321"), RConvId("123"))
    }
  }
  
  feature("conversations list state") {

    scenario("set has pending state") {
      val state = convs.getState
      state.hasPending shouldEqual false

      val user = api.getUser(provisionedUserId("auto4").str)
      user.connect("test")

      withDelay(state.hasPending shouldEqual true)
    }

    scenario("init remote process") {
      auto4 ? Login(provisionedEmail("auto4"), "auto4_pass") should eventually(be(Successful))
    }

    scenario("clear hasPending and set hasUnread once other user accepts") {
      val state = convs.getState
      withDelay(state.hasPending shouldEqual true)

      auto4 ! AcceptConnection(UserId(self.getUser.getId))

      withDelay(state.hasPending shouldEqual false)
      withDelay(state.hasUnread shouldEqual true)
    }
  }

  feature("archived conversations") {

    lazy val archived = convs.getArchivedConversations

    scenario("archive conv") {
      conv.setArchived(true)

      withDelay(archived should not be empty)
      archived.get(0) shouldEqual conv

      withDelay { Await.result(zmessaging.syncContent.syncStorage(_.getJobs), 1.second) shouldBe empty }

      archived should not be empty
      archived.get(0) shouldEqual conv
    }

    scenario("unarchive conv") {
      conv.setArchived(false)

      withDelay { archived should be(empty) }
      withDelay { Await.result(zmessaging.syncContent.syncStorage(_.getJobs), 1.second) shouldBe empty }
      archived shouldBe empty
      withDelay { convs should contain(conv) }
    }
  }
}
