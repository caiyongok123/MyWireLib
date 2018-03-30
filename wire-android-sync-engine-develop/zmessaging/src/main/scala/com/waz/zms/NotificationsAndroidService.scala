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
package com.waz.zms

import android.app.PendingIntent
import android.content.{Context, Intent}
import android.support.v4.app.RemoteInput
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{verbose, warn}
import com.waz.api.EphemeralExpiration
import com.waz.model.{AccountId, ConvId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading.Implicits.Background
import com.waz.utils._
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

class NotificationsAndroidService extends FutureService {

  import NotificationsAndroidService._

  override protected lazy val wakeLock = new TimedWakeLock(getApplicationContext, 2.seconds)

  override protected def onIntent(intent: Intent, id: Int): Future[Any] = wakeLock.async {

    val account = Option(intent.getStringExtra(ExtraAccountId)).map(AccountId)
    val conversation = Option(intent.getStringExtra(ExtraConvId)).map(ConvId)
    val instantReplyContent = Option(RemoteInput.getResultsFromIntent(intent)).map(_.getCharSequence(InstantReplyKey))

    Option(ZMessaging.currentAccounts) match {
      case Some(accs) =>
        account match {
          case Some(acc) => accs.getZMessaging(acc).flatMap {
            case Some(zms) if ActionClear == intent.getAction =>
              verbose(s"Clearing notifications for account: $acc and conversation:$conversation")
              zms.notifications.removeNotifications(nd => conversation.forall(_ == nd.conv))
            case Some(zms) if ActionQuickReply == intent.getAction =>
              (instantReplyContent, conversation) match {
                case (Some(content), Some(convId)) =>
                  zms.convsUi.setEphemeral(convId, EphemeralExpiration.NONE).flatMap { _ =>
                    zms.convsUi.sendMessage(convId, content.toString).map(_ => ())
                  }
                case _ =>
                  Future.successful({})
              }
            case Some(zms) =>
              verbose(s"Other device on account: $acc no longer active, resetting otherDeviceActiveTime")
              Future.successful(zms.notifications.otherDeviceActiveTime ! Instant.EPOCH)
          }
          case None =>
            warn("No account id passed on intent")
            Future.successful({})
        }
      case None =>
        warn("No AccountsService available")
        Future.successful({})
    }
  }
}

object NotificationsAndroidService {
  val ActionClear = "com.wire.CLEAR_NOTIFICATIONS"
  val ActionQuickReply = "com.wire.QUICK_REPLY"
  val ExtraAccountId = "account_id"
  val ExtraConvId = "conv_id"

  val InstantReplyKey = "instant_reply_key"

  val checkNotificationsTimeout: FiniteDuration = 1.minute

  def clearNotificationsIntent(accountId: AccountId, context: Context): PendingIntent =
    PendingIntent.getService(context, accountId.str.hashCode, new Intent(context, classOf[NotificationsAndroidService]).setAction(ActionClear).putExtra(ExtraAccountId, accountId.str), PendingIntent.FLAG_UPDATE_CURRENT)

  def clearNotificationsIntent(accountId: AccountId, convId: ConvId, context: Context): PendingIntent =
    PendingIntent.getService(context, accountId.str.hashCode + convId.str.hashCode, new Intent(context, classOf[NotificationsAndroidService]).setAction(ActionClear).putExtra(ExtraAccountId, accountId.str).putExtra(ExtraConvId, convId.str), PendingIntent.FLAG_UPDATE_CURRENT)

  def checkNotificationsIntent(accountId: AccountId, context: Context): PendingIntent =
    PendingIntent.getService(context, accountId.str.hashCode, new Intent(context, classOf[NotificationsAndroidService]).putExtra(ExtraAccountId, accountId.str), PendingIntent.FLAG_ONE_SHOT)

  def quickReplyIntent(accountId: AccountId, convId: ConvId, context: Context): PendingIntent =
    PendingIntent.getService(context, (accountId.str + convId.str).hashCode, new Intent(context, classOf[NotificationsAndroidService]).setAction(ActionQuickReply).putExtra(ExtraAccountId, accountId.str).putExtra(ExtraConvId, convId.str), PendingIntent.FLAG_ONE_SHOT)
}
