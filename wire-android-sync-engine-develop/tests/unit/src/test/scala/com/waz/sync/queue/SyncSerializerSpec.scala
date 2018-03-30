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
package com.waz.sync.queue

import com.waz.RobolectricUtils
import com.waz.model.ConvId
import com.waz.model.sync.SyncJob.Priority
import com.waz.testutils.DefaultPatienceConfig
import org.robolectric.shadows.ShadowLog
import org.scalatest.concurrent.ScalaFutures
import org.scalatest._

import scala.concurrent.duration._

@Ignore class SyncSerializerSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils with ScalaFutures with DefaultPatienceConfig {

  lazy val convId = ConvId()
  lazy val serializer = new SyncSerializer

  after {
    awaitUi(100.millis)
    ShadowLog.stream = null
  }

  feature("priority") {

    scenario("acquire priority lock") {
      serializer.acquire(0).futureValue
      serializer.release()
    }

    scenario("acquire lock 3 times with Priority.Optional - should execute sequentially") {
      ShadowLog.stream = System.out

      serializer.acquire(Priority.Optional).futureValue
      val f1 = serializer.acquire(Priority.Optional)
      val f2 = serializer.acquire(Priority.Optional)
      awaitUi(100.millis)

      f1.isCompleted shouldEqual false
      f2.isCompleted shouldEqual false

      serializer.release()
      f1.futureValue
      f2.isCompleted shouldEqual false

      serializer.release()
      f2.futureValue

      serializer.release()
    }

    scenario("acquire lock with higher priority - should execute in parallel") {
      ShadowLog.stream = System.out

      serializer.acquire(Priority.Optional).futureValue
      val f1 = serializer.acquire(Priority.Optional)
      val f2 = serializer.acquire(Priority.Low)
      val f3 = serializer.acquire(Priority.Normal)

      f2.futureValue // should complete
      f3.futureValue // should complete
      f1.isCompleted shouldEqual false

      serializer.release()
      serializer.release()
      awaitUi(100.millis)
      f1.isCompleted shouldEqual false

      serializer.release()
      f1.futureValue
    }
  }

  feature("conversation lock") {

    scenario("acquire and release conv lock") {
      val lock = serializer.acquire(convId).futureValue
      lock.release()
    }

    scenario("acquire and release same conv lock again") {
      val lock = serializer.acquire(convId).futureValue
      lock.release()
    }
  }
}
