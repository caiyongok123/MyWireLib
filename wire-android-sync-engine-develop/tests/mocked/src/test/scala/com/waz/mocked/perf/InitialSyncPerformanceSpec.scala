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
package com.waz.mocked.perf

import com.waz.api.MockedClientApiSpec
import com.waz.mocked.MockBackend
import com.waz.model._
import com.waz.threading.DispatchQueueStats
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class InitialSyncPerformanceSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with MockedClientApiSpec with MockBackend {
  import DefaultPushBehaviour.Implicit

  lazy val convs = api.getConversations

  override val clientDelay: Timeout = Duration.Zero

  override protected def beforeAll(): Unit = {
    for (_ <- 1 to 200) {
      val conn = addConnection()
      addMessageEvents(conn.convId, selfUserId, 100)
    }
    for (_ <- 1 to 200) {
      val conv = addGroupConversation(getRandomConnections(5) ++: Seq.fill(5)(UserId()))
      addMessageEvents(conv.remoteId, selfUserId, 100)
    }

    DispatchQueueStats.reset()

    super.beforeAll()
  }


  scenario("initial sync") {
    val time = System.currentTimeMillis()
    withDelay {
      convs should have size 400
      Await.result(zmessaging.syncContent.syncStorage(_.getJobs), 1.second) shouldBe empty
    } (120.seconds)

    info(s"synced in: ${(System.currentTimeMillis() - time) / 1000} sek")

    DispatchQueueStats.printStats()
  }

}
