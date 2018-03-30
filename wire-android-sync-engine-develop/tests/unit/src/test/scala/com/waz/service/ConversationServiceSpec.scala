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
package com.waz.service

//import java.util.Date

import com.waz._
//import com.waz.api.Message
//import com.waz.content.GlobalDatabase
//import com.waz.model.ConversationData.{ConversationDataDao, ConversationType}
//import com.waz.model._
//import com.waz.sync.client.ConversationsClient.ConversationResponse
//import com.waz.testutils.{EmptySyncService, MockZMessaging}
//import com.waz.threading.Threading
//import com.waz.utils._
//import com.waz.utils.wrappers.DB
//import org.robolectric.Robolectric
import org.scalatest._

//import scala.concurrent.duration._
//import scala.concurrent.{Await, Future}

@Ignore class ConversationServiceSpec extends FeatureSpec with Matchers with BeforeAndAfter with GivenWhenThen with RobolectricTests with RobolectricUtils { test =>
//  implicit lazy val dispatcher = Threading.Background
//  lazy val globalStorage = new GlobalDatabase(Robolectric.application)
//
//  val timeout = 5.seconds
//
//  lazy val selfUser = UserData("self user")
//  lazy val user1 = UserData("user 1")
//  lazy val user2 = UserData("user 1")
//  lazy val user3 = UserData("user 3")
//
//  implicit def db: DB = service.db.dbHelper.getWritableDatabase
//
//  var convSync = None: Option[ConvId]
//  var convNameSync = None: Option[ConvId]
//  var convStateSync = None: Option[ConvId]
//  var convMemberJoinSync = None: Option[(ConvId, Seq[UserId])]
//
//  var convMemberLeaveSync = None: Option[(ConvId, UserId)]
//
//  lazy val service = new MockZMessaging(selfUserId = selfUser.id) {
//    override lazy val sync = new EmptySyncService {
//      override def postConversationName(id: ConvId, n: String) = {
//        convNameSync = Some(id)
//        super.postConversationName(id, n)
//      }
//      override def postConversationMemberJoin(id: ConvId, members: Seq[UserId]) = {
//        convMemberJoinSync = Some((id, members))
//        super.postConversationMemberJoin(id, members)
//      }
//      override def postConversationMemberLeave(id: ConvId, member: UserId) = {
//        convMemberLeaveSync = Some((id, member))
//        super.postConversationMemberLeave(id, member)
//      }
//      override def postConversationState(id: ConvId, s: ConversationState) = {
//        convStateSync = Some(id)
//        super.postConversationState(id, s)
//      }
//      override def postConversation(id: ConvId, u: Seq[UserId], n: Option[String], t: Option[TeamId]) = {
//        convSync = Some(id)
//        super.postConversation(id, u, n, t)
//      }
//      override def syncConversations(ids: Set[ConvId], dependsOn: Option[SyncId]) = {
//        convSync = Some(ids.toSeq.head)
//        super.syncConversations(ids, dependsOn)
//      }
//    }
//
//    insertUsers(Seq(selfUser, user1, user2, user3))
//  }
//
//  before {
//    convSync = None
//    convNameSync = None
//    convStateSync = None
//    convMemberJoinSync = None
//    convMemberLeaveSync = None
//  }
//
//  import service._
//
//  def assertMessage(msgOpt: Option[MessageData], msgType: Message.Type, convId: Option[ConvId] = None,
//                    name: Option[String] = None, userId: Option[UserId] = None, id: Option[MessageId] = None,
//                    members: Option[Seq[UserId]] = None): MessageData = {
//
//    msgOpt should be('defined)
//    val msg = msgOpt.get
//
//    withClue(msg.toString) {
//      msg.msgType shouldEqual msgType
//      convId foreach { msg.convId shouldEqual _ }
//      name foreach { name => msg.name shouldEqual Some(name) }
//      userId foreach { msg.userId shouldEqual _ }
//      members foreach { msg.members shouldEqual _.toSet } // TODO: check message entries
//    }
//
//    msg
//  }
//
//  feature("Conversation name") {
//
//    scenario("Try to change name for OneToOne conversation") {
//      val conv1 = ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.OneToOne)
//      convsStorage.insert(conv1)
//
//      Await.result(convsUi.setConversationName(conv1.id, "new name"), 25.seconds) should be(None)
//
//      withDelay {
//        ConversationDataDao.getById(conv1.id).map(_.name) should be(Some(Some("convName")))
//        convNameSync should be(None)
//      }
//    }
//
//    scenario("Change conversation name for group conversation") {
//      Given("existing conversation")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//
//      When("setConversationName is called")
//      Await.result(convsUi.setConversationName(conv.id, "new name"), 15.seconds) should be('defined)
//
//      Then("conversation name is updated in local db")
//      convNameSync should be(Some(conv.id))
//      Await.result(convsContent.convById(conv.id), timeout).map(_.name) should be(Some(Some("new name")))
//      withDelay {
//        ConversationDataDao.getById(conv.id).map(_.name) should be(Some(Some("new name")))
//      }
//
//      And("local message is generated with correct data")
//      val msg = assertMessage(lastMessage(conv.id), Message.Type.RENAME, Some(conv.id), Some("new name"), Some(selfUser.id))
//
//      val messagesCount = listMessages(conv.id).size
//
//      When("rename event is received on push channel")
//      service.dispatchEvent(RenameConversationEvent(conv.remoteId, new Date(), selfUser.id, "new name"))
//
//      Thread.sleep(500) // push event is handled async so we need to give it some time
//
//      Then("message status and time is updated")
//      assertMessage(lastMessage(conv.id), Message.Type.RENAME, Some(conv.id), Some("new name"), Some(selfUser.id), id = Some(msg.id))
//
//      listMessages(conv.id) should have size messagesCount
//    }
//
//    scenario("Change conversation name twice before sync notification is received") {
//      Given("existing conversation")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//
//      When("setConversationName is called")
//      Await.result(convsUi.setConversationName(conv.id, "new name"), 15.seconds) should be('defined)
//
//      Then("conversation name is updated in local db")
//      convNameSync should be(Some(conv.id))
//      withDelay {
//        ConversationDataDao.getById(conv.id).map(_.name) should be(Some(Some("new name")))
//      }
//
//      And("local message is generated with correct data")
//      val msg = assertMessage(lastMessage(conv.id), Message.Type.RENAME, Some(conv.id), Some("new name"), Some(selfUser.id))
//
//      val messagesCount = listMessages(conv.id).size
//      convNameSync = None
//
//      When("setConversationName is called again")
//      Await.result(convsUi.setConversationName(conv.id, "new name 1"), 10.seconds) should be('defined)
//
//      Then("conversation name is updated in local db")
//      convNameSync should be(Some(conv.id))
//      withDelay {
//        ConversationDataDao.getById(conv.id).map(_.name) should be(Some(Some("new name 1")))
//      }
//
//      And("local message is only updated")
//      assertMessage(lastMessage(conv.id), Message.Type.RENAME, Some(conv.id), Some("new name 1"), Some(selfUser.id), id = Some(msg.id))
//
//      listMessages(conv.id).size shouldEqual messagesCount
//    }
//  }
//
//  feature("Adding conversation members") {
//
//    scenario("Can not add members to the conversation if user is not a member himself") {
//      Given("existing conversation")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//
//      When("addConversationMembers is called")
//      val members = Await.result(convsUi.addConversationMembers(conv.id, Seq(user1.id)), 15.seconds)
//
//      Then("No members are added")
//      members should be(empty)
//
//      And("No message is generated")
//      listMessages(conv.id) should be(empty)
//    }
//
//    scenario("Can not add members to the conversation if user is not active member himself") {
//      Given("existing conversation")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//
//      And("self user is inactive member")
//      addMember(conv.id, selfUser.id)
//      removeMember(conv.id, selfUser.id)
//
//      When("addConversationMembers is called")
//      val members = Await.result(convsUi.addConversationMembers(conv.id, Seq(user1.id)), 15.seconds)
//
//      Then("No new members are added")
//      members should be(empty)
//
//      And("No message is generated")
//      listMessages(conv.id) should be(empty)
//    }
//
//    scenario("Can only add members to group conversation") {
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//      (ConversationType.values - ConversationType.Group) foreach { convType =>
//        val conv1 = ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, convType)
//        addMember(conv.id, selfUser.id)
//        Await.result(convsUi.addConversationMembers(conv1.id, Seq(user1.id)), 15.seconds) should be(empty)
//      }
//    }
//
//    scenario("Add one member to conversation") {
//      Given("existing conversation")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//
//      And("self user is active member")
//      addMember(conv.id, selfUser.id)
//
//      When("addConversationMembers is called")
//      val members = Await.result(convsUi.addConversationMembers(conv.id, Seq(user1.id)), 15.seconds)
//
//      Then("New member is added")
//      members should have size 1
//      val member = members.head
//      member.userId shouldEqual user1.id
//
//      listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id)
//      listMembers(conv.id).map(_.userId).toSet shouldEqual Set(selfUser.id, user1.id)
//
//      And("Member join message is generated")
//      listMessages(conv.id) should have size 1
//      val msg = assertMessage(lastMessage(conv.id), Message.Type.MEMBER_JOIN, Some(conv.id), userId = Some(selfUser.id), members = Some(Seq(user1.id)))
//
//      And("sync is requested")
//      convMemberJoinSync shouldEqual Some((conv.id, Seq(user1.id)))
//
//      When("push notification is received for member join")
//      service.dispatchEvent(MemberJoinEvent(conv.remoteId, new Date(), selfUser.id, Seq(user1.id)))
//      Thread.sleep(500)
//
//      Then("message is updated")
//      listMessages(conv.id) should have size 1
//      assertMessage(lastMessage(conv.id), Message.Type.MEMBER_JOIN, Some(conv.id), userId = Some(selfUser.id), id = Some(msg.id), members = Some(Seq(user1.id)))
//    }
//
//    scenario("Add multiple members to conversation") {
//      Given("existing conversation")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//      val users = List(user1, user2, user3)
//
//      And("self user is active member")
//      addMember(conv.id, selfUser.id)
//
//      When("addConversationMembers is called")
//      val members = Await.result(convsUi.addConversationMembers(conv.id, users.map(_.id)), 15.seconds)
//
//      Then("New member is added")
//      members should have size users.size
//      members.map(_.userId).toSet shouldEqual users.map(_.id).toSet
//
//      listMembers(conv.id).map(_.userId).toSet shouldEqual (selfUser.id :: users.map(_.id)).toSet
//      listActiveMembers(conv.id).toSet shouldEqual (selfUser.id :: users.map(_.id)).toSet
//
//      And("Member join message is generated")
//      listMessages(conv.id) should have size 1
//      val msg = assertMessage(lastMessage(conv.id), Message.Type.MEMBER_JOIN, Some(conv.id), userId = Some(selfUser.id), members = Some(users.map(_.id)))
//
//      And("sync is requested")
//      convMemberJoinSync shouldEqual Some((conv.id, users.map(_.id)))
//
//      When("push notification is received for member join")
//      service.dispatchEvent(MemberJoinEvent(conv.remoteId, new Date(), selfUser.id, users.map(_.id)))
//      Thread.sleep(500)
//
//      Then("message is updated")
//      listMessages(conv.id) should have size 1
//      assertMessage(lastMessage(conv.id), Message.Type.MEMBER_JOIN, Some(conv.id), userId = Some(selfUser.id), id = Some(msg.id), members = Some(users.map(_.id)))
//    }
//
//    scenario("Add members twice before push notification is received") {
//      Given("existing conversation")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//
//      And("self user is active member")
//      addMember(conv.id, selfUser.id)
//
//      When("addConversationMembers is called twice")
//      Await.result(Future.sequence(Seq(
//        convsUi.addConversationMembers(conv.id, Seq(user1.id)),
//        convsUi.addConversationMembers(conv.id, Seq(user2.id)))), timeout)
//
//      Then("Two members are added")
//      listMembers(conv.id).map(_.userId).toSet shouldEqual Set(selfUser.id, user1.id, user2.id)
//      listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id, user2.id)
//
//      And("Single member join message is generated with both users")
//      listMessages(conv.id) should have size 1
//      val msg = assertMessage(lastMessage(conv.id), Message.Type.MEMBER_JOIN, Some(conv.id), userId = Some(selfUser.id), members = Some(Seq(user1.id, user2.id)))
//
//      When("push notification is received for only one user")
//      Thread.sleep(500)
//      service.dispatchEvent(MemberJoinEvent(conv.remoteId, (msg.time - 1.milli).javaDate, selfUser.id, Seq(user1.id)))
//      Thread.sleep(500)
//
//      Then("message is split in two, with first one updated to push result")
//      val msgs = listMessages(conv.id)
//      msgs should have size 2
//      assertMessage(Some(msgs(0)), Message.Type.MEMBER_JOIN, Some(conv.id), userId = Some(selfUser.id), id = Some(msg.id), members = Some(Seq(user1.id)))
//      assertMessage(Some(msgs(1)), Message.Type.MEMBER_JOIN, Some(conv.id), userId = Some(selfUser.id), members = Some(Seq(user2.id)))
//    }
//
//    scenario("Create conversation on incoming member join event containing self") {
//      deleteAllConvs()
//      listConvs should have size 0
//      val ev = MemberJoinEvent(RConvId(), new Date, user1.id, Seq(selfUser.id))
//      Await.result(conversations.processConversationEvent(ev, selfUser.id), timeout)
//
//      getConv(ev.convId) should be('defined)
//      val conv = getConv(ev.convId).get
//      conv.creator shouldEqual user1.id
//      conv.convType shouldEqual ConversationType.Group
//      listMembers(conv.id).map(_.userId).toSet shouldEqual Set(selfUser.id, user1.id)
//
//      listMessages(conv.id) should have size 1
//      assertMessage(lastMessage(conv.id), Message.Type.MEMBER_JOIN, Some(conv.id), userId = Some(user1.id), members = Some(Seq(selfUser.id)))
//
//      convSync shouldEqual Some(conv.id)
//    }
//
//    scenario("Create conversation on incoming member join event containing self and other users") {
//      deleteAllConvs()
//      val ev = MemberJoinEvent(RConvId(), new Date, user1.id, Seq(user2.id, selfUser.id))
//      Await.result(conversations.processConversationEvent(ev, selfUser.id), timeout)
//
//      getConv(ev.convId) should be('defined)
//      val conv = getConv(ev.convId).get
//      conv.creator shouldEqual user1.id
//      conv.convType shouldEqual ConversationType.Group
//      listMembers(conv.id).map(_.userId).toSet shouldEqual Set(selfUser.id, user1.id, user2.id)
//
//      listMessages(conv.id) should have size 1
//      assertMessage(lastMessage(conv.id), Message.Type.MEMBER_JOIN, Some(conv.id), userId = Some(user1.id), members = Some(Seq(user2.id, selfUser.id)))
//
//      convSync shouldEqual Some(conv.id)
//    }
//  }
//
//  feature("Removing conversation members") {
//
//    scenario("Can not remove members if user is not a member himself") {
//      Given("existing conversation with member")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//      addMember(conv.id, user1.id)
//
//      When("removeConversationMembers is called")
//      val res = Await.result(convsUi.removeConversationMember(conv.id, user1.id), 15.seconds)
//
//      Then("User is not removed")
//      res shouldBe None
//
//      And("No message is generated")
//      listMessages(conv.id) should be(empty)
//    }
//
//    scenario("Can not remove members if user is not active member himself") {
//      Given("existing conversation with member")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//      addMember(conv.id, user1.id)
//
//      And("self user is inactive member")
//      addMember(conv.id, selfUser.id)
//      removeMember(conv.id, selfUser.id)
//
//      When("removeConversationMember is called")
//      val res = Await.result(convsUi.removeConversationMember(conv.id, user1.id), 15.seconds)
//
//      Then("User is not removed")
//      res shouldBe None
//
//      And("No message is generated")
//      listMessages(conv.id) should be(empty)
//    }
//
//    scenario("Can only remove members from group conversation") {
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//      (ConversationType.values - ConversationType.Group) foreach { convType =>
//        val conv1 = ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, convType)
//        addMember(conv.id, selfUser.id)
//        addMember(conv.id, user1.id)
//        Await.result(convsUi.removeConversationMember(conv1.id, user1.id), 15.seconds) shouldBe None
//      }
//    }
//
//    scenario("Can not remove user who is not a member of conversation") {
//      Given("existing conversation with active self member")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//      addMember(conv.id, selfUser.id)
//
//      When("removeConversationMember is called for some random user")
//      val res = Await.result(convsUi.removeConversationMember(conv.id, user1.id), 15.seconds)
//
//      Then("User is not removed")
//      res shouldBe None
//
//      And("No message is generated")
//      listMessages(conv.id) should be(empty)
//    }
//
//    scenario("Can not remove user who is not an active member of conversation") {
//      Given("existing conversation")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//      removeMember(conv.id, user1.id)
//      addMember(conv.id, selfUser.id)
//
//      When("removeConversationMember is called for some random user")
//      val res = Await.result(convsUi.removeConversationMember(conv.id, user1.id), 15.seconds)
//
//      Then("User is not removed")
//      res shouldBe None
//
//      And("No message is generated")
//      listMessages(conv.id) should be(empty)
//    }
//
//    scenario("Remove existing member") {
//      Given("existing conversation with added user")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//      addMember(conv.id, user1.id)
//      addMember(conv.id, selfUser.id)
//
//      When("removeConversationMember is called")
//      val res = Await.result(convsUi.removeConversationMember(conv.id, user1.id), 15.seconds)
//
//      Then("sync has been initiated")
//      res should not be empty
//
//      And("User is removed")
//      listActiveMembers(conv.id) should not contain user1.id
//
//      And("Member leave message is generated")
//      listMessages(conv.id) should have size 1
//      val msg = assertMessage(lastMessage(conv.id), Message.Type.MEMBER_LEAVE, Some(conv.id), userId = Some(selfUser.id), members = Some(Seq(user1.id)))
//
//      And("sync is requested")
//      convMemberLeaveSync shouldEqual Some((conv.id, user1.id))
//
//      When("push notification is received for member leave")
//      service.dispatchEvent(MemberLeaveEvent(conv.remoteId, new Date(), selfUser.id, Seq(user1.id)))
//      Thread.sleep(500)
//
//      Then("message is updated")
//      listMessages(conv.id) should have size 1
//      assertMessage(lastMessage(conv.id), Message.Type.MEMBER_LEAVE, Some(conv.id), userId = Some(selfUser.id), id = Some(msg.id), members = Some(Seq(user1.id)))
//    }
//
//    scenario("Remove just added user - before join notification is received") {
//      Given("existing conversation")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//      addMember(conv.id, selfUser.id)
//
//      And("user is just added to conversation")
//      Await.result(convsUi.addConversationMembers(conv.id, Seq(user1.id)), 15.seconds) should not be empty
//
//      When("removeConversationMember is called")
//      val res = Await.result(convsUi.removeConversationMember(conv.id, user1.id), 15.seconds)
//
//      Then("sync has been initiated")
//      res should not be empty
//
//      And("User is removed")
//      listActiveMembers(conv.id) should not contain user1.id
//
//      And("Generated join message is removed - no messages are present in conversation")
//      listMessages(conv.id) should be(empty)
//
//      And("leave sync is requested")
//      convMemberLeaveSync shouldEqual Some((conv.id, user1.id))
//    }
//
//    scenario("Add just removed user - before remove notification is received") {
//      Given("existing conversation")
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//      addMember(conv.id, user1.id)
//      addMember(conv.id, selfUser.id)
//
//      When("removeConversationMember is called")
//      Await.result(convsUi.removeConversationMember(conv.id, user1.id), 15.seconds) should not be empty
//
//      And("add member is called for the same user")
//      Await.result(convsUi.addConversationMembers(conv.id, Seq(user1.id)), 15.seconds) should not be empty
//
//      Then("User is a member")
//      listActiveMembers(conv.id) should contain(user1.id)
//
//      And("Generated leave message is removed - no messages are present in conversation")
//      listMessages(conv.id) should be(empty)
//
//      And("leave sync is requested")
//      convMemberLeaveSync shouldEqual Some((conv.id, user1.id))
//    }
//  }
//
//  feature("leaving conversation") {
//
//    scenario("leave active conversation") {
//      val conv = insertConv(ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group))
//      addMember(conv.id, selfUser.id)
//
//      val msgsCount = listMessages(conv.id).size
//      Await.result(convsUi.leaveConversation(conv.id), 15.seconds) should not be empty
//
//      listActiveMembers(conv.id) should not contain selfUser.id
//      listMessages(conv.id) should have size (msgsCount + 1)
//      listMessages(conv.id).last.msgType shouldEqual Message.Type.MEMBER_LEAVE
//      convMemberLeaveSync shouldEqual Some((conv.id, selfUser.id))
//      convStateSync shouldBe None
//    }
//  }
//
//  feature("create one-to-one conversation") {
//
//    def conversationResponse = {
//      val convId = ConvId()
//      ConversationResponse(ConversationData(convId, RConvId(), None, selfUser.id, ConversationType.OneToOne), Seq(ConversationMemberData(selfUser.id, convId), ConversationMemberData(user1.id, convId)))
//    }
//
//    def createConversationEvent = {
//      val response = conversationResponse
//      CreateConversationEvent(response.conversation.remoteId, new Date, selfUser.id, response)
//    }
//
//    scenario("getOneToOneConversation should update remoteId") {
//      val user = UserId()
//      val selfUser = UserId()
//      val remote = RConvId()
//      val count = listConvs.size
//
//      val conv = Await.result(convsContent.getOneToOneConversation(user, selfUser), timeout)
//      val conv1 = Await.result(convsContent.getOneToOneConversation(user, selfUser, Some(remote)), timeout)
//
//      conv.id shouldEqual conv1.id
//      conv1.remoteId shouldEqual remote
//
//      listConvs should have size (count + 1)
//    }
//
//    scenario("getOneToOneConversation returns local if exists") {
//      val user = UserId()
//      val selfUser = UserId()
//      val remote = RConvId()
//      val count = listConvs.size
//
//      val conv = Await.result(convsContent.getOneToOneConversation(user, selfUser, Some(remote)), timeout)
//      val conv1 = Await.result(convsContent.getOneToOneConversation(user, selfUser), timeout)
//
//      conv.id shouldEqual conv1.id
//      conv.remoteId shouldEqual remote
//      conv1 shouldEqual conv
//
//      listConvs should have size (count + 1)
//    }
//
//    scenario("create local conversation for user") {
//      val conv = Await.result(convsContent.getOneToOneConversation(user1.id, selfUser.id), timeout)
//      conv.id.str shouldEqual user1.id.str
//      conv.creator shouldEqual selfUser.id
//
//      withDelay { ConversationDataDao.getById(ConvId(user1.id.str)) shouldEqual Some(conv) }
//      listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id)
//
//      lastMessage(conv.id) should be('empty)
//    }
//
//    scenario("find existing conversation for user") {
//      convsContent.getOneToOneConversation(user1.id, selfUser.id)
//      withDelay { ConversationDataDao.getById(ConvId(user1.id.str)) should be('defined) }
//
//      val conv = Await.result(convsContent.getOneToOneConversation(user1.id, selfUser.id), timeout)
//      conv.id.str shouldEqual user1.id.str
//    }
//
//    scenario("create local conversation on event") {
//      val event = createConversationEvent
//      service.dispatchEvent(event)
//      withDelay {
//        ConversationDataDao.getById(ConvId(user1.id.str)) should be('defined)
//        val conv = ConversationDataDao.getById(ConvId(user1.id.str)).get
//        conv.id.str shouldEqual user1.id.str
//        conv.remoteId shouldEqual event.convId
//        conv.creator shouldEqual selfUser.id
//
//        listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id)
//
//        lastMessage(conv.id) should be('empty)
//      }
//    }
//
//    scenario("update conversation on event") {
//      Await.result(convsContent.getOneToOneConversation(user1.id, selfUser.id), timeout)
//
//      val event = createConversationEvent
//      service.dispatchEvent(event)
//      awaitUi(250.millis)
//
//      val convOpt = Await.result(convsContent.convById(ConvId(user1.id.str)), timeout)
//      convOpt should be('defined)
//      val conv = convOpt.get
//
//      conv.id.str shouldEqual user1.id.str
//      conv.remoteId shouldEqual event.convId
//      conv.creator shouldEqual selfUser.id
//
//      getConv(ConvId(user1.id.str)) shouldEqual Some(conv)
//
//      listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id)
//
//      lastMessage(conv.id) should be('empty)
//    }
//
//    scenario("create new conversation for connected user") {
//      val conv = Await.result(convsUi.createOneToOneConversation(user1.id, selfUser.id), timeout)
//      conv.id.str shouldEqual user1.id.str
//      conv.creator shouldEqual selfUser.id
//
//      getConv(ConvId(user1.id.str)) shouldEqual Some(conv)
//      listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id)
//
//      convSync shouldEqual Some(conv.id)
//
//      lastMessage(conv.id) should be('defined)
//      val msg = lastLocalMessage(conv.id, Message.Type.MEMBER_JOIN)
//      msg should be('defined)
//
//      msg.map(_.userId) shouldEqual Some(selfUser.id)
//      msg.map(_.members) shouldEqual Some(Set(user1.id))
//    }
//
//    scenario("update conversation on post response") {
//      Await.result(convsUi.createOneToOneConversation(user1.id, selfUser.id), timeout)
//      convSync should be('defined)
//
//      val response = conversationResponse
//      Await.result(conversations.updateConversations(Seq(response)), 10.seconds)
//
//
//      getConv(ConvId(user1.id.str)).map(_.remoteId) shouldEqual Some(response.conversation.remoteId)
//      val conv = getConv(ConvId(user1.id.str)).get
//      listActiveMembers(conv.id).toSet shouldEqual Set(selfUser.id, user1.id)
//
//      val event = MemberJoinEvent(conv.remoteId, new Date, selfUser.id, Seq(user1.id))
//      service.dispatchEvent(event)
//      Thread.sleep(500)
//
//      lastLocalMessage(conv.id, Message.Type.MEMBER_JOIN) should be('empty)
//      val msg = lastMessage(conv.id)
//      msg should be('defined)
//
//      msg.map(_.userId) shouldEqual Some(selfUser.id)
//      msg.map(_.members) shouldEqual Some(Set(user1.id))
//    }
//  }
}
