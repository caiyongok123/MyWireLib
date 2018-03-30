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
package com.waz.service.push

import android.content.Context
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.content.Database
import com.waz.model.PushNotificationEvents.PushNotificationEventsDao
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.push.PushNotificationEventsStorage.{EventIndex, PlainWriter}
import com.waz.sync.client.PushNotificationEncoded
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.events.EventContext
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}
import org.json.JSONArray

import scala.concurrent.Future


object PushNotificationEventsStorage {
  type PlainWriter = Array[Byte] => Future[Unit]
  type EventIndex = Int
}

trait PushNotificationEventsStorage extends CachedStorage[(Uid, EventIndex), PushNotificationEvent] {
  def setAsDecrypted(id: Uid, index: EventIndex): Future[Unit]
  def writeClosure(id: Uid, index: EventIndex): PlainWriter
  def writeError(id: Uid, index: EventIndex, error: OtrErrorEvent): Future[Unit]
  def saveAll(pushNotifications: Seq[PushNotificationEncoded]): Future[Unit]
  def decryptedEvents: Future[Seq[PushNotificationEvent]]
  def encryptedEvents: Future[Seq[PushNotificationEvent]]
  def removeEventsWithIds(ids: Seq[Uid]): Future[Unit]
  def registerEventHandler(handler: () => Future[Unit])(implicit ec: EventContext): Future[Unit]
}

class PushNotificationEventsStorageImpl(context: Context, storage: Database, clientId: ClientId)
  extends CachedStorageImpl[(Uid, EventIndex), PushNotificationEvent](new TrimmingLruCache(context, Fixed(1024*1024)), storage)(PushNotificationEventsDao, "PushNotificationEvents_Cached")
    with PushNotificationEventsStorage {

  private implicit val dispatcher = new SerialDispatchQueue(name = "PushNotificationEventsStorage")

  override def setAsDecrypted(id: Uid, index: EventIndex): Future[Unit] = {
    update((id, index), u => u.copy(decrypted = true)).map {
      case None =>
        throw new IllegalStateException(s"Failed to set event at index $index with id $id as decrypted")
      case _ => ()
    }
  }

  override def writeClosure(id: Uid, index: EventIndex): PlainWriter =
    (plain: Array[Byte]) => update((id, index), _.copy(decrypted = true, plain = Some(plain))).map(_ => Unit)

  override def writeError(id: Uid, index: EventIndex, error: OtrErrorEvent): Future[Unit] =
    update((id, index), _.copy(decrypted = true, event = MessageEvent.MessageEventEncoder(error), plain = None))
      .map(_ => Unit)

  override def saveAll(pushNotifications: Seq[PushNotificationEncoded]): Future[Unit] = {
    import com.waz.utils._
    def filterOtrEventsForOtherClients(events: JSONArray): JSONArray = {
      events.foldLeft(new JSONArray){(res, obj) =>
        val eventType = obj.getString("type")
        if(eventType.startsWith("conversation.otr") &&
          !obj.getJSONObject("data").getString("recipient").equals(clientId.str)) {
          verbose(s"Skipping otr event not intended for us: $obj")
          res
        } else {
          res.put(obj)
        }
      }
    }

    val eventsToSave = pushNotifications
      .flatMap { pn =>
        val eventsForUs = filterOtrEventsForOtherClients(pn.events)
        if(eventsForUs.toVector.nonEmpty) {
          pn.events
            .toVector
            .zipWithIndex
            .map { case (obj, index) =>
              PushNotificationEvent(pn.id, index, decrypted = false, obj, transient = pn.transient)
            }
        } else {
          warn(s"Got notification ${pn.id} containing no events for us, ignoring")
          Seq.empty[PushNotificationEvent]
        }
      }
    insertAll(eventsToSave).map(_ => ())
  }

  def decryptedEvents: Future[Seq[PushNotificationEvent]] = list().map(_.filter(_.decrypted))

  def encryptedEvents: Future[Seq[PushNotificationEvent]] = list().map(_.filter(!_.decrypted))

  def removeEventsWithIds(ids: Seq[Uid]): Future[Unit] =
    storage.withTransaction { implicit db =>
      removeAll(dao.list.filter(r => ids.contains(r.pushId)).map(r => (r.pushId, r.index)))
    }.future.map(_ => ())

  //This method is called once on app start, so invoke the handler in case there are any events to be processed
  //This is safe as the handler only allows one invocation at a time.
  override def registerEventHandler(handler: () => Future[Unit])(implicit ec: EventContext): Future[Unit] = {
    onAdded(_ => handler())
    processStoredEvents(handler)
  }

  private def processStoredEvents(processor: () => Future[Unit]): Future[Unit] =
    list().map { nots =>
      if (nots.nonEmpty) {
        processor()
      }
    }

}
