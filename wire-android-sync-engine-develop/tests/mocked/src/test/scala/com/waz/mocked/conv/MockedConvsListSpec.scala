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
import com.waz.api.impl.{Conversation => Conv}
import com.waz.api.{IConversation, MockedClientApiSpec}
import com.waz.mocked.{MockBackend, SystemTimeline}
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GenericContent.LastRead
import com.waz.model._
import com.waz.sync.client.PushNotification
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.testutils._
import org.scalatest.{FeatureSpec, Inspectors, Matchers}
import com.waz.ZLog.ImplicitTag._
import com.waz.utils._

import scala.concurrent.duration._

class MockedConvsListSpec extends FeatureSpec with Matchers with Inspectors with MockedClientApiSpec with MockBackend with RobolectricUtils {
  import DefaultPushBehaviour.Implicit
  implicit val timeout: FiniteDuration = 10.seconds

  lazy val user2 = UserId()
  lazy val user3 = UserId()
  lazy val user4 = UserId()

  lazy val convs = api.getConversations
  lazy val conv = {
    withDelay(convs.size should be > 0)
    convs.getConversation(user2.str)
  }
  lazy val self = api.getSelf
  def msgs = listMessages(conv.id)

  override protected def beforeAll(): Unit = {
    addConnection(user2)
    addConnection(user3)
    addGroupConversation(Seq(selfUserId, user2, user3))
    addGroupConversation(Seq(selfUserId, user2, user3))
    addGroupConversation(Seq(selfUserId, user2, user3))
    super.beforeAll()
  }

  feature("Conversation list loading") {

    scenario("Initial load of conversations list, should not contain self") {
      withDelay {
        convs should have size 5
        convs.map(_.getType) should not contain ConversationType.Self
      }
    }

    scenario("initial ordering descending by last event time") {
      withDelay { convs.size shouldEqual 5 }

      convs.get(0).getType should not be IConversation.Type.SELF
      convs.map(_.asInstanceOf[Conv].data.lastEventTime.toEpochMilli).reverse should be(sorted)
    }
  }

  feature("Conversation list ordering") {

    scenario("incoming connection conversations") {
      withDelay(convs should not be empty)

      addIncomingConnectionRequest(convId = RConvId("123"), time = SystemTimeline)

      withDelay {
        convs.map(_.data.remoteId) should not contain RConvId("123")
        convs.getIncomingConversations.map(_.data.remoteId) should contain(RConvId("123"))
      }

      addIncomingConnectionRequest(convId = RConvId("321"), time = SystemTimeline)

      withDelay {
        convs.map(_.data.remoteId) should not contain RConvId("321")
        convs.getIncomingConversations.map(_.data.remoteId) should contain(RConvId("321"))
      }

      convs.map(_.asInstanceOf[Conv].data.lastEventTime.toEpochMilli).reverse should be(sorted)
      convs.getIncomingConversations.map(_.asInstanceOf[Conv].data.lastEventTime.toEpochMilli).reverse should be(sorted)
      convs.getIncomingConversations.map(_.data.remoteId).toSet shouldEqual Set(RConvId("321"), RConvId("123"))
    }

    scenario("message from self on another device while app is in background") {
      convs.getIncomingConversations.foreach(c => addMessageEvents(c.data.remoteId, count = 10))
      withDelay(forAll(convs.getIncomingConversations.toSeq)(c => listMessages(c.id).size shouldEqual 11))

      val conv = convs.getIncomingConversations.last
      val convId = conv.data.remoteId

      api.onPause()
      withDelay(zmessaging.websocket.connected.currentValue shouldEqual Some(false))

      val current = events.getOrElse(convId, Nil)
      val messageEvent = textMessageEvent(Uid(), convId, SystemTimeline.next(), selfUserId, "meep")
      val updateEvent = new GenericMessageEvent(RConvId(selfUserId.str), SystemTimeline.next(), selfUserId, GenericMessage(Uid(), LastRead(convId, messageEvent.time.instant)))
      addNotification(PushNotification(Uid(), Vector(messageEvent, updateEvent), transient = false))

      api.onResume()

      withDelay(convs.getIncomingConversations.head.data.remoteId shouldEqual convId)
    }

  }

  feature("Selecting conversations") {
    lazy val selected = convs.selectedConversation
    lazy val conv1 = convs.get(1)
    lazy val conv2 = convs.get(2)

    scenario("Initially, no conversation is selected.") {
      withDelay(selected should not be empty)
      selected.get shouldEqual null
      conv1.isSelected shouldEqual false
      conv2.isSelected shouldEqual false
    }

    scenario("A selected conversation is returned as selected.") {
      convs.setSelectedConversation(conv2)
      (selected.get shouldEqual conv2).soon
      (conv1.isSelected shouldEqual false).soon
      (conv2.isSelected shouldEqual true).soon
    }

    scenario("Selecting another conversation will return the newly selected one.") {
      convs.setSelectedConversation(conv1)
      (selected.get shouldEqual conv1).soon
      (conv1.isSelected shouldEqual true).soon
      (conv2.isSelected shouldEqual false).soon
    }
 }
}
