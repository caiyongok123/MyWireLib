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
package com.waz.service.media

import com.waz._
import com.waz.api.impl.ErrorResponse
import com.waz.api.{MediaProvider, Message}
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.model.messages.media.MediaAssetData.MediaWithImages
import com.waz.model.messages.media.{MediaAssetData, TrackData}
import com.waz.sync.client._
import com.waz.testutils.Matchers._
import com.waz.testutils.{EmptySyncService, MockZMessaging}
import com.waz.threading.Threading
import com.waz.znet.ZNetClient.ErrorOr
import org.scalatest._
import org.threeten.bp.Instant

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@Ignore class RichMediaServiceSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils { test =>
  implicit lazy val dispatcher = Threading.Background

  var richMediaSyncRequest = Option.empty[MessageId]
  var videoSnippetResponse: Either[ErrorResponse, MediaWithImages[TrackData]] = Left(ErrorResponse(499, "test error", "internal-error"))

  lazy val convId = ConvId()
  lazy val remoteId = RConvId()
  lazy val service = new MockZMessaging() {
    override lazy val sync = new EmptySyncService {
      override def syncRichMedia(id: MessageId, priority: Int): Future[SyncId] = {
        richMediaSyncRequest = Some(id)
        super.syncRichMedia(id, priority)
      }
    }

    override lazy val youtubeClient = new YouTubeClient(zNetClient) {
      override def loadVideo(id: String): ErrorOr[MediaWithImages[TrackData]] = Future.successful(videoSnippetResponse)
    }

    insertConv(ConversationData(convId, remoteId, None, UserId(), ConversationType.Group))
  }

  val link = "http://www.youtube.com/watch?v=c0KYU2j0TM4"

  before {
    richMediaSyncRequest = None
  }

  def addMsg(content: Seq[MessageContent] = Seq(MessageContent(Message.Part.Type.YOUTUBE, link))) =
    Await.result(service.messagesStorage.addMessage(MessageData(MessageId(), convId, Message.Type.RICH_MEDIA, UserId(), content)), 5.seconds)

  def loadContent(msg: MessageData) = service.messagesStorage.getMessage(msg.id).map(_.map(_.content))

  feature("Schedule sync") {

    scenario("Schedule sync for newly added youtube message") {
      val msg = addMsg()
      withDelay { richMediaSyncRequest shouldEqual Some(msg.id) }
      loadContent(msg) should eventually(beMatching({
        case Some(Seq(MessageContent(Message.Part.Type.YOUTUBE, `link`, _, None, None, 0, 0, true, _))) => true
      }))
    }

    scenario("Schedule sync for updated message") {
      val msg = addMsg(Seq(MessageContent(Message.Part.Type.TEXT, "some text")))
      awaitUi(100.millis)
      richMediaSyncRequest shouldEqual None
      Await.result(service.messagesContent.updateMessage(msg.id)(_.copy(msgType = Message.Type.RICH_MEDIA, content = Seq(MessageContent(Message.Part.Type.YOUTUBE, link)))), 5.seconds)
      withDelay { richMediaSyncRequest shouldEqual Some(msg.id) }
    }
  }

  feature("Syncing") {

    scenario("Update youtube message content") {
    val msg = addMsg()
      withDelay(richMediaSyncRequest should not be empty)
      val track = TrackData(MediaProvider.YOUTUBE, "video title", None, `link`, None, None, streamable = true, None, None, Instant.now)
      videoSnippetResponse = Right(MediaWithImages(track, Set()))
      service.richmedia.updateRichMedia(msg.id) should eventually(be(Nil))
      loadContent(msg) should eventually(beMatching({
        case Some(Seq(MessageContent(Message.Part.Type.YOUTUBE, `link`, Some(`track`), None, None, 0, 0, false, _))) => true
      }))
    }

    scenario("Update content back to text if the client fails fatally") {
      val msg = addMsg()
      withDelay(richMediaSyncRequest should not be empty)
      val err = ErrorResponse(499, "fatal error", "internal-error")
      videoSnippetResponse = Left(err)
      service.richmedia.updateRichMedia(msg.id) should eventually(be(Nil))
      loadContent(msg) should eventually(beMatching({
        case Some(Seq(MessageContent(Message.Part.Type.TEXT, `link`, None, None, None, 0, 0, false, _))) => true
      }))
    }

    scenario("Report error if client fails non-fatally") {
      val msg = addMsg()
      withDelay(richMediaSyncRequest should not be empty)
      val err = ErrorResponse(500, "recoverable error", "internal-error")
      videoSnippetResponse = Left(err)
      service.richmedia.updateRichMedia(msg.id) should eventually(be(Seq(err)))
      val emptyMedia = MediaAssetData.empty(Message.Part.Type.YOUTUBE)
      loadContent(msg) should eventually(beMatching({
        case Some(Seq(MessageContent(Message.Part.Type.YOUTUBE, `link`, Some(`emptyMedia`), None, None, 0, 0, false, _))) => true
      }))
    }
  }
}
