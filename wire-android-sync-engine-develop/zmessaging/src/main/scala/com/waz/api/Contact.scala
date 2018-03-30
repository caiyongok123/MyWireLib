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
package com.waz.api

import java.util.Collection

/** A contact is either a user on Wire or a contact from the device's contact provider. So only one of #getUser and
  * #getDetails will return a non-null value. In addition, an object of this type never changes from address book
  * details to user, nor vice versa.
  */
trait Contact {
  def getUser: User
  def getDetails: ContactDetails
}

trait ContactDetails extends UiObservable {
  def getDisplayName: String
  def getInitials: String
  def hasBeenInvited: Boolean

  /** A contact may possibly be reached in multiple ways (i.e. several email addresses and phone numbers) */
  def getContactMethods: Collection[ContactMethod]
}
