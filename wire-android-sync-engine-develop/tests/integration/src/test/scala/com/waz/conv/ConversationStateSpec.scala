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
package com.waz.conv

import com.waz.ZLog._
import com.waz.api._
import com.waz.model._
import com.waz.service.RemoteZmsSpec
import com.waz.sync.client.ConversationsClient
import com.waz.testutils.DefaultPatienceConfig
import com.waz.testutils.Implicits._
import com.waz.utils._
import com.waz.znet.Request
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FeatureSpec, Matchers, OptionValues}

class ConversationStateSpec extends FeatureSpec with Matchers with OptionValues with ProvisionedApiSpec with RemoteZmsSpec with ScalaFutures with DefaultPatienceConfig { test =>

  private implicit val logTag: LogTag = logTagFor[ConversationStateSpec]

  override val provisionFile = "/three_users_connected.json"

  private lazy val remote = createRemoteZms()
  private lazy val remote1 = createRemoteZms()
  private lazy val remote2 = createRemoteZms()

  private lazy val convs = api.getConversations
  private lazy val archived = convs.getArchivedConversations

  private lazy val conv2 = {
    withDelay(convs should not be empty)
    convs.find(_.data.id == ConvId(provisionedUserId("auto2").str)).get
  }
  private lazy val conv3 = {
    withDelay(convs should not be empty)
    convs.find(_.data.id == ConvId(provisionedUserId("auto3").str)).get
  }

  scenario("mute on remote with legacy request") {
    // post muted event
    val res = remote.account.get.netClient.apply(Request.Put(s"${ConversationsClient.ConversationsPath}/${conv3.data.remoteId}/self", JsonEncoder { _.put("muted", true) })).future.futureValue
    info(s"$res")
    res.status.isSuccess shouldEqual true

    withDelay {
      conv3.isMuted shouldEqual true
    }
  }

  feature("second remote") {
    lazy val remoteConvs = remote1.getConversations
    lazy val remoteArchived = remoteConvs.getArchivedConversations

    lazy val conv2_2 = remoteArchived.find(_.data.remoteId == conv2.data.remoteId).get
    lazy val conv3_2 = remoteConvs.find(_.data.remoteId == conv3.data.remoteId).get

    scenario("login on second remote") {
      awaitUiFuture(remote1.login(email, password))

      withDelay {
        remoteConvs should not be empty
        remoteArchived should not be empty

        conv2_2.isArchived shouldEqual true
        conv2_2.isMuted shouldEqual false
        conv3_2.isMuted shouldEqual true
      }
    }

    scenario("unmute on second remote with regular call") {
      conv3_2.setMuted(false)

      withDelay {
        conv3_2.isMuted shouldEqual false
        conv3.isMuted shouldEqual false
      }
    }

    scenario("unarchive on second remote with api call") {
      conv2_2.setArchived(false)

      withDelay {
        conv2_2.isArchived shouldEqual false
        conv2.isArchived shouldEqual false
      }
    }

  }

  feature("third remote") {
    lazy val remoteConvs = remote1.getConversations
    lazy val remoteArchived = remoteConvs.getArchivedConversations

    lazy val conv2_2 = remoteConvs.find(_.data.remoteId == conv2.data.remoteId).get
    lazy val conv3_2 = remoteConvs.find(_.data.remoteId == conv3.data.remoteId).get

    scenario("login on third remote") {
      awaitUiFuture(remote2.login(email, password))

      withDelay {
        remoteConvs should not be empty
        remoteArchived shouldBe empty

        conv2_2.isMuted shouldEqual false
        conv2_2.isArchived shouldEqual false
        conv3_2.isMuted shouldEqual false
        conv3_2.isArchived shouldEqual false
      }
    }

    scenario("mute on remote with regular call") {
      conv3_2.setMuted(true)

      withDelay {
        conv3_2.isMuted shouldEqual true
        conv3.isMuted shouldEqual true
      }
    }

    scenario("archive on remote with api call") {
      conv2_2.setArchived(true)

      withDelay {
        conv2_2.isArchived shouldEqual true
        conv2.isArchived shouldEqual true
      }
    }
  }

}
