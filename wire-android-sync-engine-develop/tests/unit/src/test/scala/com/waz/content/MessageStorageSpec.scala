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
package com.waz.content

import com.waz.RobolectricUtils
import com.waz.api.Message
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.testutils.MockZMessaging
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest._

@Ignore class MessageStorageSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils with ScalaFutures { test =>
  import com.waz.threading.Threading.Implicits.Background
  implicit val defaultPatience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  lazy val conv = ConversationData(ConvId(), RConvId(), None, UserId(), ConversationType.Group)
  lazy val zms = new MockZMessaging() {
    convsStorage.insert(conv).futureValue
  }

  lazy val messages = zms.messagesStorage

  feature("Unread count") {

    scenario("Receive new message and last read with older event at the same time") {
      val msg = messages.addMessage(MessageData(MessageId(), conv.id, Message.Type.TEXT, UserId())).futureValue
      messages.addMessage(MessageData(MessageId(), conv.id, Message.Type.TEXT, UserId()))
      messages.addMessage(MessageData(MessageId(), conv.id, Message.Type.TEXT, UserId()))
      zms.convsContent.updateConversationLastRead(conv.id, msg.time).futureValue shouldBe 'defined

      withDelay {
        zms.convsStorage.get(conv.id).map(_.map(_.unreadCount)).futureValue shouldEqual Some(2)
      }
    }
  }
}
