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

import com.waz.RobolectricUtils
import com.waz.api.impl.conversation.BaseConversation
import com.waz.api.{CoreList, IConversation, MockedClientApiSpec}
import com.waz.mocked.MockBackend.DefaultTimeline
import com.waz.mocked.{MockBackend, PushBehaviour, SystemTimeline}
import com.waz.model._
import com.waz.sync.client.MessagesClient.OtrMessage
import com.waz.sync.client.OtrClient.MessageResponse
import com.waz.sync.client.UserSearchClient.UserSearchEntry
import com.waz.testutils.Implicits._
import com.waz.testutils.{HasId, ReusableCountDownLatch}
import com.waz.threading.CancellableFuture
import com.waz.utils._
import com.waz.znet.ZNetClient.ErrorOrResponse
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Inside, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class ClearConversationSpec extends FeatureSpec with Matchers with Inside with BeforeAndAfterAll with MockedClientApiSpec with MockBackend with RobolectricUtils {
  implicit def pushing = PushBehaviour.Push

  lazy val convs = api.getConversations
  lazy val archived = convs.getArchivedConversations
  lazy val friends = SeqMap((1 to 30) map (_ => addConnection()))(_.to, _.convId)
  lazy val groups = SeqMap(friends.keys.sliding(2, 2).map(fs => addGroupConversation(fs).remoteId -> fs.toSet).toSeq)(_._1, _._2)

  val SyncWindow = 75

  override def beforeAll(): Unit = {
    println(s"init friends: ${friends.size}")
    println(s"init groups: ${groups.size}")

    Seq(4, 5).flatMap(groups.get).foreach { case (id, users) => addMessageEvents(id, count = 10, from = users.head) }

    addMessageEvents(groups.keys(4), count = 2 * SyncWindow, from = groups.at(4).head)
    clearConversation(groups.keys(4), time = DefaultTimeline)

    addMessageEvents(groups.keys(5), count = 2 * SyncWindow, from = groups.at(5).head)
    clearConversation(groups.keys(5), time = DefaultTimeline)
    addMessageEvents(groups.keys(5), count = SyncWindow + 10, from = groups.at(5).head)

    super.beforeAll
  }

  scenario("initial sync") {
    withDelay {
      convs should have size (friends.size + groups.size - 2)
    } (10.seconds)

    friends.foreach((u, c) => addMessageEvents(c, count = 10, from = u, timeline = Some(SystemTimeline)))
    searchResults += SearchQuery.TopPeople -> friends.keys.map(id => UserSearchEntry(id, id.str, Some(0), Handle(id.str)))

    groups.filterNot(Set(groups keys 4, groups keys 5)).foreach((id, users) => addMessageEvents(id, count = 10, from = users.head, timeline = Some(SystemTimeline)))
  }

  feature("Clearing conversations") {
    scenario("Group conversation, local device, clear") {
      val (convId, others, conv) = usingGroupConversation(0)
      withDelay(listMessages(conv.id) should have size 11)

      conv.clear()
      conv.shouldBeCleared()

      addMessageEvents(convId, count = 1, from = others.head)
      conv.shouldBeActiveWith(messageCount = 1)()
    }

    scenario("Group conversation, local device, leave") {
      val (convId, others, conv) = usingGroupConversation(1)
      withDelay(listMessages(conv.id) should have size 11)

      conv.leave()
      withDelay {
        conv shouldBe 'archived
        conv should not be 'active
        listMessages(conv.id) should have size 12 // got leave message
      }
    }

    scenario("Clear previously left group conv") {
      val (_, _, conv) = usingGroupConversation(1)
      val msgs = listMessages(conv.id)
      withDelay(listMessages(conv.id) should have size 12)

      conv.clear()
      conv.shouldBeInactiveAndCleared()
    }

    scenario("One-to-one conversation, local device, clear") {
      val (convId, friend, conv) = usingOneToOneConversation(0)
      val msgs = listMessages(conv.id)
      awaitUi(2.seconds)
      withDelay(listMessages(conv.id) should have size 11)

      conv.clear()
      conv.shouldBeCleared()

      addMessageEvents(convId, count = 1, from = friend)
      conv.shouldBeActiveWith(messageCount = 1)()
    }

    scenario("Group conversation, other device, clear") {
      val (convId, others, conv) = usingGroupConversation(2)
      withDelay(listMessages(conv.id) should have size 11)

      clearConversation(convId)
      conv.shouldBeCleared()

      addMessageEvents(convId, count = 5, from = others.head)
      conv.shouldBeActiveWith(messageCount = 5)()
    }

    scenario("Group conversation, other device, leave") {
      val (convId, others, conv) = usingGroupConversation(3)
      withDelay { listMessages(conv.id) should have size 11 }

      leaveGroupConversation(convId, selfUserId)

      withDelay {
        conv shouldBe 'archived
        conv should not be 'active
        listMessages(conv.id) should have size 12 // got leave message
      }
    }

    scenario("Group conversation, get kicked out") {
      val (convId, others, conv) = usingGroupConversation(10)
      withDelay {
        listMessages(conv.id) should have size 11
        getUnreadCount(conv.id) shouldEqual 10
      }

      removeUsersFromGroupConversation(Seq(selfUserId), convId, others.head, SystemTimeline)

      conv.shouldBeInactiveButNotCleared(messageCount = 12)(unreadCount = 11)
    }

    scenario("One-to-one conversation, other device") {
      val (convId, friend, conv) = usingOneToOneConversation(1)
      withDelay(listMessages(conv.id) should have size 11)

      clearConversation(convId)
      conv.shouldBeCleared()

      addMessageEvents(convId, count = 3, from = friend)
      conv.shouldBeActiveWith(messageCount = 3)()
    }

    scenario("One-to-one conversation, local device, only unarchive") {
      val (convId, friend, conv) = usingOneToOneConversation(2)
      withDelay(listMessages(conv.id) should have size 11)

      conv.clear()
      conv.shouldBeCleared()

      conv.setArchived(false)
      conv.shouldBeActiveWith(messageCount = 0)()
    }

    scenario("Sync cleared group conversation") {
      val (convId, others, conv) = usingGroupConversation(4)
      conv.shouldBeCleared()
    }

    scenario("Sync cleared group conversation with unobserved message") {
      val (convId, others, conv) = usingGroupConversation(5)

      withDelay { // history not available so the messages that would unarchive this conv are also not there on this device
        listMessages(conv.id) should have size 1 // "started using this device"
        getUnreadCount(conv.id) shouldBe 0
        conv shouldBe 'archived
        conv shouldBe 'active
        inside(conv) { case baseConv: BaseConversation => baseConv.data.completelyCleared shouldBe false }
        convs.getConversationIndex(conv.getId) shouldEqual -1
        archived.getConversationIndex(conv.getId) should be >= 0
        conv.checkSearch(expectedToBeFound = true)
      }
    }

    scenario("Clear conversation while there are unsync'd local messages") {
      val (convId, others, conv) = usingGroupConversation(6)
      withDelay(listMessages(conv.id) should have size 11)

      latch.ofSize(1) { l =>
        (1 to 5).foreach(i => zmessaging.convsUi.sendMessage(conv.id, s"meep: $i"))
        awaitUi(1.second)
        conv.clear()
        awaitUi(1.second)
        l.countDown()
      }
      conv.shouldBeCleared()
    }

    scenario("Group conversation, leave and clear") {
      val (convId, others, conv) = usingGroupConversation(7)
      val msgs = listMessages(conv.id)
      withDelay(listMessages(conv.id) should have size 11)

      conv.leave()
      conv.clear()

      conv.shouldBeInactiveAndCleared()
    }
  }

  def usingGroupConversation(n: Int) = {
    info(s"conversation ID: ${groups.keys(n)}")
    (groups.keys(n), groups.at(n), convs.getConversation(groups.keys(n).str))
  }

  def usingOneToOneConversation(n: Int) = {
    info(s"user ID: ${friends.keys(n)}, conversation ID: ${friends.at(n)}")
    (friends.at(n), friends.keys(n), api.getUser(friends.keys(n).str).getConversation)
  }

  implicit class EnrichedConversation(val conv: IConversation) {

    def shouldBeCleared(): Unit = withDelay {
      val msgs = listMessages(conv.id)
      withClue(msgs.map(m => (m.contentString, m.msgType))) {
        msgs shouldBe empty
      }
      getUnreadCount(conv.id) shouldBe 0
      conv shouldBe 'archived
      conv shouldBe 'active
      inside(conv) { case baseConv: BaseConversation => baseConv.data.completelyCleared shouldBe true }
      convs.getConversationIndex(conv.getId) shouldEqual -1
      archived.getConversationIndex(conv.getId) shouldEqual -1
      checkSearch(expectedToBeFound = true)
    }

    def shouldBeActiveWith(messageCount: Int)(unreadCount: Int = messageCount): Unit = withDelay {
      listMessages(conv.id) should have size messageCount
      getUnreadCount(conv.id) shouldEqual unreadCount
      conv should not be 'archived
      conv shouldBe 'active
      inside(conv) { case baseConv: BaseConversation =>
        if (messageCount > 0) baseConv.data.completelyCleared shouldBe false
        else baseConv.data shouldBe 'completelyCleared
      }
      convs.getConversationIndex(conv.getId) should be >= 0
      archived.getConversationIndex(conv.getId) shouldEqual -1
      checkSearch(expectedToBeFound = true)
    }

    def shouldBeInactiveAndCleared(): Unit = withDelay {
      listMessages(conv.id) should have size 0
      getUnreadCount(conv.id) shouldBe 0
      conv shouldBe 'archived
      conv should not be 'active
      inside(conv) { case baseConv: BaseConversation => baseConv.data.completelyCleared shouldBe true }
      convs.getConversationIndex(conv.getId) shouldEqual -1
      archived.getConversationIndex(conv.getId) shouldEqual -1
      checkSearch(expectedToBeFound = false)
    }

    def shouldBeInactiveButNotCleared(messageCount: Int)(unreadCount: Int = messageCount): Unit = withDelay {
      listMessages(conv.id) should have size messageCount
      getUnreadCount(conv.id) shouldEqual unreadCount
      conv should not be 'archived
      conv should not be 'active
      inside(conv) { case baseConv: BaseConversation => baseConv.data.completelyCleared shouldBe false }
      convs.getConversationIndex(conv.getId) should be >= 0
      archived.getConversationIndex(conv.getId) shouldEqual -1
      checkSearch(expectedToBeFound = true)
    }

    def checkSearch(expectedToBeFound: Boolean): Unit = conv.getType match {
      case IConversation.Type.GROUP =>
        inspect(api.search().getGroupConversations("", 100), ConvId(conv.getId), expectedToBeFound)
      case IConversation.Type.ONE_TO_ONE =>
        inspect(api.search().getConnectionsByName("", 30, Array.empty), UserId(conv.getOtherParticipant.getId), expectedToBeFound)
      case tpe => fail(s"unexpected conversation type: $tpe")
    }

    private def inspect[A](l: CoreList[A], t: Any, expectedToBeFound: Boolean)(implicit ex: HasId[A]): Unit =
      if (expectedToBeFound) {
        withDelay(l.asScala.map(e => ex.idOf(e)) should contain(t))
      } else {
        33.times {
          l.asScala.map(e => ex.idOf(e)) should not(contain(t))
          awaitUi(67.millis)
        }
      }
  }

  private val latch = new ReusableCountDownLatch
  import com.waz.threading.Threading.Implicits.Background
  override def postMessage(convId: RConvId, msg: OtrMessage, ignoreMissing: Boolean): ErrorOrResponse[MessageResponse] = CancellableFuture(latch.await(1.minute)).flatMap(_ => super.postMessage(convId, msg, ignoreMissing))
  override def postConversationState(convId: RConvId, updated: ConversationState): ErrorOrResponse[Boolean] = CancellableFuture(latch.await(1.minute)).flatMap(_ => super.postConversationState(convId, updated))
}
