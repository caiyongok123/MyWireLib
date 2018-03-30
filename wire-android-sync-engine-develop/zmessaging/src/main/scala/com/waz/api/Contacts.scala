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

/** Groups of people:
  *  a) in your address book, not matching a user
  *  b) in your address book, matching a user that is not connected (including pending, cancelled etc.)
  *  c) in your address book, matching a user that is blocked
  *  d) in your address book, matching a user that is accepted
  *  e) user that is not in address book and not connected (including pending, cancelled etc.)
  *  f) user that is not in address book but is blocked
  *  g) user that is not in address book but is accepted
  *
  *  The unified contacts list should only contain a), b), d) and g).
  *  Only a) shows the contact from the address book. b), d) and g) show the matching user (with possibly abbreviated name in case of connection)
  */
trait Contacts extends CoreList[Contact] {
  def search(token: String): Unit
  def getInitials: Collection[String]
  def getNumberOfContactsForInitial(initial: String): Int
  def getContactForInitial(initial: String, index: Int): Contact
  def getTop10ContactsOnWire(): Collection[ContactDetails]
  def getTotalContactsOnWireCount(): Int
}
