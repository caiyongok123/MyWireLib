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
package com.waz.ui

import android.os.Parcel
import com.waz.Control.getOrUpdate
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api
import com.waz.api.impl.Message
import com.waz.model._
import com.waz.model.sync.SyncJob.Priority
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.EventStream
import com.waz.utils.{JsonDecoder, RichFuture}

import scala.collection.Seq
import scala.concurrent.Future

class Messages(implicit module: UiModule) {
  import Threading.Implicits.Background

  val messages = new UiCache[MessageId, Message](lruSize = 50)

  new UiEventListener[MessageId] {
    def ui: UiModule = module
    override protected def publisher(zms: ZMessaging): EventStream[MessageId] = zms.msgAndLikes.onUpdate
    override protected def onReset: Future[Unit] = Threading.Ui(messages.clear())
    override protected def onResume: Future[Unit] = Threading.Ui(messages.foreach(_.reload()))
    override protected def process(events: Seq[MessageId]): Future[Unit] = {
      val changed = events.toSet
      Threading.Ui(messages.foreach(m => if (changed(m.id)) m.reload()))
    }
  }

  def cachedOrFromParcel(p: Parcel): api.Message = cachedOrUpdated(MessageAndLikes(JsonDecoder.decode[MessageData](p.readString()),
    p.createStringArray().map(UserId), p.readInt() == 1))

  def cachedOrNew(id: MessageId): Message = getOrUpdate(messages)(id, new Message(id)(module))

  def cachedOrUpdated(data: MessageAndLikes, userAction: Boolean = false): Message = {
    val msg = getOrUpdate(messages)(data.message.id, new Message(data)(module))

    if (userAction) {
      if (msg.data.state == api.Message.Status.FAILED) {
        verbose(s"user read FAILED message: ${msg.data}")
        module.zms(_.messages.markMessageRead(data.message.convId, data.message.id))
      }

      if (msg.data.content exists syncNeeded) module.zms(_.sync.syncRichMedia(data.message.id, Priority.High))
    }
    msg
  }

  private[this] val syncNeeded = (_: MessageContent).syncNeeded

  def updateLastRead(conv: ConvId, msg: MessageData) = module.zms.flatMapFuture(_.convsUi.setLastRead(conv, msg)).recoverWithLog(reportHockey = true)

  def retry(conv: ConvId, msg: MessageId): Unit = module.zms(_.messages.retryMessageSending(conv, msg)).future.recoverWithLog(reportHockey = true)
}
