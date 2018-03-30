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
package com.waz.messages

import java.util.concurrent.atomic.AtomicLong

import akka.pattern.ask
import com.waz.api._
import com.waz.api.impl.LocalImageAsset
import com.waz.cache.CacheEntry
import com.waz.model.TeamId
import com.waz.model.otr.ClientId
import com.waz.provision.ActorMessage.{AwaitSyncCompleted, Login, Successful}
import com.waz.service._
import com.waz.sync.client.AssetClientImpl
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.utils.IoUtils
import com.waz.znet.Request
import com.waz.znet.ZNetClient.ErrorOrResponse
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.duration._

class ImageAssetMessageSpec extends FeatureSpec with Matchers with ProvisionedApiSpec with ThreadActorSpec {
  override val provisionFile: String = "/two_users_connected.json"

  lazy val conversations = api.getConversations
  lazy val self = api.getSelf
  lazy val conv = conversations.head
  def messages = listMessages(conv.id)

  lazy val auto2 = registerDevice("auto2")

  scenario("init") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    auto2 ? AwaitSyncCompleted should eventually(be(Successful))
    soon {
      conversations should not be empty
      messages should have size 1
    }
  }

  scenario("Post text in new conv") {
    zmessaging.convsUi.sendMessage(conv.id, "first msg")
    withDelay {
      messages.last.msgType shouldEqual Message.Type.TEXT
      messages.last.state shouldEqual Message.Status.SENT
    }
  }

  scenario("Post image asset followed by text") {
    val asset = ImageAssetFactory.getImageAsset(IoUtils.toByteArray(getClass.getResourceAsStream("/images/big.png")))
    zmessaging.convsUi.sendMessage(conv.id, asset)

    withDelay {
      messages.last.msgType shouldEqual Message.Type.ASSET
      messages.last.state shouldEqual Message.Status.PENDING
    }
    val assetMsg = messages.last

    zmessaging.convsUi.sendMessage(conv.id, "test message")

    withDelay {
      assetMsg.state shouldEqual Message.Status.SENT
      messages.last.msgType shouldEqual Message.Type.TEXT
    }
  }

  scenario("Post a giphy search result as message") {
    val result = api.getGiphy.search("animated")
    (result shouldBe 'ready).soon
    val imageFromGiphy = result.head
    imageFromGiphy.shouldBeAnAnimatedGif

    (messages should have size 1).soon
    downloads.set(0)

    zmessaging.convsUi.sendMessage(conv.id, imageFromGiphy)
    (messages should have size 2).soon

    val postedImage = new com.waz.api.impl.ImageAsset(messages(1).assetId)

    postedImage should not(be(a[LocalImageAsset]))
    postedImage.shouldBeAnAnimatedGif
    idle(5.seconds)
    postedImage.shouldBeAnAnimatedGif
    withClue("Image should not be downloaded but fetched from cache instead.")(downloads.get shouldEqual 0)
  }

  override lazy val zmessagingFactory = new ZMessagingFactory(globalModule) {
    override def zmessaging(teamId: Option[TeamId], clientId: ClientId, user: UserModule): ZMessaging =
      new ZMessaging(teamId, clientId, user) {
        override lazy val assetClient = new AssetClientImpl(zNetClient) {

          override def loadAsset(req: Request[Unit]): ErrorOrResponse[CacheEntry] = {
            downloads.incrementAndGet()
            super.loadAsset(req)
          }
        }
      }
  }

  private val downloads = new AtomicLong(0)
}
