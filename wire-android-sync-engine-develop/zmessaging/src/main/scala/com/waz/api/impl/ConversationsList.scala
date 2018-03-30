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

import android.net.Uri
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api
import com.waz.api.ConversationsList.VerificationStateCallback
import com.waz.api.IConversation
import com.waz.api.impl.ConversationsListState.Data
import com.waz.api.impl.conversation.BaseConversationsList
import com.waz.content.Uris
import com.waz.content.Uris.SyncIndicatorUri
import com.waz.model.ConversationData.ConversationType
import com.waz.model.sync.SyncCommand
import com.waz.model.{ConvId, ConversationData}
import com.waz.ui._

class ConversationsList(implicit val ui: UiModule) extends api.ConversationsList with BaseConversationsList {
  import ConversationsList._

  lazy val incoming = new SearchableConversationsList(conversations, IncomingListFilter)

  lazy val established = new ConversationsList {
    override def filter = EstablishedListFilter
  }

  lazy val archived =
    if (filter == RegularListFilter) ui.cached(ArchivedUri, new SearchableConversationsList(conversations, ArchivedListFilter))
    else ui.cached(EstablishedArchivedUri, new SearchableConversationsList(conversations, EstablishedArchivedListFilter))

  override protected val conversations: Conversations = ui.convs

  override def getConversation(id: String): IConversation = conversations.convById(ConvId(id))

  override def getSyncIndicator = ui.cached(SyncIndicatorUri(Uris.ConversationsUri), new SyncIndicator(SyncCommand.SyncConversations, SyncCommand.SyncSelf, SyncCommand.SyncConnections))

  override def getArchivedConversations = archived

  override def getIncomingConversations = incoming

  override def getEstablishedConversations = established

  override def getState = ui.cached(Uris.ConversationsStateUri, new ConversationsListState)

  override def onVerificationStateChange(callback: VerificationStateCallback): Unit = conversations.onVerificationStateChange(callback)

}

object ConversationsList {
  val ArchivedUri = Uri.parse(s"${Uris.Base}/conv-archived")
  val IncomingUri = Uri.parse(s"${Uris.Base}/conv-incoming")
  val EstablishedUri = Uri.parse(s"${Uris.Base}/conv-established")
  val EstablishedArchivedUri = Uri.parse(s"${Uris.Base}/conv-established-archived")

  val RegularListFilter: (ConversationData => Boolean) = { c => !c.hidden && !c.archived && c.convType != ConversationType.Incoming && c.convType != ConversationType.Self }
  val IncomingListFilter: (ConversationData => Boolean) = { c => !c.hidden && !c.archived && c.convType == ConversationType.Incoming }
  val ArchivedListFilter: (ConversationData => Boolean) = { c => !c.hidden && !c.completelyCleared && c.archived }
  val EstablishedListFilter: (ConversationData => Boolean) = { c => RegularListFilter(c) && c.convType != ConversationType.WaitForConnection }
  val EstablishedArchivedListFilter: (ConversationData => Boolean) = { c => ArchivedListFilter(c) && c.convType != ConversationType.WaitForConnection }
}

class SearchableConversationsList(val conversations: Conversations, override val filter: ConversationData => Boolean)(implicit val ui: UiModule)
  extends com.waz.api.ConversationsList.SearchableConversationsList with BaseConversationsList

class ConversationsListState(implicit ui: UiModule) extends com.waz.api.ConversationsList.ConversationsListState with UiObservable with SignalLoading {
  var data = Data()

  addLoader(_.convsStats.state) { data =>
    debug(s"onLoaded($data)")
    if (this.data != data) {
      this.data = data
      notifyChanged()
    }
  }

  override def hasUnread: Boolean = data.unread
  override def hasUnsent: Boolean = data.unsent
  override def hasPending: Boolean = data.pending
}

object ConversationsListState {
  case class Data(unread: Boolean = false, unsent: Boolean = false, pending: Boolean = false)
}
