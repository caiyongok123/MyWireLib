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
package com.waz.service.assets

import java.io.File

import com.waz.api
import com.waz.api.AssetFactory
import com.waz.api.impl.AudioAssetForUpload
import com.waz.service.assets.GlobalRecordAndPlayService._
import com.waz.service.assets.PCMRecorder.{Cancelled, CompletionCause, StoppedByUser}
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.testutils.{MockAccountManager, MockAccountsService, MockGlobalModule, MockUiModule, MockUserModule, MockZMessaging}
import com.waz.threading.Threading
import com.waz.threading.Threading.Background
import com.waz.utils._
import org.scalatest._
import org.threeten.bp
import org.threeten.bp.Instant
import org.threeten.bp.Instant.now
import com.waz.ZLog.ImplicitTag._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

@Ignore class RecordAndPlayServiceSpec extends FeatureSpec with Matchers with OptionValues with BeforeAndAfterAll with BeforeAndAfter with RobolectricTests {

  feature("Recording an audio message") {

    scenario("init") {
      ui.onResume()
      soon {
        ui.currentZms.currentValue.flatten shouldBe defined
      }
    }

    scenario("Cancel recording") {
      val spy = new RecordingHandler
      val controls = AssetFactory.recordAudioAsset(spy)
      spy.start.await()
      soon(service.state.currentValue.value should beMatching { case Recording(_, _, _, _, _) => })
      controls.cancel()
      spy.cancel.await()
      soon(service.state.currentValue.value should beMatching { case Idle => })
    }

    scenario("Just record") {
      val spy = new RecordingHandler
      val beforeStart = now
      val controls = AssetFactory.recordAudioAsset(spy)
      val start = spy.start.await()
      start should be > beforeStart
      soon(service.state.currentValue.value should beMatching { case Recording(_, _, _, _, _) => })
      val Recording(recorder, _, _, _, _) = service.state.currentValue.value
      val beforeStop = now
      controls.stop()
      val AudioAssetForUpload(id, entry, duration, _) = spy.complete.await()
      entry.cacheFile.exists shouldBe true
      duration should be > bp.Duration.between(start, beforeStop)
      soon(service.state.currentValue.value should beMatching { case Idle => })
    }

    scenario("Attempt to start recording while recording") {
      val spy, spy2 = new RecordingHandler
      val controls = AssetFactory.recordAudioAsset(spy)
      spy.start.await()
      soon(service.state.currentValue.value should beMatching { case Recording(_, _, _, _, _) => })
      val Recording(_, idOfFirstRecording, _, _, _) = service.state.currentValue.value
      val controls2 = AssetFactory.recordAudioAsset(spy2)
      spy.cancel.await()
      spy2.start.await()
      spy.complete.isCompleted shouldBe false
      soon(service.state.currentValue.value should beMatching { case Recording(_, _, _, _, _) => })
      val Recording(_, idOfSecondRecording, _, _, _) = service.state.currentValue.value
      idOfSecondRecording should not equal idOfFirstRecording
      controls2.stop()
      spy2.complete.await()
      soon(service.state.currentValue.value should beMatching { case Idle => })
    }

    scenario("Race to start recording")(10.times {
      val spy, spy2 = new RecordingHandler
      val controls = AssetFactory.recordAudioAsset(spy)
      val controls2 = AssetFactory.recordAudioAsset(spy2)

      spy.start.await()
      spy.cancel.await()
      spy2.start.await()
      controls2.cancel()
      spy2.cancel.await()
    })

    scenario("File is missing after recording") {
      shouldProvideFile = false
      val spy = new RecordingHandler
      val controls = AssetFactory.recordAudioAsset(spy)
      spy.start.await()
      soon(service.state.currentValue.value should beMatching { case Recording(_, _, _, _, _) => })
      idle(100.millis)
      controls.stop()
      spy.cancel.await()
      spy.complete.isCompleted shouldBe false
      soon(service.state.currentValue.value should beMatching { case Idle => })
    }

    scenario("Record again right after stop") {
      val spy, spy2 = new RecordingHandler
      val controls = AssetFactory.recordAudioAsset(spy)
      spy.start.await()
      controls.stop()

      val controls2 = spy.complete.future.map(_ => AssetFactory.recordAudioAsset(spy2))(Background).await()
      spy2.start.await()
      controls2.stop()
      spy2.complete.await()

      soon(service.state.currentValue.value should beMatching { case Idle => })
    }
  }

  before {
    shouldProvideFile = true
  }

  @volatile private var shouldProvideFile = true

  class MockGlobal extends MockGlobalModule {
    override lazy val recordingAndPlayback = new GlobalRecordAndPlayService(cache, context) {
      override protected def startRecording(destination: File, lengthLimit: FiniteDuration): PCMRecorder = new PCMRecorder {
        override def stopRecording(): Future[CompletionCause] = Future {
          if (shouldProvideFile) IoUtils.copy(getClass.getResourceAsStream("/assets/audio.m4a"), destination)
          StoppedByUser
        }(Threading.Background)
        override def cancelRecording(): Future[CompletionCause] = Future.successful(Cancelled)
        override def onError(f: (Throwable) => Unit): Unit = ()
        override def maxAmplitudeSinceLastCall: Short = 0
        override def onLengthLimitReached(f: => Unit): Unit = ()
      }
    }
  }

  lazy val ui = MockUiModule(new MockZMessaging(new MockUserModule(new MockAccountManager(new MockAccountsService(new MockGlobal)))))
  lazy val service = ui.global.recordingAndPlayback
}

class RecordingHandler extends api.RecordingCallback {
  val start = Promise[Instant]
  val complete = Promise[api.AudioAssetForUpload]
  val cancel = Promise[Unit]

  override def onStart(timestamp: Instant): Unit = start.success(timestamp)
  override def onComplete(recording: api.AudioAssetForUpload, fileSizeLimitReached: Boolean, overview: api.AudioOverview): Unit = complete.success(recording)
  override def onCancel(): Unit = cancel.success(())
}
