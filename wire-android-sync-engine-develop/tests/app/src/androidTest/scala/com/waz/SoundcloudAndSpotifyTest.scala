package com.waz

import android.content.{Context, Intent}
import android.os.Looper
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.spotify.sdk.android.authentication.{AuthenticationClient, AuthenticationRequest, AuthenticationResponse}
import com.waz.api.Asset.LoadCallback
import com.waz.api._
import com.waz.service.ZMessaging
import com.waz.testapp.SpotifyActivity
import com.waz.threading.Threading
import com.waz.utils.returning
import org.junit.Assert._
import org.junit.runner.RunWith
import org.junit.{After, Before, Ignore, Test}
import org.threeten.bp
import org.threeten.bp.Instant

import scala.concurrent.Promise
import scala.concurrent.duration._

@RunWith(classOf[AndroidJUnit4])
class SoundcloudAndSpotifyTest {
implicit val timeout: Timeout = Timeout(5.seconds)
  val orderedInstants = Ordering.ordered[Instant]

  private var api: ZMessagingApi = _
  private lazy val convs = api.getConversations
  private lazy val conv = convs.get(0)
  private lazy val messages = conv.getMessages

  @Before def setUp(): Unit = {
    Threading.AssertsEnabled = false
    ZMessaging.useStagingBackend()
    api = ZMessagingApiFactory.getInstance(context)
    api.onCreate(context)
    api.onResume()
    val promisedLogin = Promise[Unit]
    api.login(CredentialsFactory.emailCredentials("android.test+af222@wearezeta.com", "12345678"), new LoginListener {
      override def onSuccess(user: Self): Unit = promisedLogin.success(())
      override def onFailed(code: Int, message: String, label: String): Unit = promisedLogin.failure(new RuntimeException(s"($code)[$label] $message"))
    })
    promisedLogin.future.await(Timeout(20.seconds))
    assertFalse(Thread.currentThread eq Looper.getMainLooper.getThread)
    within(30.seconds) {
      assert(convs.size > 1, s"${convs.size} should be > 1")
      assert(messages.size > 0, s"${messages.size} should be > 0")
    }
  }

  @Ignore @Test def soundcloudPlayback(): Unit = {
    val sizeBefore = messages.size
    val content = new MessageContent.Text("http://soundcloud.com/wearepantheon/illenium-said-the-sky-painted-white-ft-cristina-soto")
    conv.sendMessage(content)
    within(5.seconds)(assert(messages.size == sizeBefore + 1, s"${messages.size} should be ${sizeBefore + 1}"))
    val message = messages.getLastMessage
    within(10.seconds) {
      assert(message.getMessageStatus == Message.Status.SENT, "message should be sent")
      assert(! message.isEmpty, "message should not be empty")
      assert(message.getMessageType == Message.Type.RICH_MEDIA, "message should be rich media")
    }
    def mediaAsset = message.getParts.find(_.getPartType == Message.Part.Type.SOUNDCLOUD).get.getMediaAsset

    within(10.seconds)(assert(! mediaAsset.isEmpty, "media asset should not be empty"))
    val promisedControls = Promise[PlaybackControls]
    mediaAsset.getPlaybackControls(new LoadCallback[PlaybackControls] {
      override def onLoaded(a: PlaybackControls): Unit = promisedControls.success(a)
      override def onLoadFailed(): Unit = promisedControls.failure(new RuntimeException("loading playback controls failed for unknown reasons"))
    })
    val controls = promisedControls.future.await
    controls.play()
    6.seconds.idle()
    controls.stop()
    1.seconds.idle()
    controls.play()
    3.seconds.idle()
    controls.setPlayhead(bp.Duration.ofSeconds(1))
    3.seconds.idle()
    controls.stop()
  }

  @Ignore @Test def spotifyPlayback(): Unit = {
    val spotify = api.getSpotify
    val builder = new AuthenticationRequest.Builder(spotify.getClientId, AuthenticationResponse.Type.CODE, "wire://spotify")
    builder.setScopes(Array("user-read-private", "streaming"))
    val request = builder.build()
    val act1 = instr.startActivitySync(returning(new Intent(context, classOf[SpotifyActivity]))(_.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))).asInstanceOf[SpotifyActivity]
    AuthenticationClient.openLoginActivity(act1, 87654321, request)

    println(act1.accessToken.await(Timeout(30.seconds)))
    //    AuthenticationClient.openLoginActivity()

    //    spotify.connect("", new ConnectCallback {
    //      override def onConnect(kindOfAccount: KindOfSpotifyAccount): Unit = ???
    //    })

    val sizeBefore = messages.size
    val content = new MessageContent.Text("spotify:track:6rFjVx5ZCr4Si9f41TkF4F")
    //conv.sendMessage(content)
    //within(5.seconds)(assert(messages.size == sizeBefore + 1, s"${messages.size} should be ${sizeBefore + 1}"))
  }


  @After def tearDown(): Unit = {
    api.logout()
    api.onPause()
    api.onDestroy()
    api = null
  }

  def context: Context = instr.getTargetContext
  def instr = InstrumentationRegistry.getInstrumentation
}
