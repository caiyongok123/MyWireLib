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

import com.waz.api.ConversationsList.VerificationStateCallback

object ConversationsList {

  trait SearchableConversationsList extends CoreList[IConversation] {
    def getConversationIndex(id: String): Int
  }

  trait ConversationsListState extends UiObservable {
    def hasUnread: Boolean
    def hasUnsent: Boolean
    def hasPending: Boolean
  }

  trait VerificationStateCallback {
    def onVerificationStateChanged(convId: String, previous: Verification, current: Verification): Unit
  }
}

trait ConversationsList extends CoreList[IConversation] with EventualReadiness {
  @Deprecated // remove when not used anymore
  def getConversation(id: String): IConversation

  def getSyncIndicator: SyncIndicator
  def getState: ConversationsList.ConversationsListState
  def getArchivedConversations: ConversationsList.SearchableConversationsList
  def getIncomingConversations: ConversationsList.SearchableConversationsList
  def getEstablishedConversations: ConversationsList

  def onVerificationStateChange(callback: VerificationStateCallback): Unit
}
