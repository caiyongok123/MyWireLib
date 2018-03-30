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
package com.waz.testutils

import com.waz.api.{CoreList, IConversation, User, Contact}
import com.waz.api.impl
import com.waz.model._
import com.waz.utils._

import scala.collection.breakOut

sealed trait HasId[A] { type Id; def idOf(a: A): Id }

object HasId {
  import Implicits._

  def idsOfAll[A](elems: A*)(implicit extract: HasId[A]): Vector[extract.Id] = elems.map(extract idOf _)(breakOut)
  def idsOf[A](l: CoreList[A])(implicit extract: HasId[A]): Vector[extract.Id] = idsOfAll(l.asScala:_*)(extract)

  implicit val ConversationDataHasId = new HasId[ConversationData] { type Id = ConvId; def idOf(a: ConversationData) = a.id }
  implicit val IConversationHasId = new HasId[IConversation] { type Id = ConvId; def idOf(a: IConversation) = ConvId(a.getId) }
  implicit val UserInfoHasId = new HasId[UserInfo] { type Id = UserId; def idOf(a: UserInfo) = a.id }
  implicit val UserHasId = new HasId[User] { type Id = UserId; def idOf(a: User) = UserId(a.getId) }
  implicit val ContactHasId = new HasId[Contact] {
    type Id = Either[UserId, ContactId]
    def idOf(c: Contact) = Option(c.getUser).fold2(Right(c.getDetails.asInstanceOf[impl.ContactDetails].id), u => Left(u.asInstanceOf[impl.User].id))
  }
}
