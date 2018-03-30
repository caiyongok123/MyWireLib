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
package com.waz.service.notifications

import java.util.Date

import android.database.sqlite.SQLiteDatabase
import com.waz.RobolectricUtils
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.model.AssetData.RemoteData
import com.waz.model.AssetStatus.UploadCancelled
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GenericContent.{Asset, MsgEdit, MsgRecall, Text}
import com.waz.model.GenericMessage.TextMessage
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.service.Timeouts
import com.waz.service.push.NotificationService.NotificationInfo
import com.waz.testutils.Matchers._
import com.waz.testutils._
import com.waz.utils.events.EventContext
import org.robolectric.shadows.ShadowLog
import org.scalatest._
import org.scalatest.prop.PropertyChecks
import org.threeten.bp.Instant

import scala.concurrent.Await
import scala.concurrent.duration._

@Ignore class NotificationServiceSpec extends FeatureSpec with Matchers with PropertyChecks with BeforeAndAfter with BeforeAndAfterAll with RobolectricTests with RobolectricUtils with DefaultPatienceConfig { test =>

  ShadowLog.stream = System.out

  @volatile var currentNotifications = Nil: Seq[NotificationInfo]

  lazy val selfUserId = UserId()
  lazy val oneToOneConv = ConversationData(ConvId(), RConvId(), None, selfUserId, ConversationType.OneToOne)
  lazy val groupConv = ConversationData(ConvId(), RConvId(), Some("group conv"), selfUserId, ConversationType.Group)

  lazy val zms = new MockZMessaging(selfUserId = selfUserId) { self =>
    notifications.notifications { ns => println(s"updating ns: $ns"); currentNotifications = ns } (EventContext.Global)

    override def timeouts = new Timeouts {
      override val notifications = new Notifications() {
        override def clearThrottling = 100.millis
      }
    }
  }

  lazy val service = zms.notifications

