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
package com.waz.api.impl

import android.database.Cursor
import android.net.Uri
import android.webkit.MimeTypeMap
import com.waz.model.{AssetId, Mime}
import com.waz.service.ZMessaging
import com.waz.testutils.Matchers._
import com.waz.testutils.TestContentProvider
import com.waz.utils.wrappers.URI
import org.robolectric.Robolectric
import org.robolectric.shadows.{ShadowContentResolver2, ShadowLog}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest._

@Ignore class AssetForUploadSpec extends FeatureSpec with Matchers with RobolectricTests with BeforeAndAfter with TableDrivenPropertyChecks {

  scenario("Determining a MIME type") {
    ShadowLog.stream = System.out

    forAll (Table(("URI", "expected MIME type"),
      (URI.parse("content://my-provider/something/my-file.txt"), plainText),
      (URI.parse("content://my-provider/something/my-file.pdf"), pdf),
      (URI.parse("content://my-provider/something/my-file.txt.pdf"), pdf),
      (URI.parse("content://my-provider/something/my-file.txt?option=3"), plainText),
      (URI.parse("file:///data/my-file.txt"), plainText),
      (URI.parse("file:/data/my-file.txt"), plainText),
      (URI.parse("file:///data/my-file.txt?option=3&size=42"), plainText),
      (URI.parse("file:/data/my-file.doc.txt"), plainText),
      (URI.parse("file:/data/.my-file.txt"), plainText),
      (URI.parse("file:/data/.txt"), Mime.Default),
      (URI.parse("file:/data/txt"), Mime.Default),
      (URI.parse("file:/data/my-file"), Mime.Default),
      (URI.parse("file:/data/my-file.doc"), Mime.Default),
      (URI.parse("file:/data/my-file.txt.doc"), Mime.Default)
    )) { (uri: URI, expectedMime: Mime) =>
      val asset = new ContentUriAssetForUpload(AssetId(), uri)
      asset.mimeType.await() shouldEqual expectedMime
    }
  }

  before {
    ZMessaging.context = Robolectric.application
    ShadowContentResolver2.reset()
    ShadowContentResolver2.registerProvider("my-provider", PdfProvider)
    val mimes = Robolectric.shadowOf(MimeTypeMap.getSingleton)
    mimes.clearMappings()
    mimes.addExtensionMimeTypMapping("txt", plainText.str)
    mimes.addExtensionMimeTypMapping("pdf", plainText.str)
  }

  val plainText = Mime("text/plain")
  val pdf = Mime("application/pdf")

  object PdfProvider extends TestContentProvider {
    override def getType(uri: Uri): String =
      Mime.extensionOf(uri.getLastPathSegment) match {
        case Some("pdf") => pdf.str
        case Some("txt") => null
        case _          => Mime.Default.str
      }

    override def query(uri: Uri, projection: Array[String], selection: String, selectionArgs: Array[String], sortOrder: String): Cursor = null
  }
}
