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

import akka.pattern.ask
import android.content.Context
import android.net.{ConnectivityManager, NetworkInfo}
import com.waz.api.{Message => ApiMessage, _}
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConvId, MessageData, RConvId}
import com.waz.provision._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.testutils.UnreliableAsyncClientImpl
import com.waz.utils._
import com.waz.utils.events.EventContext
import org.robolectric.Robolectric
import org.scalatest.{FeatureSpec, Matchers}
import org.threeten.bp.Instant

import scala.concurrent.Await
import scala.concurrent.duration._

class ConversationMessagesSpec extends FeatureSpec with Matchers with ProvisionedApiSpec with ThreadActorSpec {
  import ActorMessage._
  implicit val ev: EventContext = EventContext.Global

  override val provisionFile = "/one_conversation.json"

  lazy val networkController = Robolectric.shadowOf(context.getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager])

  lazy val conversations = api.getConversations
  lazy val archived = conversations.getArchivedConversations

  lazy val self = api.getSelf

  lazy val conv = {
    withDelay {
      conversations should have size 1
    }
    conversations.asScala.find(_.getType == ConversationType.OneToOne).get
  }

  override lazy val testClient: UnreliableAsyncClientImpl = new UnreliableAsyncClientImpl

  var defaultNetworkInfo: NetworkInfo = _

  lazy val auto2 = registerDevice("ConversationMessagesSpec_auto2")

  override def beforeAll(): Unit = {
    testClient.delayInMillis = 100L
    defaultNetworkInfo = networkController.getActiveNetworkInfo
    super.beforeAll()
  }

  feature("Preparations") {
    scenario("Prepare self") {
      withDelay {
        conversations should have size 1
      }
      withDelay(listMessages(conv.id) should not be empty)
    }

    scenario("Prepare other user") {
      auto2 ? Login(provisionedEmail("auto2"), "auto2_pass") should eventually(be(Successful))
    }
  }

  feature("Receive incoming text messages") {
    scenario("auto2: Send text message") {
      val count = listMessages(conv.id).size
      auto2 ! SendText(RConvId(self.getUser.getId), msg = "meep")
      withDelay {
        listMessages(conv.id) should have size (count + 1)
      }
    }

    scenario("Should receive incoming text message") {
      withDelay {
        lastMessage(conv.id).map(_.contentString) shouldEqual Some("meep")
      }
      lastMessage(conv.id).map(_.state) shouldEqual Some(ApiMessage.Status.SENT)
      conv.getFailedCount shouldEqual 0
    }

    scenario("Received message should be marked as first message") {
      lastMessage(conv.id).map(_.firstMessage) shouldEqual Some(true)
    }
  }

  feature("Sending text messages") {
    @volatile var pushReceived = false
    scenario("Send a single message") {
      val count = listMessages(conv.id).size

      zmessaging.convsUi.sendMessage(conv.id, "test")

      withDelay {
        listMessages(conv.id) should have size (count + 1)
      }
      lastMessage(conv.id).get.msgType shouldEqual ApiMessage.Type.TEXT
      lastMessage(conv.id).get.contentString shouldEqual "test"
    }

    scenario("Sent message should not be marked as first message") {
      lastMessage(conv.id).get.firstMessage shouldEqual false
    }

    scenario("Message should be pending and not yet be pushed") {
      lastMessage(conv.id).get.state shouldEqual ApiMessage.Status.PENDING
      withDelay {
        conv.getFailedCount shouldEqual 0
      }
    }

    scenario("After sync, message should be sent") {
      withDelay {
        lastMessage(conv.id).get.state shouldEqual ApiMessage.Status.SENT
      }
      withDelay {
        conv.getFailedCount shouldEqual 0
      }
    }
  }

  feature("Sending image messages") {
    scenario("Send a single image") {
      val count = listMessages(conv.id).size

      val im = api.ui.images.createImageAssetFrom(IoUtils.toByteArray(resourceStream("/images/penguin.png")))
      conv.getFailedCount shouldEqual 0
      zmessaging.convsUi.sendMessage(conv.id, im)

      withDelay {
        listMessages(conv.id) should have size (count + 1)
        lastMessage(conv.id).get.msgType shouldEqual ApiMessage.Type.ASSET
      }
    }

    scenario("Message should be pending") {
      lastMessage(conv.id).get.state shouldEqual ApiMessage.Status.PENDING
      withDelay {
        conv.getFailedCount shouldEqual 0
      }
    }

    scenario("After sync, message should be sent") {
      awaitUi(1.second)
      withDelay {
        assets(listMessages(conv.id)).last.state shouldEqual ApiMessage.Status.SENT
      }
      withDelay {
        conv.getFailedCount shouldEqual 0
      }
    }
  }

  feature("Receive incoming image messages") {
    scenario("auto2: Send image message") {
      auto2 ? SendImageData(RConvId(self.getUser.getId), IoUtils.toByteArray(getClass.getResourceAsStream("/images/penguin.png"))) should eventually(be(Successful))
    }

    scenario("Should receive incoming image message") {
      withDelay {
        assets(listMessages(conv.id)) should have size 2
        val msg = assets(listMessages(conv.id)).last
        Await.result(zmessaging.assetsStorage.get(msg.assetId), 5.seconds) shouldBe 'defined
      }(60.seconds)

      withDelay {
        assets(listMessages(conv.id)) foreach {
          _.state shouldEqual ApiMessage.Status.SENT
        }
      }
    }
  }

  feature("Failing requests") {
    var failedTextMessage: Option[MessageData] = None
    var failedImageMessage: Option[MessageData] = None

    scenario("Sending should not fail on first try if not offline") {
      withDelay {
        conv.getFailedCount shouldEqual 0
      }
      testClient.failFor = Some(".*".r -> "POST")
      zmessaging.convsUi.sendMessage(conv.id, "test failed")
      failedTextMessage = withDelay {
        returning(lastMessage(conv.id))(_.get.contentString shouldEqual "test failed")
      }
      awaitUi(3.seconds)
      conv.getFailedCount shouldEqual 0
      lastMessage(conv.id).get.state shouldEqual ApiMessage.Status.PENDING
    }

    scenario("Sent message should fail when retried in offline mode") {
      zmessaging.network.networkMode ! NetworkMode.OFFLINE

      withDelay {
        zmessaging.sync.postMessage(failedTextMessage.get.id, ConvId(conv.getId), Instant.EPOCH)
        conv.getFailedCount shouldEqual 1
        lastMessage(conv.id).get.state shouldEqual ApiMessage.Status.FAILED
      }(20.seconds)
    }

    scenario("Offline, sending an image as a second message.") {
      val im = api.ui.images.createImageAssetFrom(IoUtils.toByteArray(resourceStream("/images/penguin_128.png")))
      zmessaging.convsUi.sendMessage(conv.id, im)
      failedImageMessage = withDelay {
        returning(lastMessage(conv.id))(_.get.msgType shouldEqual ApiMessage.Type.ASSET)
      }
      withDelay {
        listMessages(conv.id) filter (_.state == ApiMessage.Status.FAILED) should have size 2
        conv.getFailedCount shouldEqual 2
      }
    }

    scenario("Read messages (while online) don't count as failed") {
      testClient.failFor = None
      zmessaging.network.updateNetworkMode()
      conv.getFailedCount shouldEqual 2
      listMessages(conv.id).size should be > 0
      withDelay {
        conv.getFailedCount shouldEqual 0
      } // after being read, messages don't count here anymore
    }

    scenario("Back online, sending a third message should leave the two previous messages failed.") {
      zmessaging.convsUi.sendMessage(conv.id, "back online")
      withDelay {
        listMessages(conv.id) filter (_.state == ApiMessage.Status.FAILED) should have size 2
      }
    }

    scenario("Back online, retrying a text message should succeed.") {
      failedTextMessage should be('defined)
      failedTextMessage foreach { msg =>
        msg.state shouldEqual ApiMessage.Status.FAILED
        zmessaging.messages.retryMessageSending(msg.convId, msg.id)
        withDelay {
          getMessage(msg.id).get.state shouldEqual ApiMessage.Status.PENDING
        }
        withDelay {
          getMessage(msg.id).get.state shouldEqual ApiMessage.Status.SENT
        }
        withDelay {
          getMessage(msg.id).get.msgType shouldEqual ApiMessage.Type.TEXT
        }
        withDelay {
          listMessages(conv.id) filter (_.state == ApiMessage.Status.FAILED) should have size 1
        }
        withDelay {
          conv.getFailedCount shouldEqual 0
        }
      }
    }

    scenario("Back online, retrying an image message should succeed.") {
      failedImageMessage should be('defined)
      failedImageMessage foreach { msg =>
        msg.state shouldEqual ApiMessage.Status.FAILED
        zmessaging.messages.retryMessageSending(msg.convId, msg.id)
        withDelay {
          getMessage(msg.id).get.state shouldEqual ApiMessage.Status.PENDING
        }
        withDelay {
          getMessage(msg.id).get.state shouldEqual ApiMessage.Status.SENT
        }
        withDelay {
          getMessage(msg.id).get.msgType shouldEqual ApiMessage.Type.ASSET
        }
        withDelay {
          listMessages(conv.id) filter (_.state == ApiMessage.Status.FAILED) should have size 0
        }
        withDelay {
          conv.getFailedCount shouldEqual 0
        }
      }
    }
  }

  feature("Clearing a conversation") {
    scenario("Clear the conversation then receive a message again") {
      info(s"conv: ${conv.getId} (self: ${self.getEmail})")

      conv.clear()

      withDelay {
        listMessages(conv.id) shouldBe empty
        conv shouldBe 'archived
        conv shouldBe 'active
        conversations.getConversationIndex(conv.getId) shouldEqual -1
        archived.getConversationIndex(conv.getId) shouldEqual -1
      }

      auto2 ! SendText(RConvId(self.getUser.getId), msg = "meep")

      withDelay {
        listMessages(conv.id) should have size 1
        lastMessage(conv.id).get.contentString shouldEqual "meep"
        conv should not(be('archived))
        conv shouldBe 'active
        conversations.getConversationIndex(conv.getId) should be >= 0
        archived.getConversationIndex(conv.getId) shouldEqual -1
      }
    }
  }

  def assets(msgs: Seq[MessageData]) = msgs filter (_.msgType == ApiMessage.Type.ASSET)
}
