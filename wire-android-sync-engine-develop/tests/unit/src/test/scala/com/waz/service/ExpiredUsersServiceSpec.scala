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

import com.waz.content.{MembersStorage, UsersStorage}
import com.waz.model.{ConvId, SyncId, UserData, UserId}
import com.waz.service.conversation.ConversationsListStateService
import com.waz.service.push.PushService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal}
import org.threeten.bp.Duration

import scala.concurrent.Future
import scala.concurrent.duration._
import com.waz.utils.RichInstant

class ExpiredUsersServiceSpec extends AndroidFreeSpec {

  implicit val ec = Threading.Background
  val convState = mock[ConversationsListStateService]
  val push      = mock[PushService]
  val members   = mock[MembersStorage]
  val users     = mock[UsersStorage]
  val sync      = mock[SyncServiceHandle]

  val onDeleted = EventStream[Seq[(UserId, ConvId)]]
  val currentConv = Signal(Option.empty[ConvId])

  //All user expiry times have an extra 10 seconds to factor in the buffer we leave in the service
  scenario("Start timer for user soon to expire") {
    val conv = ConvId("conv")

    currentConv ! Some(conv)

    clock + 10.seconds

    val wirelessId = UserId("wirelessUser")

    val convUsers = Set(
      UserData("user1").copy(id = UserId("user1")),
      UserData("user2").copy(id = UserId("user2")),
      UserData("wireless").copy(id = wirelessId, expiresAt = Some(clock.instant() - 10.seconds + 200.millis))
    )

    (members.activeMembers _).expects(conv).once().returning(Signal.const(convUsers.map(_.id)))
    (users.signal _).expects(*).anyNumberOfTimes().onCall { id: UserId =>
      Signal.const(convUsers.find(_.id == id).get)
    }

    getService //trigger creation of service

    val finished = EventStream[Unit]()
    (sync.syncUsers _).expects(*).once().onCall { (us: Seq[UserId]) =>
      if (!us.contains(wirelessId)) fail("Called sync for wrong user")
      finished ! {}
      Future.successful(SyncId())
    }

    result(finished.next)
  }

  scenario("Start timer for user soon to expire, user is removed elsewhere") {
    val conv = ConvId("conv")

    currentConv ! Some(conv)

    val wirelessId = UserId("wirelessUser")

    val convUsers = Set(
      UserData("user1").copy(id = UserId("user1")),
      UserData("user2").copy(id = UserId("user2")),
      UserData("wireless").copy(id = wirelessId, expiresAt = Some(clock.instant() - 10.seconds + 200.millis))
    )

    val activeMembers = Signal(convUsers.map(_.id))

    (members.activeMembers _).expects(conv).once().returning {
      activeMembers
    }
    (users.signal _).expects(*).anyNumberOfTimes().onCall { id: UserId =>
      Signal.const(convUsers.find(_.id == id).get)
    }

    (members.getByUsers _).expects(Set(wirelessId)).once().returning(Future.successful(IndexedSeq.empty))

    getService //trigger creation of service

    Thread.sleep(100) //need to sleep to give timer a chance to be built...

    activeMembers ! (convUsers.map(_.id) - wirelessId)
    onDeleted ! Seq((wirelessId, conv))

    awaitAllTasks

    Thread.sleep(500)
    (sync.syncUsers _).expects(*).never()
  }

  scenario("Wireless member added to conversation also triggers a timer") {
    val conv = ConvId("conv")

    currentConv ! Some(conv)

    val wirelessUser = UserData("wireless").copy(id = UserId("wirelessUser"), expiresAt = Some(clock.instant() - 10.seconds + 200.millis))

    val convUsers = Set(
      UserData("user1").copy(id = UserId("user1")),
      UserData("user2").copy(id = UserId("user2"))
    )

    val activeMembers = Signal(convUsers.map(_.id))

    (members.activeMembers _).expects(conv).once().returning(activeMembers)
    (users.signal _).expects(*).anyNumberOfTimes().onCall { id: UserId =>
      Signal.const((convUsers + wirelessUser).find(_.id == id).get)
    }

    getService //trigger creation of service

    activeMembers.mutate(_ + wirelessUser.id)

    awaitAllTasks

    val finished = EventStream[Unit]()
    (sync.syncUsers _).expects(*).once().onCall { (us: Seq[UserId]) =>
      if (!us.contains(wirelessUser.id)) fail("Called sync for wrong user")
      finished ! {}
      Future.successful(SyncId())
    }

    result(finished.next)
  }

  scenario("Wireless member data updates after we've already checked the conversation") {
    val conv = ConvId("conv")

    currentConv ! Some(conv)

    val wirelessUser = UserData("wireless").copy(id = UserId("wirelessUser"), expiresAt = Some(clock.instant() - 10.seconds + 200.millis))

    val convUsers = Set(
      UserData("user1").copy(id = UserId("user1")),
      UserData("user2").copy(id = UserId("user2"))
    )

    val wirelessUserSignal = Signal[UserData]()

    (members.activeMembers _).expects(conv).once().returning(Signal.const(convUsers.map(_.id) + wirelessUser.id))
    (users.signal _).expects(*).anyNumberOfTimes().onCall { id: UserId =>
      if (id == wirelessUser.id) wirelessUserSignal else Signal.const(convUsers.find(_.id == id).get)
    }

    getService //trigger creation of service

    println("Wireless user gets synced")
    wirelessUserSignal ! wirelessUser

    awaitAllTasks

    val finished = EventStream[Unit]()
    (sync.syncUsers _).expects(*).once().onCall { (us: Seq[UserId]) =>
      if (!us.contains(wirelessUser.id)) fail("Called sync for wrong user")
      finished ! {}
      Future.successful(SyncId())
    }


    result(finished.next)
  }

  def getService = {
    (members.onDeleted _).expects().once().returning(onDeleted)
    (convState.selectedConversationId _).expects().once().returning(currentConv)
    (push.beDrift _).expects().anyNumberOfTimes().returning(Signal.const(Duration.ZERO))

    new ExpiredUsersService(convState, push, members, users, sync)
  }

}
