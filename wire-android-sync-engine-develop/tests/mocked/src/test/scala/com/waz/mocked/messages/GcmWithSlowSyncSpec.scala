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
package com.waz.mocked.messages

import java.util.Date

import com.waz.RobolectricUtils
import com.waz.api.MockedClientApiSpec
import com.waz.mocked.{MockBackend, SystemTimeline}
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.sync.client.PushNotification
import com.waz.testutils.Implicits._
import com.waz.testutils._
import com.waz.utils.returning
import org.scalatest.{FeatureSpec, Matchers}
import com.waz.ZLog.ImplicitTag._

import scala.concurrent.duration._

class GcmWithSlowSyncSpec extends FeatureSpec with Matchers with MockedClientApiSpec with MockBackend with RobolectricUtils { self =>
  import DefaultPushBehaviour.Implicit

  val userId = UserId()
  lazy val convId = addConnection(userId).convId
  lazy val convs  = api.getConversations
  lazy val conv   = convs.getConversation(userId.str)

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    ZMessaging.currentAccounts = accounts
  }

  feature("Unread dot") {

    scenario("init") {
      addMessageEvents(convId, count = 5)

      withDelay {
        convs should not be empty
        listMessages(conv.id) should have size 8
      }
    }

    scenario("Receive gcm notification about message that is unread, then resume with slow sync") {
      withDelay { listMessages(conv.id) should have size 8 }

      zmessaging.messages.markMessageRead(conv.id, lastMessage(conv.id).get.id)
      api.onPause()
      withDelay {
        zmessaging.websocket.connected.currentValue shouldEqual Some(false)
      }
      val newMsg = textMessageEvent(Uid(), convId, new Date, userId, "meep")
      pushMessageToAppInBackground(newMsg)

      forceSlowSync()
      api.onResume()
      awaitUi(1.second)

      withDelay {
        getUnreadCount(conv.id) shouldEqual 2 // + OTR_LOST_HISTORY
      }
    }

    scenario("Receive gcm notification about message that was already read on another device, then resume with slow sync") {
      withDelay { listMessages(conv.id) should have size 10 }

      zmessaging.messages.markMessageRead(conv.id, lastMessage(conv.id).get.id)
      awaitUi(1.second) // this is to make sure that we don't overwrite lastRead on backend with some older value

      api.onPause()
      withDelay {
        zmessaging.websocket.connected.currentValue shouldEqual Some(false)
      }
      val newMsg = readNewMessageOnOtherDevice()
      pushMessageToAppInBackground(newMsg)

      forceSlowSync()
      api.onResume()
      awaitUi(1.second)

      withDelay {
        listMessages(conv.id) should have size 12
        withClue((conv.data.lastRead, listMessages(conv.id).last)) {
          conv.getUnreadCount shouldEqual 0
          getUnreadCount(conv.id) shouldEqual 0
        }
      }
    }

    def readNewMessageOnOtherDevice() = {
      returning(textMessageEvent(Uid(), convId, SystemTimeline.next(), userId, "meep meep")) { msgAdd =>
        addEvent(msgAdd)
        markAsRead(convId)
      }
    }

    def pushMessageToAppInBackground(msg: MessageEvent): Unit = {
      pushGcm(PushNotification(Uid(), Seq(msg), transient = false), selfUserId)
      awaitUi(1.second)
    }

    def forceSlowSync(): Unit = notifications = Vector.empty
  }
}
