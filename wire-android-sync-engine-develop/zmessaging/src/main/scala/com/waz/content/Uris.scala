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
package com.waz.content

import android.content.ContentResolver
import android.net.Uri
import com.waz.model._

object Uris {
  val Authority = "com.waz"
  val Content = ContentResolver.SCHEME_CONTENT
  val Base = s"$Content://$Authority"

  val ConversationsUri = Uri.parse(s"$Base/conv")
  val SelfConversationUri = Uri.parse(s"$Base/conv/self")
  val ConversationsStateUri = ConversationsUri.buildUpon().appendEncodedPath("state").build()
  val MessagesRootUri = Uri.parse(s"$Base/msgs")
  val UsersUri = Uri.parse(s"$Base/users")
  val ErrorsUri = Uri.parse(s"$Base/errors")
  val ConvMembersRootUri = Uri.parse(s"$Base/members")
  val ConnectionsRootUri = Uri.parse(s"$Base/conn")
  val ContactsUri = Uri.parse(s"$Base/contacts")

  def ConversationUri(convId: ConvId): Uri = ConversationsUri.buildUpon().appendEncodedPath(convId.toString).build()

  def InputStateIndicatorUri(id: ConvId) = ConversationUri(id).buildUpon().appendEncodedPath("input-state").build()

  def MessagesUri(convId: ConvId): Uri = MessagesRootUri.buildUpon().appendEncodedPath(convId.toString).build()

  def ConvMembersUri(convId: ConvId): Uri = ConvMembersRootUri.buildUpon().appendEncodedPath(convId.str).build()

  def CommonConnectionsUri(userId: UserId): Uri = ConnectionsRootUri.buildUpon().appendEncodedPath(userId.str).appendEncodedPath("comm").build()

  def SyncIndicatorUri(base: Uri) = base.buildUpon().appendEncodedPath("sync-indicator").build()
}
