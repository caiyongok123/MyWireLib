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
package com.waz.api.impl

import java.util.{Collection, Locale}

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.model._
import com.waz.service.ContactsServiceImpl.{TopContactsOnWire, UnifiedContacts}
import com.waz.service.SearchKey
import com.waz.threading.Threading
import com.waz.ui._
import com.waz.utils._
import com.waz.utils.events.{EventContext, Signal}
import com.waz.{api, model}

import scala.collection.JavaConverters._
import scala.collection.{GenSet, mutable}
import scala.concurrent.Future
import scala.concurrent.duration._

class Contacts(filtering: ContactsFiltering)(implicit ui: UiModule) extends api.Contacts with CoreList[api.Contact] with SignalLoading {
  import Contacts._

  private val search = Signal(filtering.initial)
  private var content = Content(UnifiedContacts(Map.empty, Map.empty, Vector.empty, SeqMap.empty, TopContactsOnWire(Vector.empty, 0)), Vector.empty,
    filtering.initial, Filtered(Vector.empty, SeqMap.empty))
  private var currentUpdate: Future[Unit] = Future.successful(())
  private var invitedContacts = Set.empty[ContactId]

  addLoader(_.contacts.unifiedContacts) { updated =>
    verbose(s"loaded unified contacts: ${updated.sorted.size}")
    currentUpdate = currentUpdate.map(_ => content)(Threading.Ui).flatMap { current =>
      updateFiltering(current.copy(updated, accessorsFor(updated), current.filter, current.filtered))
    }(Threading.Background).recoverWithLog()
  }

  addLoader(_.invitations.invitedContacts)(invitedContacts = _)

  private def accessorsFor(updated: UnifiedContacts) = updated.sorted.map(_.fold(
    u => new OnWire(ui.users.getUser(updated.users(u))),
    c => new NotOnWire(getOrCreate(c, updated))))

  override def search(token: String): Unit = search ! filtering.basedOn(token)

  search.throttle(400.millis).onUi { newFilter =>
    currentUpdate = currentUpdate.flatMap { _ =>
      updateFiltering(content.copy(filter = newFilter))
    }(Threading.Ui).recoverWithLog()
  }(EventContext.Global)

  private def updateFiltering(current: Content): Future[Unit] = Future(current.copy(filtered =
    if (current.filter.includesAll) Filtered(current.unifiedContacts.sorted.indices, current.unifiedContacts.groupedByInitial)
    else {
      def included(n: Int): Boolean = current.unifiedContacts.sorted(n).fold(
        uid => current.unifiedContacts.users.get(uid).exists(current.filter.includesUser),
        cid => current.unifiedContacts.contacts.get(cid).exists(current.filter.includesContact)
      )

      val sorted = current.unifiedContacts.sorted.indices.filter(included)
      val includes = sorted.to[mutable.HashSet]
      val byInitial = SeqMap(current.unifiedContacts.groupedByInitial.keys.flatMap { k =>
        val indices = current.unifiedContacts.groupedByInitial.byKey(k).filter(includes)
        if (indices.isEmpty) None
        else Some((k, indices))
      })(_._1, _._2)

      verbose(s"current filter ${current.filter} includes ${sorted.size} unified contact(s) split into ${byInitial.size} initial(s)")
      Filtered(sorted, byInitial)
    }
  ))(Threading.Background).map { updated =>
    val indicesChanged = content.filtered.sorted != updated.filtered.sorted
    val changed = indicesChanged || content.filtered.sorted.indices.exists(i => content.unifiedContacts.sorted(content.filtered.sorted(i)) != updated.unifiedContacts.sorted(updated.filtered.sorted(i)))
    content = updated
    if (changed) notifyChanged()
  }(Threading.Ui)

  override def size: Int = content.filtered.sorted.size
  override def get(index: Int): api.Contact = content.accessors(content.filtered.sorted(index))

  override def getInitials: Collection[String] = content.filtered.byInitial.keys.asJava
  override def getNumberOfContactsForInitial(initial: String): Int = content.filtered.byInitial.byKey.getOrElse(initial, Nil).size
  override def getContactForInitial(initial: String, index: Int): api.Contact =
    content.accessors(content.filtered.byInitial.byKey(initial)(index))

