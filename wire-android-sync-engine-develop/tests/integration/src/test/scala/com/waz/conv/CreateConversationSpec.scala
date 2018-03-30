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

import java.lang.Iterable

import com.waz.api.ConversationsList.ConversationCallback
import com.waz.api.{IConversation, ProvisionedApiSpec, Message => ApiMessage}
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.service.{RemoteZmsSpec, ZMessaging}
import com.waz.sync.client.ConnectionsClient
import com.waz.testutils.DefaultPatienceConfig
import com.waz.testutils.Implicits._
import com.waz.znet._
import com.waz.ZLog.ImplicitTag._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FeatureSpec, Matchers, OptionValues}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

class CreateConversationSpec extends FeatureSpec with Matchers with OptionValues with ProvisionedApiSpec with RemoteZmsSpec with ScalaFutures with DefaultPatienceConfig {
  implicit val timeout: Timeout = 15.seconds

  override val provisionFile = "/create_conversations.json"

  lazy val self = api.getSelf
  lazy val conversations = api.getConversations
  lazy val incomingConvs = conversations.getIncomingConversations

  lazy val Seq(auto2, auto4, auto5) = Seq("auto2", "auto4", "auto5").map { key =>
    val zms = createRemoteZms()
//    awaitUiFuture(zms.login(provisionedEmail(key), s"${key}_pass"))(10.seconds)
    zms.account.get
  }

  lazy val allUserIds = Seq("auto1", "auto2", "auto3", "auto4", "auto5", "auto6") map provisionedUserId

  lazy val auto6 = new ConnectionsClient(new ZNetClientImpl(null, null, null))//provisionedEmail("auto6"), "auto6_pass", new AsyncClientImpl(wrapper = TestClientWrapper())))

  lazy val auto4Id = Await.result(auto4.zmessaging.currentValue.flatten.get.users.getSelfUser, 15.seconds).get.id

