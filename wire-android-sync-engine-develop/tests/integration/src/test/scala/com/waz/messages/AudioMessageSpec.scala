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

import java.io.ByteArrayInputStream

import akka.pattern.ask
import com.waz.api._
import com.waz.api.impl.{DoNothingAndProceed}
import com.waz.cache._
import com.waz.model.AssetMetaData.Loudness
import com.waz.model.otr.ClientId
import com.waz.model.{Mime, AssetStatus => _, MessageContent => _, _}
import com.waz.provision.ActorMessage._
import com.waz.service
import com.waz.service.assets.MetaDataService
import com.waz.service.{UserModule, ZMessagingFactory}
import com.waz.testutils.DefaultPatienceConfig
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.threading.CancellableFuture
import com.waz.threading.Threading.Implicits.Background
import com.waz.utils.IoUtils.toByteArray
import org.robolectric.Robolectric.{getShadowApplication, shadowOf}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.threeten.bp

import scala.concurrent.duration._

class AudioMessageSpec extends FeatureSpec with Matchers with BeforeAndAfter with OptionValues with ProvisionedApiSpec with ThreadActorSpec with ScalaFutures with Inspectors with DefaultPatienceConfig {
  scenario("init remote") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    auto2 ? AwaitSyncCompleted should eventually(be(Successful))
    (messages should have size 1).soon
    zmessaging.convsUi.sendMessage(conv.id, "meep")
    (messages should have size 2).soon
  }

  feature("Sending an audio asset message") {
    scenario("Blue sky") {
      val fromBefore = messages.size
      val audio = audioForUpload
      isAudioMessageFromTest += audio.cacheKey

      zmessaging.convsUi.sendMessage(conv.id, audio, DoNothingAndProceed)

      within(1.second)(messages should have size (fromBefore + 1))

      lazy val msg = messages.last
      lazy val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)

      within(10.seconds) {
        asset.isEmpty shouldBe false
        asset.isAudio shouldBe true
        asset.getDuration shouldEqual audioDuration
        asset.getAudioOverview.isEmpty shouldBe false
        asset.getAudioOverview.getLevels(100) shouldEqual levels
        asset.getStatus shouldEqual AssetStatus.UPLOAD_NOT_STARTED // preview should be avaible before sync even started
      }

      within(10.seconds) {
        asset.getStatus shouldEqual AssetStatus.DOWNLOAD_DONE
        asset.isAudio shouldBe true
        asset.getDuration shouldEqual audioDuration
        asset.getAudioOverview.isEmpty shouldBe false
        asset.getAudioOverview.getLevels(100) shouldEqual levels
      }

      val ConvMessages(msgs) = (auto2 ? GetMessages(conv.data.remoteId)).await()
      msgs.last.tpe shouldEqual Message.Type.AUDIO_ASSET
    }
  }

  before {
    isAudioMessageFromTest = Set.empty
  }

  after {
    awaitUi(zmessaging.syncContent.listSyncJobs.await().isEmpty)
  }

  lazy val conversations = api.getConversations
  lazy val self = api.getSelf
  lazy val conv = conversations.head
  lazy val messages = listMessages(conv.id)
  lazy val resolver = shadowOf(getShadowApplication.getContentResolver)

  lazy val auto2 = registerDevice("auto2")

  lazy val audioRaw = toByteArray(getClass.getResourceAsStream("/assets/audio.m4a"))
  lazy val audioDuration = bp.Duration.ofMillis(4921)
  lazy val levels = Array.tabulate(100)(n => math.round((n.toFloat / 99f) * 255f) / 255f)

  def audioForUpload = impl.AssetForUpload(AssetId(), Some("audio.m4a"), Mime.Audio.MP4, Some(audioRaw.length))(_ => new ByteArrayInputStream(audioRaw))

  override val provisionFile: String = "/two_users_connected.json"

  @volatile private var isAudioMessageFromTest = Set.empty[CacheKey]

  override lazy val zmessagingFactory = new ZMessagingFactory(globalModule) {
    override def zmessaging(teamId: Option[TeamId], clientId: ClientId, userModule: UserModule): service.ZMessaging =
      new ApiZMessaging(teamId, clientId, userModule) {
        override lazy val assetMetaData = new MetaDataService(context, cache, assetsStorage, assets, assetGenerator) {
          override def loadMetaData(asset: AssetData, data: LocalData): CancellableFuture[Option[AssetMetaData]] = (asset.mime, data) match {
            case (Mime.Audio(), entry: CacheEntry) if isAudioMessageFromTest(entry.data.key) =>
              CancellableFuture(Some(AssetMetaData.Audio(audioDuration, Some(Loudness(levels.toVector)))))
            case _ => super.loadMetaData(asset, data)
          }
        }
      }
  }
}

