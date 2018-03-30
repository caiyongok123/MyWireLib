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
package com.waz.sync

import com.waz.api._
import com.waz.model.sync.SyncCommand
import com.waz.service.RemoteZmsSpec
import com.waz.testutils._
import com.waz.utils._
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}

import scala.concurrent.duration._

class SyncIndicatorSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with ProvisionedApiSpec with RemoteZmsSpec { test =>
  implicit val timeout: Timeout = 10.seconds

  override val provisionFile = "/two_users_connected.json"

  lazy val convs = api.getConversations
  lazy val search = api.search()

  override val autoLogin: Boolean = false

  override lazy val testClient = returning(new UnreliableAsyncClientImpl)(_.delayInMillis = 500)


  feature("ConversationsList indicator") {

    scenario("Indicate sync on login") {
      withSyncIndicator(convs.getSyncIndicator) {
        login()
      }
    }
  }

  feature("Failed sync") {

    scenario("Show failed state if sync fails") {
      testClient.failFor = Some(".*".r -> "GET")
      val ind = new com.waz.api.impl.SyncIndicator(SyncCommand.SyncConversations)
      zmessaging.sync.syncConversations()
      withDelay(ind.getState shouldEqual SyncState.FAILED)
    }
  }

  def withSyncIndicator(indicator: SyncIndicator, expectedStates: Seq[SyncState] = Seq(SyncState.SYNCING))(body: => Unit) = {
    var states = Set[SyncState]()
    val listener = new UpdateListener {
      override def updated(): Unit = states += indicator.getState
    }
    indicator.addUpdateListener(listener)
    body
    withDelay {
      expectedStates foreach { state =>
        states should contain(state)
      }
    }
    withDelay(indicator.getState shouldEqual SyncState.COMPLETED)
    states
  }
}
