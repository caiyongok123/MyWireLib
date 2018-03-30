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

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.impl.conversation.BaseConversation
import com.waz.content.UsersStorage
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConvId, ConversationData, UserId}
import com.waz.threading.Threading
import com.waz.ui._
import com.waz.utils.RichFutureOpt

import scala.concurrent.Future
import scala.concurrent.Future.successful

class Conversation(override val id: ConvId, val initData: ConversationData = ConversationData.Empty)(implicit ui: UiModule) extends BaseConversation {
  import Threading.Implicits.Background

  def this(data: ConversationData)(implicit ui: UiModule) = this(data.id, data)
  private var convIsOtto = false

  set(initData)
  reload()

  def reload() = {
    verbose(s"load $id")
    ui.zms
      .flatMapFuture { zms =>
        zms.convsContent.convById(id).flatMapSome { conv =>
          Conversation.isOtto(conv, zms.usersStorage).map(isOtto => (conv, isOtto))
        }
      }.mapSome { case (conv, otto) =>
        set(conv)
        convIsOtto = otto
      }(Threading.Ui)
  }

  if (initData.displayName == "") {
    // force conversation name update
    // XXX: this is a hack for some random errors, sometimes conv has empty name which is never updated
    ui.zms { _.conversations.forceNameUpdate(id) }
  }

}

object Conversation {
  implicit object Cached extends UiCached[Conversation, ConvId, ConversationData] {
    override def reload(item: Conversation): Unit = item.reload()
    override def update(item: Conversation, d: ConversationData): Unit = item.set(d)
    override def toUpdateMap(values: Seq[ConversationData]) = values.map(c => c.id -> c)(collection.breakOut)
  }

  def isOtto(conv: ConversationData, users: UsersStorage): Future[Boolean] =
    if (conv.convType == ConversationType.OneToOne) users.get(UserId(conv.id.str)).map(_.exists(_.isWireBot))(Threading.Background)
    else successful(false)
}
