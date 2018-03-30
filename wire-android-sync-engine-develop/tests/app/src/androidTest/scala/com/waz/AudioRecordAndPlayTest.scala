package com.waz

import android.content.Context
import android.net.Uri
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.waz.api.{AudioEffect, ZMessagingApi, ZMessagingApiFactory}
import com.waz.api.impl.AudioAssetForUpload
import com.waz.model.{AssetId, Mime}
import com.waz.service.ZMessaging
import com.waz.service.assets.GlobalRecordAndPlayService._
import com.waz.service.assets.PCM
import com.waz.threading.Threading
import org.junit.Assert._
import org.junit.runner.RunWith
import org.junit.{After, Before, Test}
import org.threeten.bp
import org.threeten.bp.Instant

import scala.PartialFunction.cond
import scala.concurrent.Promise
import scala.concurrent.duration._

@RunWith(classOf[AndroidJUnit4])
class AudioRecordAndPlayTest {

  implicit val timeout: Timeout = Timeout(5.seconds)
  val orderedInstants = Ordering.ordered[Instant]
  import orderedInstants._

  private var zmsApi: ZMessagingApi = _

  @Before def before(): Unit = {
    Threading.AssertsEnabled = false
    ZMessaging.onCreate(context)
  }

  @Test def audioRecording(): Unit = {
    val ongoingRecording = new RecordingHandler
    val beforeStart = Instant.now
    val recControls = api.AssetFactory.recordAudioAsset(ongoingRecording)
    assertTrue(beforeStart < ongoingRecording.start.await)
    5.seconds.idle()
    recControls.stop()
    val (asset @ AudioAssetForUpload(assetId, entry, _, _), limitReached, overview) = ongoingRecording.complete.await
    assertEquals(Mime.Audio.PCM, asset.mimeType.await)
    assertTrue(asset.sizeInBytes.await.get > 0L)
    assertFalse(limitReached)
    assertFalse(overview.isEmpty)

    val service = ZMessaging.currentGlobal.recordingAndPlayback
    val media = PCMContent(entry.cacheFile)
    val id = AssetMediaKey(assetId)
    assertTrue(cond(service.play(id, media).await) { case Playing(_, `id`) => true })
    7.seconds.idle()
    assertTrue(cond(service.state.head.await) { case Idle => true })

    val asset2 @ AudioAssetForUpload(assetId2, entry2, _, _) = service.applyAudioEffect(AudioEffect.VOCODER_MED, entry.cacheFile).await
    val media2 = PCMContent(entry2.cacheFile)
    val id2 = AssetMediaKey(assetId2)
    assertTrue(cond(service.play(id2, media2).await) { case Playing(_, `id2`) => true })
    7.seconds.idle()
    assertTrue(cond(service.state.head.await) { case Idle => true })
  }

  @Test def audioRecordingLimit(): Unit = {
    val service = ZMessaging.currentGlobal.recordingAndPlayback

    val id = AssetMediaKey(AssetId())
    val (start, promisedResult) = service.record(id, 5.seconds).await
    val RecordingSuccessful(asset: AudioAssetForUpload, limitReached) = promisedResult.await(Timeout(20.seconds))

    assertTrue(limitReached)
    assertTrue(asset.sizeInBytes.await.get <= (5.seconds.toMillis * PCM.sampleRate * 2L))
  }

  @Test def audioPlayback(): Unit = {
    val service = ZMessaging.currentGlobal.recordingAndPlayback
    val media = UnauthenticatedContent(Uri.parse("content://com.waz.test/audio.m4a"))
    val id = AssetMediaKey(AssetId())
    assertTrue(cond(service.play(id, media).await) { case Playing(_, `id`) => true })
    3.seconds.idle()
    assertTrue(cond(service.pause(id).await) { case Paused(_, `id`, _, _) => true })
    1.second.idle()
    assertTrue(cond(service.play(id, media).await) { case Playing(_, `id`) => true })
    1.second.idle()
    assertTrue(cond(service.setPlayhead(id, media, bp.Duration.ofSeconds(7)).await) { case Playing(_, _) => true })
    2.seconds.idle()
    assertTrue(cond(service.pause(id).await) { case Paused(_, `id`, _, _) => true })
    assertTrue(cond(service.setPlayhead(id, media, bp.Duration.ZERO).await) { case Paused(_, `id`, MediaPointer(media, bp.Duration.ZERO), _) => true })
    assertTrue(cond(service.play(id, media).await) { case Playing(_, `id`) => true })
    2.seconds.idle()

    val id2 = AssetMediaKey(AssetId())
    assertTrue(cond(service.play(id2, media).await) { case Playing(_, `id2`) => true })
    2.seconds.idle()
    assertTrue(cond(service.pause(id2).await) { case Paused(_, `id2`, _, _) => true })
  }

  @Test def setPlayheadBeforePlayback(): Unit = {
    val service = ZMessaging.currentGlobal.recordingAndPlayback
    val media = UnauthenticatedContent(Uri.parse("content://com.waz.test/audio.m4a"))
    val id = AssetMediaKey(AssetId())
    val SevenSeconds = bp.Duration.ofSeconds(7)
    assertTrue(cond(service.setPlayhead(id, media, SevenSeconds).await) { case Paused(_, `id`, MediaPointer(media, SevenSeconds), _) => true })
    assertTrue(cond(service.play(id, media).await) { case Playing(_, `id`) => true })
    2.seconds.idle()
    assertTrue(cond(service.pause(id).await) { case Paused(_, `id`, _, _) => true })
  }

  def context: Context = instr.getTargetContext
  def instr = InstrumentationRegistry.getInstrumentation
}

class RecordingHandler extends api.RecordingCallback {
  val start = Promise[Instant]
  val complete = Promise[(api.AudioAssetForUpload, Boolean, api.AudioOverview)]
  val cancel = Promise[Unit]

  override def onStart(timestamp: Instant): Unit = start.success(timestamp)
  override def onComplete(recording: api.AudioAssetForUpload, fileSizeLimitReached: Boolean, overview: api.AudioOverview): Unit = complete.success((recording, fileSizeLimitReached, overview))
  override def onCancel(): Unit = cancel.success(())
}
