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
import com.waz.api
import com.waz.api.InputStateIndicator.KnockState
import com.waz.api.UsersList
import com.waz.model.{ConvId, UserId}
import com.waz.threading.Threading
import com.waz.ui.{SignalLoading, UiModule}
import com.waz.utils.events.EventContext

class InputStateIndicator(conv: ConvId)(implicit ui: UiModule) extends api.InputStateIndicator with UiObservable with SignalLoading  {
  import Threading.Implicits.Background
  private implicit val ev = EventContext.Global
  private val typingUsers = new TypingUsersList(conv)

  override def getKnockState = KnockState.NONE

  override def getTypingUsers: UsersList = typingUsers

  override def textChanged(): Unit = {
    verbose(s"textChanged()")
    ui.zms { _.typing.selfChangedInput(conv) }
  }

  override def textCleared(): Unit = {
    verbose(s"textCleared()")
    ui.zms { _.typing.selfClearedInput(conv) }
  }
}

class TypingUsersList(convId: ConvId)(implicit ui: UiModule) extends UsersList with CoreList[api.User] with SignalLoading {
  private var data = IndexedSeq.empty[UserId]

  addLoader(_.typing.typingUsers(convId)) { users =>
    this.data = users
    notifyChanged()
  }

  def get(pos: Int): User = ui.users.getUser(data(pos))

  def size: Int = data.size
}
