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
package com.waz.users

import akka.pattern.ask
import com.waz.api._
import com.waz.model.ConversationData.ConversationDataDao
import com.waz.model.MessageData.MessageDataDao
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.{ConvId, RConvId, UserId}
import com.waz.provision.ActorMessage._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.utils.wrappers.DB
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.duration._
import scala.util.Random

class BlockingSpec extends FeatureSpec with Matchers with ProvisionedApiSpec with ThreadActorSpec{

  override val provisionFile = "/two_users_connected.json"

  implicit def zmsDb: DB = zmessaging.db.dbHelper.getWritableDatabase

  val msgHex = Random.nextInt().toHexString

  lazy val convs = api.getConversations
  lazy val self = api.getSelf

  lazy val otherUserId = UserId(concurrent.Await.result(auto2 ? GetUser, 5.seconds).asInstanceOf[Successful].response)

  lazy val auto2 = registerDevice("BlockingSpec_auto2")

  feature("Block") {

    scenario("remote process") {
      auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
      awaitUi(3.seconds)
    }

    scenario("sync all") {
      withDelay(convs should not be empty)
      awaitUi(3.seconds)
    }

    scenario("Block contact") {
      otherUserId should not be null
      val user = api.ui.users.getUser(otherUserId)
      user.block()

      withDelay(user.getConnectionStatus shouldEqual ConnectionStatus.Blocked)
    }

    scenario("We should not see a conversation with blocked contact") {
      withDelay {
        convs.map(_.getId) should not contain otherUserId.str
      }
    }

    scenario("We should not receive any messages from blocked contact") {
      val lastMsg = MessageDataDao.last(ConvId(otherUserId.str))
      val lastEventTime = ConversationDataDao.getById(ConvId(otherUserId.str)).get.lastEventTime
      info(s"lastEventTime: $lastEventTime")

      auto2 ! SendText(RConvId(self.getUser.getId), s"test message 1 $msgHex")
      auto2 ! SendText(RConvId(self.getUser.getId), s"test message 2 $msgHex")
      auto2 ! SendText(RConvId(self.getUser.getId), s"test message 3 $msgHex")
      auto2 ! SendText(RConvId(self.getUser.getId), s"test message 4 $msgHex")

      awaitUi(5.seconds)
      MessageDataDao.last(ConvId(otherUserId.str)) shouldEqual lastMsg
      ConversationDataDao.getById(ConvId(otherUserId.str)).get.lastEventTime shouldEqual lastEventTime
    }
  }

  feature("Unblock") {

    scenario("Unblock contact") {
      val user = api.ui.users.getUser(otherUserId)
      val conv = user.unblock()

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.Accepted
        convs.contains(conv)
      }
    }
  }
}
