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

import com.waz.model.sync.{SerialExecutionWithinConversation, SyncRequest}
import com.waz.sync._
import com.waz.sync.queue.ConvLock

import scala.concurrent.Future

class EmptySyncHandler extends SyncHandler {
  override def apply(req: SyncRequest): Future[SyncResult] = Future.successful(SyncResult.Success)
  override def apply(req: SerialExecutionWithinConversation, lock: ConvLock): Future[SyncResult] = Future.successful(SyncResult.Success)
}
