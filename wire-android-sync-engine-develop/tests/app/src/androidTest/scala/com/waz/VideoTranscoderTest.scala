package com.waz

import java.io.File
import java.util.concurrent.CountDownLatch

import android.content.Context
import android.net.Uri
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.waz.api.AssetFactory.LoadCallback
import com.waz.api.{AssetFactory, AssetForUpload}
import com.waz.bitmap.video.VideoTranscoder
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import org.junit.runner.RunWith
import org.junit.{Assert, Before, Test}

import scala.concurrent.Await
import scala.concurrent.duration._

@RunWith(classOf[AndroidJUnit4])
class VideoTranscoderTest {

  @Before def setUp(): Unit = {
    Threading.AssertsEnabled = false
    ZMessaging.onCreate(context)
  }

  @Test def audioTranscodingFrom8kHz(): Unit = {
    val transcoder = VideoTranscoder(context)
    val out = File.createTempFile("video", ".mp4", context.getCacheDir)

    val future = transcoder(Uri.parse("content://com.waz.test/8khz.mp4"), out, { _ => })

    Assert.assertEquals(out, Await.result(future, 15.seconds))
  }

  @Test def assetLoadingWithNoAudio(): Unit = {
    val latch = new CountDownLatch(1)
    var asset = Option.empty[AssetForUpload]

    AssetFactory.videoAsset(Uri.parse("content://com.waz.test/no_audio.mp4"), new LoadCallback {
      override def onLoaded(a: AssetForUpload): Unit = {
        asset = Some(a)
        latch.countDown()

      }
      override def onFailed(): Unit = {
        println(s"transcode failed")
        latch.countDown()
      }
    })

    latch.await()
    Assert.assertTrue(asset.isDefined)
  }

  def context: Context = instr.getTargetContext
  def instr = InstrumentationRegistry.getInstrumentation
}
