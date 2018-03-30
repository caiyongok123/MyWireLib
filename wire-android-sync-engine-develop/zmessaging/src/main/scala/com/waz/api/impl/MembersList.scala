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

import com.waz.api
import com.waz.api.UsersList
import com.waz.model.{ConvId, UserData}
import com.waz.service.{SearchKey, ZMessaging}
import com.waz.ui.{SignalLoading, UiModule}
import com.waz.utils.Locales.currentLocaleOrdering
import com.waz.utils.events.Signal


class MembersList(convId: ConvId)(implicit ui: UiModule) extends BaseUsersList with com.waz.api.MembersList { list =>

  override def loadSignal(zms: ZMessaging): Signal[IndexedSeq[UserData]] =
    zms.membersStorage.activeMembers(convId) flatMap { ids =>
        zms.usersStorage.listSignal(ids.filter(_ != zms.selfUserId).toSeq) map { users =>
          users.sortBy(_.getDisplayName)(currentLocaleOrdering)
        }
    }

  override def search(name: String): UsersList = new BaseUsersList {
    val key = SearchKey(name)
    override protected def loadSignal(zms: ZMessaging) = list.loadSignal(zms) map { _.filter(u => key.isAtTheStartOfAnyWordIn(u.searchKey)) }
  }
}

abstract class BaseUsersList(implicit ui: UiModule) extends com.waz.api.UsersList with CoreList[api.User] with SignalLoading {

  var data = IndexedSeq[UserData]()

  addLoader(loadSignal) { data =>
    val shouldNotify = this.data.map(_.id) != data.map(_.id)
    this.data = data
    if (shouldNotify) notifyChanged()
  }

  override def size: Int = data.size

  override def get(index: Int): User = ui.users.getUser(data(index).id)

  protected def loadSignal(zms: ZMessaging): Signal[IndexedSeq[UserData]]
}