  override def getTop10ContactsOnWire(): Collection[api.ContactDetails] = content.unifiedContacts.topContactsOnWire.contacts.map { c =>
    getOrCreate(c, content.unifiedContacts): api.ContactDetails
  }.asJava

  override def getTotalContactsOnWireCount() = content.unifiedContacts.topContactsOnWire.totalCount

  private def getOrCreate(c: ContactId, unified: UnifiedContacts) =
    returning(ui.contactDetails.getOrElseUpdate(c, new ContactDetails(unified.contacts(c), invitedContacts(c))(ui)))(_.setCurrent(unified.contacts(c)))
}

object Contacts {
  case class Content(unifiedContacts: UnifiedContacts, accessors: Vector[api.Contact], filter: ContactsFilter, filtered: Filtered)
  case class Filtered(sorted: IndexedSeq[Int], byInitial: SeqMap[String, IndexedSeq[Int]])

  class OnWire(ui: => User) extends api.Contact {
    override def getUser = ui
    override def getDetails = null
    override def toString = s"OnWire(${ui.toString})"
  }

  class NotOnWire(ui: => ContactDetails) extends api.Contact {
    override def getUser = null
    override def getDetails = ui
    override def toString = s"NotOnWire(${ui.toString})"
  }
}

class ContactDetails(private var current: model.Contact, private var invited: Boolean)(ui: UiModule) extends api.ContactDetails with UiObservable {
  def id = current.id

  def setCurrent(updated: model.Contact) = if (updated != current) {
    current = updated
    notifyChanged()
  }

  def setInvited(updated: Boolean) = if (updated != invited) {
    invited = updated
    notifyChanged()
  }

  override def getDisplayName = current.name
  override def getInitials = current.initials
  override def hasBeenInvited = invited
  override def getContactMethods = sorted(current.emailAddresses).map(e => new ContactMethod(current, Left(e))(ui.zms)).
    ++[api.ContactMethod](sorted(current.phoneNumbers).map(p => new ContactMethod(current, Right(p))(ui.zms))).to[Vector].asJava
  override def toString = s"ContactDetails($current, invited = $invited)"

  private def sorted[A: Ordering](as: GenSet[A]): Iterator[A] = as.toVector.sorted.iterator
}

class ContactMethod(contact: model.Contact, method: Either[EmailAddress, PhoneNumber])(service: ZMessagingResolver) extends api.ContactMethod {
  override def getKind = method.fold(_ => api.ContactMethod.Kind.EMAIL, _ => api.ContactMethod.Kind.SMS)
  override def getStringRepresentation = method.fold(_.str, _.str)
  override def invite(message: String, locale: Locale): Unit = service(_.invitations.invite(contact.id, method, contact.name, message, Option(locale)))
  override def toString = s"ContactMethod(${contact.id}, $getStringRepresentation)"
}

trait ContactsFiltering {
  def initial: ContactsFilter
  def basedOn(token: String): ContactsFilter
}

trait ContactsFilter {
  def includesAll: Boolean
  def includesUser(u: UserData): Boolean
  def includesContact(c: model.Contact): Boolean
}

case class SearchKeyFiltering(initialToken: SearchKey = SearchKey.empty) extends ContactsFiltering {
  val initial = SearchKeyFilter(initialToken)
  def basedOn(token: String) = SearchKeyFilter(SearchKey(token))
}

case class SearchKeyFilter(searchKey: SearchKey) extends ContactsFilter {
  def includesAll: Boolean = searchKey.isEmpty
  def includesUser(u: UserData): Boolean = searchKey.isAtTheStartOfAnyWordIn(u.searchKey)
  def includesContact(c: model.Contact): Boolean = searchKey.isAtTheStartOfAnyWordIn(c.searchKey)
}

case class OnlyContactsBySearchKeyFiltering(initialToken: SearchKey = SearchKey.empty) extends ContactsFiltering {
  val initial = OnlyContactsBySearchKeyFilter(initialToken)
  def basedOn(token: String) = OnlyContactsBySearchKeyFilter(SearchKey(token))
}

case class OnlyContactsBySearchKeyFilter(searchKey: SearchKey) extends ContactsFilter {
  def includesAll: Boolean = false
  def includesUser(u: UserData): Boolean = false
  def includesContact(c: model.Contact): Boolean = searchKey.isAtTheStartOfAnyWordIn(c.searchKey)
}
