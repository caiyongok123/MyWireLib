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
package com.waz.service.conversation

import java.util.Date

import com.waz.RobolectricUtils
import com.waz.content.GlobalDatabase
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserData.UserDataDao
import com.waz.model._
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.testutils.MockZMessaging
import com.waz.threading.Threading
import com.waz.utils.wrappers.DB
import org.robolectric.Robolectric
import org.scalatest._

import scala.concurrent.duration._

@Ignore class GroupConversationNameSpec extends FeatureSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with RobolectricTests with RobolectricUtils { test =>
  implicit lazy val dispatcher = Threading.Background
  lazy val globalStorage = new GlobalDatabase(Robolectric.application)

  lazy val selfUser = UserData("self user")
  lazy val user1 = UserData("user 1")

  lazy val time = System.currentTimeMillis() - 100
  lazy val conv = ConversationData(ConvId(), RConvId(), Some("convName"), selfUser.id, ConversationType.Group).withFreshSearchKey

  implicit def db: DB = service.db.dbHelper.getWritableDatabase

  lazy val service = new MockZMessaging(selfUserId = selfUser.id)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    UserDataDao.insertOrReplace(Seq(selfUser, user1))
  }

  import service._

  feature("Syncing") {

    scenario("Set name from initial sync event") {
      service.dispatchEvent(CreateConversationEvent(conv.remoteId, new Date(time), selfUser.id, ConversationResponse(conv, Seq(ConversationMemberData(selfUser.id, conv.id), ConversationMemberData(user1.id, conv.id)))))
      withDelay {
        listConvs.flatMap(_.name) shouldEqual List("convName")
      }
    }

    scenario("Don't update name on event older than just fetched lastEvent") {
      service.dispatchEvent(RenameConversationEvent(conv.remoteId, new Date(time - 100), user1.id, "old name"))
      awaitUi(200.millis)
      getConv(conv.id).flatMap(_.name) shouldEqual Some("convName")
    }

    scenario("Update name on new RenameEvent") {
      service.dispatchEvent(RenameConversationEvent(conv.remoteId, new Date(time + 1), user1.id, "updated name"))
      withDelay {
        getConv(conv.id).flatMap(_.name) shouldEqual Some("updated name")
        getConv(conv.id).map(_.lastEventTime.toEpochMilli) shouldEqual Some(time + 1)
      }
    }

    scenario("Don't update name on older RenameEvent") {
      service.dispatchEvent(RenameConversationEvent(conv.remoteId, new Date(time), user1.id, "prev name"))
      awaitUi(200.millis)
      getConv(conv.id).flatMap(_.name) shouldEqual Some("updated name")
      getConv(conv.id).map(_.lastEventTime.toEpochMilli) shouldEqual Some(time + 1)
    }
  }
}
