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
import com.waz.api
import com.waz.api.SyncState
import com.waz.model.sync.SyncCommand
import com.waz.sync.SyncRequestServiceImpl.SyncMatcher
import com.waz.ui.{SignalLoading, UiModule}

import scala.collection.JavaConverters._

class SyncIndicator(matchers: Seq[SyncMatcher])(implicit ui: UiModule) extends api.SyncIndicator with UiObservable with SignalLoading {

  private implicit val logTag: LogTag = logTagFor[SyncIndicator]

  def this(cmd: SyncCommand, cmds: SyncCommand*)(implicit ui: UiModule) = this((cmd +: cmds).map(SyncMatcher(_, None)))
  def this(m: SyncMatcher, ms: SyncMatcher*)(implicit ui: UiModule) = this(m +: ms)

  var data = SyncIndicator.Data()

  addLoader(_.syncRequests.syncState(matchers)) { data =>
    this.data = data
    notifyChanged()
  }

  override def getState: SyncState = data.state

  override def getProgress: Int = data.progress

  override def retry(): Unit = {} // TODO: implement retry

  override def hasErrors: Boolean = data.errors.nonEmpty

  override def getErrors = data.errors.asJava
}

object SyncIndicator {
  case class Data(state: SyncState = SyncState.COMPLETED, progress: Int = 0, errors: Seq[api.ErrorResponse] = Nil)
}
