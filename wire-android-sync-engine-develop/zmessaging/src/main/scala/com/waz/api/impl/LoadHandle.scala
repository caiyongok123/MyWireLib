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
import com.waz.api.LoadHandle
import com.waz.api.ProgressIndicator.State
import com.waz.threading.{Threading, CancellableFuture}

import scala.util.Success


object EmptyLoadHandle extends api.LoadHandle {
  override def getProgressIndicator: api.ProgressIndicator = ProgressIndicator.Empty
  override def cancel(): Unit = {}
}

case class FutureLoadHandle[A](var future: CancellableFuture[A])(onComplete: A => Unit) extends LoadHandle {
  private val progressIndicator = new ProgressIndicator

  var cancelled = false

  future.onComplete {
    case _ if cancelled => progressIndicator.setState(State.CANCELLED)
    case Success(result) =>
      future = null
      progressIndicator.setState(State.COMPLETED)
      onComplete(result)
    case _ =>
      future = null
      progressIndicator.setState(State.FAILED)
  } (Threading.Ui)

  override def getProgressIndicator: api.ProgressIndicator =  progressIndicator

  override def cancel(): Unit = {
    Threading.assertUiThread()
    cancelled = true
    if (future != null) future.cancel()("LoadHandle.cancel")
    future = null
  }
}
