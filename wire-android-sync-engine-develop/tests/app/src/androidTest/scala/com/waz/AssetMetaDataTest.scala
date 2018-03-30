package com.waz

import android.content.Context
import android.net.Uri
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.waz.model.AssetMetaData
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils._
import org.junit.runner.RunWith
import org.junit.{Assert, Before, Test}

import scala.concurrent.Await
import scala.concurrent.duration._

@RunWith(classOf[AndroidJUnit4])
class AssetMetaDataTest {

  @Before def setUp(): Unit = {
    Threading.AssertsEnabled = false
    ZMessaging.onCreate(context)
  }

  @Test def testAudioDurationLoadingFromUri(): Unit = {
    val meta = Await.result(AssetMetaData.Audio(context, Uri.parse("content://com.waz.test/32kbps.m4a")), 5.seconds)

    Assert.assertEquals(309L, meta.fold2(0L, _.duration.getSeconds))
  }

  def context: Context = instr.getTargetContext
  def instr = InstrumentationRegistry.getInstrumentation
}
