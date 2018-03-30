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

import com.waz.api.ProgressIndicator.State
import com.waz.threading.CancellableFuture.CancelException
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.{RobolectricUtils, api}
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

import scala.concurrent.Promise
import scala.util.{Failure, Success}

@Ignore class FutureLoadHandleSpec extends FeatureSpec with Matchers with RobolectricTests with RobolectricUtils {

  implicit lazy val dispatcher = Threading.Background

  var progress: api.ProgressIndicator = null

  feature("Future load handler progress indicators") {
    val p = Promise[Unit]()

    scenario("Initially, the progress indicator state is unknown.") {
      progress = handle(p).getProgressIndicator
      progress.isIndefinite should be(false)
      progress.getState shouldEqual State.UNKNOWN
    }

    scenario("When completing the future successfully, the progress indicator should be completed.") {
      p.complete(Success({}))
      withDelay {
        progress.getState shouldEqual State.COMPLETED
      }
    }

    scenario("When failing a future, the progress indicator should be failed.") {
      val p = Promise[Unit]()
      progress = handle(p).getProgressIndicator
      p.complete(Failure(new RuntimeException))
      withDelay {
        progress.getState shouldEqual State.FAILED
      }
    }

    scenario("When cancelling a handle, the progress indicator should be cancelled (and underlying future cancelled).") {
      val p = Promise[Unit]()
      val lh = handle(p)
      progress = lh.getProgressIndicator
      lh.cancel()
      withDelay {
        progress.getState shouldEqual State.CANCELLED
      }
      p.future.value match {
        case Some(Failure(_: CancelException)) => // expected
        case v => fail(s"unexpected value: $v")
      }
    }
  }
  private def handle[T](p: Promise[T]): FutureLoadHandle[T] = FutureLoadHandle(new CancellableFuture(p))(_ => ())
}