  feature("Create conversation") {

    scenario("Create group conversation") {
      withDelay(conversations should have size 2)(25.seconds)

      val users = allUserIds.slice(1, 3).map(id => api.getUser(id.str))

      var convResult = Seq.empty[IConversation]
      conversations.createGroupConversation(users, new ConversationCallback {
        override def onConversationsFound(conversations: Iterable[IConversation]): Unit = convResult = conversations.asScala.toSeq
      })

      withDelay {
        withClue(conversations.map(_.data).mkString(",")) {
          conversations should have size 3
        }
        convResult should have size 1
      }

      val conv = convResult.headOption.value
      withDelay (listMessages(conv.id) should have size 1)

      withDelay {
        lastMessage(conv.id).get.msgType shouldEqual ApiMessage.Type.MEMBER_JOIN
        lastMessage(conv.id).get.isLocal shouldEqual true
        lastMessage(conv.id).get.members shouldEqual users.map(_.id)
      }
    }

    scenario("Load auto4 user id") { // separate scenario to not clutter logs
      info(s"auto4 id: $auto4Id")
      withDelay(auto4.storage.convsStorage.conversations should not be empty)
    }

    scenario("Create conversation with new person - connect") {
      val auto4User = api.ui.users.getUser(auto4Id)
      withDelay { auto4User.getName shouldEqual "auto4 user" }
      auto4User.getConnectionStatus shouldEqual ConnectionStatus.Unconnected

      val conv = auto4User.connect("connect message")
      conv.getType shouldEqual ConversationType.WaitForConnection
      withDelay {
        conversations should have size 4
        conversations.head shouldEqual conv // fresh conversation will be at the top
        conv.getName shouldEqual "auto4 user"
      }

      withDelay { listMessages(conv.id) should not be empty }
      info(s"msgs: ${listMessages(conv.id).map(m => (m.msgType, m.contentString))}")

      withDelay {
        self.getUser should not be null
        val conv = auto4.storage.convsStorage.conversations.find(_.id.str == self.getUser.getId)
        conv should be('defined)
        conv.get.convType shouldEqual ConversationType.Incoming
      }
    }

    scenario("Other person accepts the connection") {
      auto4.zmessaging.currentValue.flatten.get.connection.acceptConnection(UserId(self.getUser.getId))

      withDelay {
        conversations.find(_.getType == ConversationType.WaitForConnection) shouldEqual None
      }
      val conv = conversations.find(_.getId == auto4Id.str)
      conv should be('defined)
      conv.get.getType shouldEqual ConversationType.OneToOne
      withDelay {
        listMessages(conv.get.id) should have size 2
        val msg = lastMessage(conv.get.id).get
        msg.msgType shouldEqual ApiMessage.Type.MEMBER_JOIN
        msg.members shouldEqual Set(auto4Id)
        msg.isLocal shouldEqual false
      }
    }
  }

  feature("Receive conversation") {

    scenario("Receive group conversation") {
      withDelay {
        auto2.zmessaging.currentValue.flatten.get.convsStorage.conversations should not be empty
        self.getUser should not be null
      }

      val count = conversations.size
      val convIdsBefore = conversations.map(_.data.id)

      val users = Seq(allUserIds.head, allUserIds(2))
      auto2.zmessaging.currentValue.flatten.get.convsUi.createGroupConversation(ConvId(), users)

      val auto1Members = Seq(allUserIds(1), allUserIds(2))

      withDelay(conversations should have size (count + 1))
      val conv = conversations.find(c => !convIdsBefore.contains(c.data.id)).value
      conv.getType shouldEqual ConversationType.Group
      val members = conv.getUsers
      withDelay {
        members.map(_.data.id) should contain theSameElementsAs auto1Members
      }

      withDelay (listMessages(conv.id) should have size 1)

      withDelay {
        val msg = lastMessage(conv.id).get
        msg.msgType shouldEqual ApiMessage.Type.MEMBER_JOIN
        msg.members should contain theSameElementsAs auto1Members
      }

      api.zmessaging.futureValue.value.push.onHistoryLost ! ZMessaging.clock.instant()

      awaitUi(1.second)
      withDelay {
        listMessages(conv.id) should have size 1
      }
    }

    scenario("Receive connection request") {
      withDelay {
        auto5.zmessaging.currentValue.flatten.get.convsStorage.conversations should not be empty
        self.getUser should not be null
      }

      Await.result(auto5.zmessaging.currentValue.flatten.get.connection.connectToUser(UserId(self.getUser.getId), "connect message", ""), 15.seconds)

      withDelay(incomingConvs should not be empty)
      val conv = incomingConvs.head
      conv.getType shouldEqual ConversationType.Incoming
      withDelay(listMessages(conv.id) should not be empty)
      val m = lastMessage(conv.id).get
      m.msgType shouldEqual ApiMessage.Type.CONNECT_REQUEST
      m.contentString shouldEqual "connect message"

      withDelay(conv.getName shouldEqual "auto5 user")
    }

    scenario("Ignore connection request") {
      val conv = incomingConvs.head
      conv.getType shouldEqual ConversationType.Incoming
      val user = conv.getOtherParticipant

      user.ignoreConnection()

      withDelay(incomingConvs should be(empty))
      conversations.find(_.getType == ConversationType.Incoming) shouldEqual None
    }

    scenario("Accept connection request") {
      val count = conversations.size
      Await.result(auto6.createConnection(UserId(self.getUser.getId), "name", "connect message"), 15.seconds)

      withDelay(incomingConvs should not be empty)
      val conv = incomingConvs.head
      conv.getType shouldEqual ConversationType.Incoming
      val user = conv.getOtherParticipant

      user.acceptConnection()

      withDelay {
        conv.getType shouldEqual ConversationType.OneToOne
        incomingConvs should be(empty)
        conversations should have size (count + 1)
      }
      withDelay(listMessages(conv.id) should have size 2)
      val m = lastMessage(conv.id).get
      m.msgType shouldEqual ApiMessage.Type.MEMBER_JOIN
      m.members shouldEqual Set(self.getUser.id)
    }
  }
}
