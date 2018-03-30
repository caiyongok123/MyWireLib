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
import com.waz.threading.Threading

import scala.concurrent.Promise

trait EventualReadiness extends api.EventualReadiness {
  private lazy val promisedReadiness = Promise[Unit]
  protected def ready(): Unit = promisedReadiness.trySuccess(())
  def isReady: Boolean = promisedReadiness.isCompleted
  def whenReady(task: Runnable): Unit = promisedReadiness.future.foreach(_ => task.run())(Threading.Ui)
}
