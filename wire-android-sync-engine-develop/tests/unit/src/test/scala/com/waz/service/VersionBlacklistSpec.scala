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
package com.waz.service

import com.waz._
import com.waz.content.GlobalPreferences.{LastUpToDateSyncTime, VersionUpToDate}
import com.waz.model._
import com.waz.sync.client.VersionBlacklistClient
import com.waz.testutils.Matchers._
import com.waz.testutils.{DefaultPatienceConfig, MockGlobalModule}
import com.waz.threading.CancellableFuture
import org.scalatest.concurrent.ScalaFutures
import com.waz.ZLog.ImplicitTag._
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

@Ignore class VersionBlacklistSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils with ScalaFutures with DefaultPatienceConfig { test =>
  implicit val timeout: FiniteDuration = 2.seconds

  var blacklist = VersionBlacklist()

  @volatile var loaded = false

  lazy val global = new MockGlobalModule() {
    override lazy val metadata: MetaDataService = new MetaDataService(context) {
      override lazy val appVersion: Int = 123
    }
    override lazy val blacklistClient: VersionBlacklistClient = new VersionBlacklistClient(globalClient, backend) {
      override def loadVersionBlacklist() = {
        loaded = true
        CancellableFuture successful Right(test.blacklist)
      }
    }
  }

  def createService = new VersionBlacklistService(global.metadata, global.prefs, global.blacklistClient) {
    upToDate.disableAutowiring()
  }

  before {
    loaded = false
    resetLastSyncTime()
  }

  feature("Evaluate blacklist") {
    scenario("If the current app version is blacklisted, we're not up-to-date") {
      blacklist = VersionBlacklist(0, Seq(0, 1, 2, global.metadata.appVersion, Integer.MAX_VALUE))
      val service = createService
      withDelay {
        loaded shouldEqual true
        service.upToDate.head.futureValue shouldEqual false
      }
    }

    scenario("If the current app version is older than the oldest supported one, we're not up-to-date") {
      blacklist = VersionBlacklist(global.metadata.appVersion + 1, Seq())
      val service = createService
      withDelay {
        loaded shouldEqual true
        service.upToDate.head.futureValue shouldEqual false
      }
    }

    scenario("If the current app version is exactly the oldest supported one, we're up-to-date") {
      blacklist = VersionBlacklist(global.metadata.appVersion, Seq())
      val service = createService
      withDelay {
        loaded shouldEqual true
        service.upToDate.head.futureValue shouldEqual true
      }
    }

    scenario("If recently synced, return the up-to-date state from the preferences") {
      resetLastSyncTime(System.currentTimeMillis)
      Await.ready(global.prefs.preference(VersionUpToDate) := false, 1.second)
      blacklist = VersionBlacklist(global.metadata.appVersion, Seq())
      val service = createService
      service.upToDate.head should eventually(be(false))
      awaitUi(100.millis)
      withDelay(loaded shouldEqual false)
    }
  }

  def resetLastSyncTime(to: Long = 0L): Unit = Await.ready(global.prefs.preference(LastUpToDateSyncTime) := to, 1.second)
}
