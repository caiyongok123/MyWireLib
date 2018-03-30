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
package com.waz.connections

import akka.pattern.ask
import com.waz.api._
import com.waz.model.ConversationData.{ConversationDataDao, ConversationType}
import com.waz.model.UserData.{ConnectionStatus, UserDataDao}
import com.waz.model.{RConvId, UserId}
import com.waz.provision.ActorMessage._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.scalatest.{FeatureSpec, Matchers}
import com.waz.ZLog.ImplicitTag._

import scala.concurrent.duration._

class ConnectionSpec extends FeatureSpec with Matchers with ProvisionedApiSpec with ThreadActorSpec {
  val provisionFile = "/five_users.json"

  lazy val convs = api.getConversations

  lazy val auto1b = registerDevice("ConnectionSpec_auto1b")
  lazy val auto2 = registerDevice("ConnectionSpec_auto2")
  lazy val auto3 = registerDevice("ConnectionSpec_auto3")
  lazy val auto4 = registerDevice("ConnectionSpec_auto4")
  lazy val auto5 = registerDevice("ConnectionSpec_auto5")

  feature("Send request to other") {
    scenario("auto1: initial sync") {
      awaitUi(3.seconds)
    }

    scenario("auto2: initial sync") {
      auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
      awaitUi(1.second)
    }

    scenario("send connection request") {
      val user = api.getUser(provisionedUserId("auto2").str)
      val conv = user.connect("hello?")

      withDelay(user.getConnectionStatus shouldEqual ConnectionStatus.PendingFromUser)

      conv.getType shouldEqual ConversationType.WaitForConnection
      conv.getName shouldEqual "…"

      withDelay {
        convs should contain(conv)
        conv.getName shouldEqual "auto2 user"
      }
    }

    scenario("restart api") {

      zmessaging.db.withTransaction { implicit db =>
        ConversationDataDao.deleteAll
        UserDataDao.deleteAll
      }
      api.logout()
      withDelay(convs should be(empty))

      api.ui.onReset ! true

      awaitUi(500.milliseconds)
      login()
      withDelay {
        convs should not be empty
        convs.find(_.getType == ConversationType.WaitForConnection).map(_.getName) shouldEqual Some("auto2 user")
      }
    }

    scenario("auto2: confirm connection request") {
      val conv = convs.find(_.getType == ConversationType.WaitForConnection)
      conv should not be empty

      auto2 ? AcceptConnection(UserId(api.getSelf.getUser.getId)) should eventually(be(Successful))

      withDelay(conv.map(_.getType) shouldEqual Some(ConversationType.OneToOne))(15.seconds)
    }

    scenario("auto1: send a message after connecting") {
      val conv = convs.getConversation(provisionedUserId("auto2").str)
      withDelay {
        conv.getType shouldEqual ConversationType.OneToOne
        conv.getName shouldEqual "auto2 user"
      }

      zmessaging.convsUi.sendMessage(conv.id,"first msg")
      withDelay {
        lastMessage(conv.id).map(_.contentString) shouldEqual Some("first msg")
      }
    }

    scenario("auto4: initial sync") {
      auto4 ? Login(provisionedEmail("auto4"), "auto4_pass") should eventually(be(Successful))
      awaitUi(1.second)
    }

    scenario("send request to auto4 and cancel it") {
      val user = api.getUser(provisionedUserId("auto4").str)
      val conv = user.connect("hello?")

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.PendingFromUser
        conv.getType shouldEqual ConversationType.WaitForConnection
        convs should contain(conv)
        conv.getName shouldEqual "auto4 user"
      }

      awaitUi(1.second)

      user.cancelConnection()

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.Cancelled
        convs should not contain conv
      }
    }

    scenario("send another request to auto4") {
      idle(1.second) // wait until previous sync request for cancelling completes
      val user = api.getUser(provisionedUserId("auto4").str)
      val conv = user.connect("hello?")

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.PendingFromUser
        conv.getType shouldEqual ConversationType.WaitForConnection
        convs should contain(conv)
        conv.getName shouldEqual "auto4 user"
      }
    }
  }

  feature("Receive request from other") {
    scenario("auto3: initial sync") {
      auto3 ? Login(provisionedEmail("auto3"), "auto3_pass") should eventually(be(Successful))
      awaitUi(1.second)
    }

    scenario("auto3: send connection request") {
      awaitUi(1.second)
      auto3 ? SendRequest(provisionedUserId("auto1")) should eventually(be(Successful))
    }

    scenario("confirm connection request") {
      val user = api.getUser(provisionedUserId("auto3").str)
      val conv = convs.getConversation(user.id.str)
      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.PendingFromOther
        conv.getType shouldEqual ConversationType.Incoming
        conv.getName shouldEqual "auto3 user"
      }

      user.acceptConnection()

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.Accepted
        conv.getType shouldEqual ConversationType.OneToOne
        conv.getName shouldEqual "auto3 user"
      }
    }

    scenario("auto3: send message after connecting") {
      val conv = convs.getConversation(provisionedUserId("auto3").str)
      withDelay { listMessages(conv.id) should have size 2 }

      auto3 ? SendText(RConvId(api.getSelf.getUser.getId), "hello world") should eventually(be(Successful))

      withDelay {
        listMessages(conv.id) should have size 3
        lastMessage(conv.id).map(_.contentString) shouldEqual Some("hello world")
      }
    }

    scenario("receive request from auto5") {
      auto5 ? Login(provisionedEmail("auto5"), "auto5_pass") should eventually(be(Successful))
      awaitUi(2.seconds)
      auto5 ? SendRequest(provisionedUserId("auto1")) should eventually(be(Successful))

      val user = api.getUser(provisionedUserId("auto5").str)
      val conv = convs.getConversation(user.id.str)
      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.PendingFromOther
        conv.getType shouldEqual ConversationType.Incoming
        conv.getName shouldEqual "auto5 user"
      }
    }

    scenario(s"auto5 cancels connection request") {
      auto5 ? CancelConnection(provisionedUserId("auto1")) should eventually(be(Successful))

      val user = api.getUser(provisionedUserId("auto5").str)
      val conv = convs.getConversation(user.id.str)
      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.Cancelled
        conv.getName shouldEqual "auto5 user"
        convs should not contain conv
      }
    }
  }

  feature("multiple devices") {
    scenario("auto1b: initial sync") {
      auto1b ? Login(provisionedEmail("auto1"), "auto1_pass") should eventually(be(Successful))
      awaitUi(1.second)
    }

    scenario("send request to auto4 and cancel it from other device") {
      val user = api.getUser(provisionedUserId("auto4").str)
      val conv = user.connect("hello?")

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.PendingFromUser
        conv.getType shouldEqual ConversationType.WaitForConnection
        convs should contain(conv)
        conv.getName shouldEqual "auto4 user"
      }

      awaitUi(1.second)
      api.onPause()
      withDelay(zmessaging.websocket.connected.currentValue shouldEqual Some(false))
      awaitUi(1.second)

      auto1b ? CancelConnection(provisionedUserId("auto4")) should eventually(be(Successful))

      awaitUi(2.seconds)
      api.onResume()

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.Cancelled
        convs should not contain conv
      }

      awaitUi(1.second)

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.Cancelled
        convs should not contain conv
      }
    }

    scenario("send request to auto4 from other device") {
      val user = api.getUser(provisionedUserId("auto4").str)
      val conv = user.getConversation

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.Cancelled
        convs should not contain conv
      }

      auto1b ? SendRequest(provisionedUserId("auto4"))

      withDelay {
        user.getConnectionStatus shouldEqual ConnectionStatus.PendingFromUser
        conv.getType shouldEqual ConversationType.WaitForConnection
        convs should contain(conv)
        conv.getName shouldEqual "auto4 user"
      }
    }
  }
}
