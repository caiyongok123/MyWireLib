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

import com.waz.api._
import com.waz.provision.ActorMessage.{ConvMessages, _}
import com.waz.utils._
import org.scalatest._
import com.waz.testutils._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import org.scalatest.concurrent.ScalaFutures
import akka.pattern.ask
import com.waz.api.Message.Part
import com.waz.api.impl.DoNothingAndProceed
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{AssetStatus => _, MessageContent => _, _}
import org.threeten.bp.Instant

import scala.util.Random
import scala.concurrent.duration._

class EphemeralMessageSpec extends FeatureSpec with BeforeAndAfter with Matchers with OptionValues
  with ProvisionedApiSpec with ThreadActorSpec with ScalaFutures with Inspectors with DefaultPatienceConfig {

  override val provisionFile: String = "/three_users_connected.json"

  lazy val conversations = api.getConversations
  lazy val self = api.getSelf
  lazy val conv = {
    withDelay { conversations should not be empty }
    conversations.find(_.getId == provisionedUserId("auto2").str).get
  }
  lazy val group = {
    withDelay { conversations should not be empty }
    conversations.find(_.getType == ConversationType.Group).get
  }
  def messages = listMessages(conv.id)
  def grpMsgs = listMessages(group.id)

  lazy val auto2 = registerDevice("auto2")
  lazy val auto3 = registerDevice("auto3")

  scenario("init") {
    soon {
      conversations should not be empty
      messages should have size 1
    }

    conv.setEphemeralExpiration(EphemeralExpiration.FIVE_SECONDS)
  }

  def withNewMessage(body: => Unit)(implicit msgs: Seq[MessageData] = messages): MessageData = {
    val count = msgs.size

    body

    soon {
      msgs should have size (count + 1)
    }
    msgs.last
  }

  scenario("init remotes") {
    auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    auto3 ? Login(provisionedEmail("auto3"), "auto3_pass") should eventually(be(Successful))
    auto2 ? AwaitSyncCompleted should eventually(be(Successful))
    auto3 ? AwaitSyncCompleted should eventually(be(Successful))
    soon {
      val ConvMessages(ms) = (auto2 ? GetMessages(conv.data.remoteId)).await()
      ms should not be empty
    }
    soon {
      val ConvMessages(ms) = (auto2 ? GetMessages(conv.data.remoteId)).await()
      ms should not be empty
    }
  }

  def assertEphemeralSent(msg: MessageData) = {
    Set(Message.Status.SENT, Message.Status.DELIVERED) should contain (msg.state)
    msg.isEphemeral shouldEqual true
    msg.ephemeral shouldEqual EphemeralExpiration.FIVE_SECONDS
    val time = msg.expiryTime.getOrElse(Instant.MAX)
    time should be > Instant.now
    time should be < (Instant.now + msg.ephemeral.duration)
  }

  feature("Sending") {

    var messageId = MessageId()

    /*def sendMessage(content: MessageContent)(assert: MessageData => Unit)(implicit p: PatienceConfig) = {
      val msg = withNewMessage(conv.sendMessage(content))
      soon {
        assertEphemeralSent(getMessage(msg.id).get)
        assert(getMessage(msg.id).get)
      }
    }*/

    scenario("Send text message") {
      var msg = withNewMessage(zmessaging.convsUi.sendMessage(conv.id, "test msg 1"))
      soon {
        assertEphemeralSent(getMessage(msg.id).get)
        msg = getMessage(msg.id).get
        msg.msgType shouldEqual Message.Type.TEXT
        msg.contentString shouldEqual "test msg 1"
      }
    }

    scenario("Obfuscate text message when timer expires") {
      val msg = messages.last
      msg.contentString shouldEqual "test msg 1"
      messageId = msg.id

      zmessaging.messagesStorage.update(msg.id, _.copy(expiryTime = Some(Instant.now))) // update expiry to make it faster

      soon {
        msg.expiryTime.getOrElse(Instant.MAX) should be < Instant.now
        msg.expired shouldEqual true
        msg.contentString should not equal "test msg 1"
        msg.id shouldEqual messageId
      }

      // message still exists on receiver side
      val ConvMessages(ms) = (auto2 ? GetMessages(conv.data.remoteId)).await()
      ms.find(_.id == messageId) shouldBe defined
    }

    scenario("Delete message when it's read on receiver side") {
      val count = messages.size
      val msg = messages.last

      auto2 ? MarkEphemeralRead(conv.data.remoteId, msg.id) should eventually(be(Successful))

      soon {
        messages.size shouldEqual (count - 1)
      }(patience(10.seconds))
    }

    scenario("Send link") {
      var msg = withNewMessage(zmessaging.convsUi.sendMessage(conv.id, "test link: www.wire.com"))
      soon {
        assertEphemeralSent(getMessage(msg.id).get)
        msg = getMessage(msg.id).get
        msg.msgType shouldEqual Message.Type.RICH_MEDIA
        msg.contentString shouldEqual "test link: www.wire.com"
        msg.content.map(_.tpe) shouldEqual Seq(Part.Type.TEXT, Part.Type.WEB_LINK)
      }
    }

    scenario("Send knock") {
      val count = messages.size
      conv.knock()

      soon {
        messages should have size (count + 1)
        val msg = messages.last
        msg.msgType shouldEqual Message.Type.KNOCK
        assertEphemeralSent(msg)
      }
    }

    scenario("Send another knock") {
      val count = messages.size
      conv.knock()

      soon {
        messages should have size (count + 1)
        val msg = messages.last
        msg.msgType shouldEqual Message.Type.KNOCK
        assertEphemeralSent(msg)
      }
    }

    scenario("Send image") {
      val asset = ImageAssetFactory.getImageAsset(IoUtils.toByteArray(getClass.getResourceAsStream("/images/penguin_128.png")))
      var msg = withNewMessage(zmessaging.convsUi.sendMessage(conv.id, asset))
      soon {
        assertEphemeralSent(getMessage(msg.id).get)
        msg = getMessage(msg.id).get
        msg.msgType shouldEqual Message.Type.ASSET
      }
    }

    scenario("Send file asset") {
      val size = 100000
      val asset = impl.AssetForUpload(AssetId(), Some("asset"), Mime.Default, Some(size)) {
        val data = returning(Array.ofDim[Byte](size.toInt))(Random.nextBytes)
        _ => new ByteArrayInputStream(data)
      }

      var msg = withNewMessage(zmessaging.convsUi.sendMessage(conv.id, asset, DoNothingAndProceed))
      soon {
        assertEphemeralSent(getMessage(msg.id).get)
        msg = getMessage(msg.id).get
        msg.msgType shouldEqual Message.Type.ANY_ASSET
      }
    }

    scenario("Send location") {
      var msg = withNewMessage(zmessaging.convsUi.sendMessage(conv.id, new MessageContent.Location(50f, 20f, "location", 13)))
      soon {
        assertEphemeralSent(getMessage(msg.id).get)
        msg = getMessage(msg.id).get
        msg.msgType shouldEqual Message.Type.LOCATION
      }
    }

    scenario("Obfuscate location when timer expires") {
      val msg = messages.last
      msg.msgType shouldEqual Message.Type.LOCATION
      zmessaging.messagesStorage.update(msg.id, _.copy(expiryTime = Some(Instant.now))) // update expiry to make it faster

      soon {
        msg.expiryTime.getOrElse(Instant.MAX) should be < Instant.now
        msg.expired shouldEqual true
        msg.location.get.getLatitude shouldEqual 0f
        msg.location.get.getLongitude shouldEqual 0f
        msg.location.get.getName should not be "location"
      }
    }
  }

  feature("Receiving") {

    var messageId = MessageId()
    scenario("Set remote conv to ephemeral") {
      soon {
        auto2 ? SetEphemeral(conv.data.remoteId, EphemeralExpiration.FIVE_SECONDS) should eventually(be(Successful))
      }
    }

    scenario("Receive text") {
      val count = messages.size
      (auto2 ? SendText(conv.data.remoteId, "test msg 2")).await() shouldEqual Successful

      soon {
        messages should have size (count + 1)
        val msg = messages.last
        msg.contentString shouldEqual "test msg 2"
        msg.isEphemeral shouldEqual true
        msg.ephemeral shouldEqual EphemeralExpiration.FIVE_SECONDS
        msg.expiryTime.getOrElse(Instant.MAX) shouldEqual Instant.MAX
      }
    }

    scenario("Start expiry timer when message is read - has update listener") {
      val msg = messages(messages.size - 1)
      msg.expiryTime.getOrElse(Instant.MAX) shouldEqual Instant.MAX

      zmessaging.ephemeral.onMessageRead(msg.id)

      soon {
        msg.expiryTime.getOrElse(Instant.MAX) should be < (Instant.now + msg.ephemeral.duration)
      }
    }

    scenario("Delete message once the timer expires") {
      val msg = messages.last
      msg.contentString shouldEqual "test msg 2"
      messageId = msg.id

      soon {
        messages.last.id should not equal msg.id
      }
    }

    scenario("Expired message should also be deleted on sending side") {
      soon {
        val ConvMessages(ms) = (auto2 ? GetMessages(conv.data.remoteId)).await()
        ms.find(_.id == messageId) shouldBe empty
      }
    }

    scenario("Receive knock") {
      val msg = withNewMessage {
        auto2 ? Knock(conv.data.remoteId) should eventually(be(Successful))
      }
      msg.msgType shouldEqual Message.Type.KNOCK
      msg.isEphemeral shouldEqual true
      msg.ephemeral shouldEqual EphemeralExpiration.FIVE_SECONDS
      msg.expiryTime.getOrElse(Instant.MAX) shouldEqual Instant.MAX
    }

    scenario("Receive link") {
      val msg = withNewMessage {
        auto2 ? SendText(conv.data.remoteId, "link: www.wire.com") should eventually(be(Successful))
      }
      soon {
        msg.msgType shouldEqual Message.Type.RICH_MEDIA
        msg.content.map(_.tpe) shouldEqual Seq(Part.Type.TEXT, Part.Type.WEB_LINK)
        msg.isEphemeral shouldEqual true
        msg.ephemeral shouldEqual EphemeralExpiration.FIVE_SECONDS
        msg.expiryTime.getOrElse(Instant.MAX) shouldEqual Instant.MAX
      }
    }

    scenario("Receive image") {
      val msg = withNewMessage {
        auto2 ? SendImageData(conv.data.remoteId, IoUtils.toByteArray(getClass.getResourceAsStream("/images/penguin.png"))) should eventually(be(Successful))
      }
      msg.msgType shouldEqual Message.Type.ASSET
      msg.isEphemeral shouldEqual true
      msg.ephemeral shouldEqual EphemeralExpiration.FIVE_SECONDS
      msg.expiryTime.getOrElse(Instant.MAX) shouldEqual Instant.MAX
    }

    scenario("Start timer when full image is uploaded") {
      val msg = messages(messages.size - 1)
      msg.msgType shouldEqual Message.Type.ASSET

      val image = new com.waz.api.impl.ImageAsset(msg.assetId)
      zmessaging.ephemeral.onMessageRead(msg.id)

      soon {
        image should not be null
        image.isEmpty shouldEqual false
        withClue(s"$image, data: ${image.data}") {
          image.data shouldNot be theSameInstanceAs AssetData.Empty // full image received
        }
        msg.expiryTime.getOrElse(Instant.MAX) should be < (Instant.now + msg.ephemeral.duration)
      } (DefaultPatience.PatienceConfig(15.seconds))
    }

    scenario("Delete asset when timer expires") {
      val count = messages.size
      val msg = messages.last
      msg.msgType shouldEqual Message.Type.ASSET

      val image = new com.waz.api.impl.ImageAsset(msg.assetId)
      zmessaging.ephemeral.onMessageRead(msg.id)

      soon {
        messages.size should be < count
        messages.last.id should not equal msg.id
      }

      // image data should be removed from local storage, and there should be no way to download it anymore (no remoteId)
      val spy = new BitmapSpy(image)
      soon {
        spy.failed shouldEqual true
      }
    }

    lazy val assetData = returning(Array.ofDim[Byte](100000))(Random.nextBytes)

    scenario("Receive file") {
      val msg = withNewMessage {
        val Successful(mid) = (auto2 ? SendAsset(conv.data.remoteId, assetData, "application/octet-stream", "random.dat")).await()
        messageId = MessageId(mid)
      }
      msg.msgType shouldEqual Message.Type.ANY_ASSET
      msg.isEphemeral shouldEqual true
      msg.ephemeral shouldEqual EphemeralExpiration.FIVE_SECONDS
      msg.expiryTime.getOrElse(Instant.MAX) shouldEqual Instant.MAX
    }

    scenario("Start timer when file is uploaded") {
      val msg = messages(messages.size - 1)
      msg.msgType shouldEqual Message.Type.ANY_ASSET

      val asset = new com.waz.api.impl.Asset(msg.assetId, msg.id)
      zmessaging.ephemeral.onMessageRead(msg.id)

      soon {
        asset.getStatus shouldEqual AssetStatus.UPLOAD_IN_PROGRESS
      }
      msg.expiryTime.getOrElse(Instant.MAX) shouldEqual Instant.MAX

      soon {
        asset.getStatus shouldEqual AssetStatus.UPLOAD_DONE
        msg.expiryTime.getOrElse(Instant.MAX) should be < (Instant.now + msg.ephemeral.duration)
      }
    }

    scenario("Receive location") {
      val msg = withNewMessage {
        val Successful(mid) = (auto2 ? SendLocation(conv.data.remoteId, 51f, 20f, "location", 13)).await()
        messageId = MessageId(mid)
      }
      msg.msgType shouldEqual Message.Type.LOCATION
      msg.isEphemeral shouldEqual true
      msg.ephemeral shouldEqual EphemeralExpiration.FIVE_SECONDS
      msg.expiryTime.getOrElse(Instant.MAX) shouldEqual Instant.MAX
      msg.location shouldEqual new MessageContent.Location(51f, 20f, "location", 13)
    }
  }

  feature("Group messages") {

    var messageId = MessageId()

    scenario("Set group ephemeral") {
      group.setEphemeralExpiration(EphemeralExpiration.FIVE_SECONDS)
    }

    scenario("Send text message") {
      var msg = withNewMessage(zmessaging.convsUi.sendMessage(group.id, "test msg 1"))
      soon {
        assertEphemeralSent(getMessage(msg.id).get)
        msg = getMessage(msg.id).get
        msg.msgType shouldEqual Message.Type.TEXT
        msg.contentString shouldEqual "test msg 1"
      }
    }

    scenario("Delete message when it's read by one of receivers") {
      val count = grpMsgs.size
      val msg = grpMsgs.last
      messageId = msg.id

      auto2 ? MarkEphemeralRead(group.data.remoteId, msg.id) should eventually(be(Successful))

      soon {
        grpMsgs.size shouldEqual (count - 1)
      }(patience(10.seconds))
    }

    scenario("Message should not be deleted on third client") {
      awaitUi(3.seconds)
      val ConvMessages(ms) = (auto3 ? GetMessages(group.data.remoteId)).await()
      ms.find(_.id == messageId) shouldBe defined
    }

    scenario("Delete message on third client when it expires") {
      auto3 ? MarkEphemeralRead(group.data.remoteId, messageId) should eventually(be(Successful))
      soon {
        val ConvMessages(ms) = (auto3 ? GetMessages(group.data.remoteId)).await()
        ms.find(_.id == messageId) shouldBe empty
      }
    }
  }
}