  implicit def db: SQLiteDatabase = zms.db.dbHelper.getWritableDatabase

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    zms.convsStorage.insertAll(Seq(oneToOneConv, groupConv)).await()
  }

  before {
    zms.convsContent.updateConversationLastRead(oneToOneConv.id, Instant.now()).await()
    zms.convsContent.updateConversationLastRead(groupConv.id, Instant.now()).await()
    clearNotifications()
  }

  feature("Add notifications for events") {

    scenario("Process user connection event for an unsynced user") {
      val userId = UserId(oneToOneConv.id.str)
      val convId = oneToOneConv.remoteId
      Await.ready(zms.dispatch(UserConnectionEvent(convId, selfUserId, userId, Some("hello"), ConnectionStatus.PendingFromOther, new Date, Some("other user")).withCurrentLocalTime()), 5.seconds)

      withDelay {
        currentNotifications should beMatching {
          case Seq(NotificationInfo(_, NotificationType.CONNECT_REQUEST, _, "hello", _, Some("other user"), Some("other user"), _, false, _, _, _, _)) => true
        }
      }
    }

    scenario("Process contact join event") {
      val userId = UserId()
      Await.ready(zms.dispatch(ContactJoinEvent(userId, "test name")), 5.seconds)

      withDelay {
        currentNotifications should beMatching {
          case Seq(NotificationInfo(_, NotificationType.CONTACT_JOIN, _, _, _, _, _, _, false, _, _, _, _)) => true
        }
      }
    }

    scenario("Process mentions event") {
      val userId = UserId(oneToOneConv.id.str)
      val convId = oneToOneConv.id

      Await.ready(zms.dispatch(GenericMessageEvent(oneToOneConv.remoteId, new Date, userId, TextMessage("test name", Map(selfUserId -> "name"))).withCurrentLocalTime()), 5.seconds)

      withDelay {
        currentNotifications should beMatching {
          case Seq(NotificationInfo(_, NotificationType.TEXT, _, "test name", `convId`, _, _, _, false, true, _, _, _)) => true
        }
      }
    }

    scenario("Process any asset event") {
      val userId = UserId(oneToOneConv.id.str)
      val convId = oneToOneConv.id
      Await.ready(zms.dispatch(GenericMessageEvent(oneToOneConv.remoteId, new Date, userId, GenericMessage(MessageId().uid, Asset(AssetData(status = UploadCancelled)))).withCurrentLocalTime()), 5.seconds) // this should not generate a notification
      Await.ready(zms.dispatch(GenericAssetEvent(oneToOneConv.remoteId, new Date, userId, GenericMessage(MessageId().uid, Asset(AssetData().copyWithRemoteData(RemoteData(Some(RAssetId()), None, Some(AESKey()), Some(Sha256("sha")))))), RAssetId(), None).withCurrentLocalTime()), 5.seconds)

      withDelay {
        currentNotifications should beMatching {
          case Seq(NotificationInfo(_, NotificationType.ANY_ASSET, _, _, `convId`, _, _, _, false, _, _, _, _)) => true
        }
      }
    }

    scenario("Receiving the same message twice") {
      val ev1 = textMessageEvent(Uid(), groupConv.remoteId, new Date, UserId(), "meep").withCurrentLocalTime()
      val ev2 = ev1.copy()

      Await.ready(zms.dispatch(ev1, ev2), 5.seconds)

      val groupId = groupConv.id

      withDelay {
        currentNotifications should beMatching {
          case Seq(NotificationInfo(_, NotificationType.TEXT, _, "meep", `groupId`, Some("group conv"), _, _, true, _, _, _, _)) => true
        }
      }
    }

    scenario("Don't show duplicate notification even if notifications were cleared") {
      val ev = textMessageEvent(Uid(), groupConv.remoteId, new Date, UserId(), "meep").withCurrentLocalTime()

      zms.dispatch(ev)

      withDelay {
        currentNotifications should have size 1
      }
      clearNotifications()

      zms.dispatch(ev)

      awaitUi(250.millis)
      currentNotifications shouldBe empty
    }

    scenario("Ignore events from self user") {
      val ev = textMessageEvent(Uid(), groupConv.remoteId, new Date, selfUserId, "meep").withCurrentLocalTime()

      Await.ready(zms.dispatch(ev), 5.seconds)
      awaitUi(200.millis)

      currentNotifications shouldBe empty
    }

    scenario("Remove older notifications when lastRead is updated") {
      val time = System.currentTimeMillis()
      zms.dispatch(
        textMessageEvent(Uid(), groupConv.remoteId, new Date(time), UserId(), "meep").withCurrentLocalTime(),
        textMessageEvent(Uid(), groupConv.remoteId, new Date(time + 10), UserId(), "meep1").withCurrentLocalTime(),
        textMessageEvent(Uid(), groupConv.remoteId, new Date(time + 20), UserId(), "meep2").withCurrentLocalTime(),
        textMessageEvent(Uid(), groupConv.remoteId, new Date(time + 30), UserId(), "meep3").withCurrentLocalTime(),
        textMessageEvent(Uid(), groupConv.remoteId, new Date(time + 40), UserId(), "meep4").withCurrentLocalTime()
      )

      withDelay {
        currentNotifications should have size 5
      }

      zms.convsContent.updateConversationLastRead(groupConv.id, Instant.ofEpochMilli(time + 30))

      withDelay {
        currentNotifications should have size 1
      }
    }

    scenario("Remove notifications for recalled messages") {
      val id = Uid()
      val user = UserId()
      val ev = textMessageEvent(id, groupConv.remoteId, new Date(), user, "meep")

      zms.dispatch(ev.withCurrentLocalTime())

      withDelay {
        currentNotifications should have size 1
      }

      zms.dispatch(GenericMessageEvent(groupConv.remoteId, new Date(), user, GenericMessage(Uid(), MsgRecall(MessageId(id.str)))).withCurrentLocalTime())

      withDelay {
        currentNotifications should have size 0
      }
    }

    scenario("Update notifications for edited messages") {
      val id = Uid()
      val user = UserId()
      val time = new Date()
      val ev = textMessageEvent(id, groupConv.remoteId, time, user, "meep")

      zms.dispatch(ev.withCurrentLocalTime())

      withDelay {
        currentNotifications should have size 1
      }

      zms.dispatch(GenericMessageEvent(groupConv.remoteId, new Date(), user, GenericMessage(Uid(), MsgEdit(MessageId(id.str), Text("updated")))).withCurrentLocalTime())

      withDelay {
        currentNotifications should have size 1
        currentNotifications.head.tpe shouldEqual NotificationType.TEXT
        currentNotifications.head.message shouldEqual "updated"
      }
    }
  }

  feature("List notifications") {

    scenario("List notifications with local conversation ids") {
      Await.ready(zms.dispatch(textMessageEvent(Uid(), groupConv.remoteId, new Date, UserId(), "test msg").withCurrentLocalTime()), 5.seconds)

      val groupId = groupConv.id

      withDelay {
        currentNotifications should beMatching {
          case Seq(NotificationInfo(_, NotificationType.TEXT, _, _, `groupId`, Some("group conv"), _, _, true, _, _, _, _)) => true
        }
      }
    }
  }

  def clearNotifications(): Unit = service.removeNotifications()
}
