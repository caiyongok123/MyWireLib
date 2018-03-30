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

import com.waz.Control.getOrUpdate
import com.waz.api.impl.ContactDetails
import com.waz.model.{Contact, ContactId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.EventStream
import com.waz.ZLog.ImplicitTag._

import scala.collection.Seq
import scala.concurrent.Future

class ContactDetailsCache(module: UiModule) {
  private val details = new UiCache[ContactId, ContactDetails](lruSize = 50)(module)
  
  def getOrElseUpdate(id: ContactId, default: => ContactDetails): ContactDetails = getOrUpdate(details)(id, default)

  new UiEventListener[IndexedSeq[Contact]] {
    def ui: UiModule = module
    override protected def publisher(zms: ZMessaging): EventStream[IndexedSeq[Contact]] = zms.contacts.contactsLoaded
    override protected def onReset: Future[Unit] = Threading.Ui(details.clear())
    override protected def onResume: Future[Unit] = Future.successful(()) // nothing to do here; changes will be pushed from ContactsService
    override protected def process(events: Seq[IndexedSeq[Contact]]): Future[Unit] = Threading.Ui {
      events.lastOption.foreach(_.foreach(d => details.peek(d.id).foreach(_.setCurrent(d))))
    }
  }

  new UiEventListener[Set[ContactId]] {
    def ui: UiModule = module
    override protected def publisher(zms: ZMessaging): EventStream[Set[ContactId]] = zms.invitations.invitedContacts.onChanged
    override protected def onReset: Future[Unit] = Future.successful(())
    override protected def onResume: Future[Unit] = ui.getCurrent.map {
      case Some(zms) =>
        zms.invitations.invitedContacts.currentValue.foreach { invited =>
          details.foreach(d => d.setInvited(invited contains d.id))
        }
      case None =>
    }(Threading.Ui)

    override protected def process(events: Seq[Set[ContactId]]): Future[Unit] = Threading.Ui {
      events.lastOption.foreach(invitations => details.foreach(d => d.setInvited(invitations contains d.id)))
    }
  }
}
