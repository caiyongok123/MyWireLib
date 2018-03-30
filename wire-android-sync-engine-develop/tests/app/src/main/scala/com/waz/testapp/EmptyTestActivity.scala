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
package com.waz.testapp

import java.io.File

import android.app.{Activity, DownloadManager}
import android.content.{Context, Intent}
import android.graphics.Bitmap
import android.os.{Bundle, Environment}
import android.provider.MediaStore
import android.view.View
import android.view.View.OnClickListener
import android.widget.{Button, ImageView}
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.BitmapCallback
import com.waz.api._
import com.waz.bitmap.video.VideoTranscoder
import com.waz.model.{AssetMetaData, Mime}
import com.waz.utils.{wrappers, _}
import com.waz.utils.events.ActivityEventContext

class EmptyTestActivity extends Activity with ActivityEventContext {
  import com.waz.threading.Threading.Implicits.Ui

  lazy val btnCapture = findViewById(R.id.btnCapture).asInstanceOf[Button]
  lazy val btnDecode = findViewById(R.id.btnDecode).asInstanceOf[Button]
  lazy val imageView = findViewById(R.id.image).asInstanceOf[ImageView]

  private var handle: LoadHandle = _

  private var api: ZMessagingApi = _

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    api = ZMessagingApiFactory.getInstance(this)

    btnCapture.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        startActivityForResult(new Intent(MediaStore.ACTION_VIDEO_CAPTURE), 1)
      }
    })

    btnDecode.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        Option(handle).foreach(_.cancel())
        val asset = ImageAssetFactory.getImageAsset(R.raw.gif_image)
        handle = asset.getBitmap(imageView.getWidth, new BitmapCallback {
          override def onBitmapLoadingFailed(): Unit = ()
          override def onBitmapLoaded(b: Bitmap): Unit = imageView.setImageBitmap(b)
        })
      }
    })
  }


  override def onResume(): Unit = {
    super.onResume()
    api.onResume()
  }


  override def onPause(): Unit = {
    super.onPause()
    api.onPause()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    if (resultCode == Activity.RESULT_OK && requestCode == 1) {
      val file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "camera_video.mp4")
      (for {
       meta1 <- AssetMetaData.Video(this, data.getData)
        _ = info(s"captured video meta: $meta1")
        _ <- VideoTranscoder(this).apply(new wrappers.AndroidURI(data.getData), file, { data => verbose(s"transcoding $data") }).future
        meta2 <- AssetMetaData.Video(file)
      } yield {
        info(s"transcoded video meta: $meta2")
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE).asInstanceOf[DownloadManager]
        downloadManager.addCompletedDownload("camera_video.mp4", "camera_video.mp4", false, Mime.Video.MP4.str, file.getAbsolutePath, file.length(), true)
      }).recoverWithLog()
    }
  }
}
