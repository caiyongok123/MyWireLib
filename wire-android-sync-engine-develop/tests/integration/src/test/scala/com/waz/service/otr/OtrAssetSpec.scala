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
package com.waz.service.otr

import java.io.ByteArrayInputStream

import akka.pattern.ask
import android.graphics.BitmapFactory
import com.waz.api.Message.Status
import com.waz.api.{Message, ProcessActorSpec, ProvisionedApiSpec}
import com.waz.provision.ActorMessage.{Login, SendImageData, Successful}
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.utils._
import org.robolectric.annotation.Config
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}

import scala.concurrent.duration._

@Config(manifest = "tests/OtrAndroidManifest.xml")
class OtrAssetSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with ProvisionedApiSpec with ProcessActorSpec { test =>

  override val provisionFile = "/two_users_connected.json"
  override val otrOnly = true

  lazy val convs = api.getConversations
  lazy val conv = {
    withDelay { convs should not be empty }
    convs.get(0)
  }
  def msgs = listMessages(conv.id)

  lazy val auto2 = registerDevice("otr_auto2", otrOnly = true)
  lazy val auto2_1 = registerDevice("otr_auto2_1")
  lazy val auto1_1 = registerDevice("otr_auto1_1")
  lazy val auto1_2 = registerDevice("otr_auto1_2")

  scenario("init remote process") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    awaitUi(3.seconds)
  }

  feature("Inline assets") {

    scenario("Receive an image with inline preview") {
      auto2 ? SendImageData(conv.data.remoteId, IoUtils.toByteArray(getClass.getResourceAsStream("/images/penguin.png"))) should eventually(be(Successful))

      withDelay {
        val last = msgs.last
        last.msgType shouldEqual Message.Type.ASSET
        last.state shouldEqual Status.SENT
      }

      val img = new com.waz.api.impl.ImageAsset(msgs.last.assetId)
      withDelay {
        img.isEmpty shouldEqual false
        img.data.data shouldBe 'defined
      }

      val bmp = BitmapFactory.decodeStream(new ByteArrayInputStream(img.data.data.get))
      bmp should not be null
      info(s"preview image size: (${bmp.getWidth}, ${bmp.getHeight})")
    }
  }
}
