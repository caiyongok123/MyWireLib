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

import android.content.Context

object ZMessagingApi {

  trait RegistrationListener {
    def onRegistered(user: Self): Unit
    def onRegistrationFailed(code: Int, message: String, label: String): Unit
  }
}

trait ZMessagingApi {

  def onCreate(context: Context): Unit

  def onResume(): Unit

  def onPause(): Unit

  def onDestroy(): Unit

  def onInit(callback: InitListener): Unit

  def logout(): Unit

  def getSelf: Self

  def getConversations: ConversationsList

  def getUser(id: String): User

  /** Lists the contacts from this phone's contact providers (if they have an associated email or phone number) blended
    * with Wire users.
    */
  def getContacts: Contacts

  def getInvitations: Invitations

  def getErrors: ErrorsList

  def getGiphy: Giphy

  def getConnectionIndicator: ConnectionIndicator

  def getUsernames: Usernames
}
