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

object SyncIndicator {
  val PROGRESS_MAX: Int = 10000
  val PROGRESS_UNKNOWN: Int = -1
}

trait SyncIndicator extends UiObservable {
  /**
   * Returns current sync state
   */
  def getState: SyncState

  /**
   * Return current progress as int between 0 and PROGRESS_MAX.
   * Returns PROGRESS_UNKNOWN (-1), if this sync operation doesn't support progress reporting.
   */
  def getProgress: Int

  def hasErrors: Boolean

  def getErrors: java.lang.Iterable[ErrorResponse]

  /**
   * Requests this sync job to be retried.
   * This will only work for FAILED sync, and may only work for some specific sync operations.
   */
  def retry(): Unit
}
