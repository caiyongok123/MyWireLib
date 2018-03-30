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
package com.waz.content

import java.io.{ByteArrayInputStream, FileInputStream}

import android.provider.OpenableColumns
import com.waz.RobolectricUtils
import com.waz.utils.wrappers.URI
import com.waz.content.WireContentProvider.CacheUri
import com.waz.model.{CacheKey, Mime}
import com.waz.service.ZMessaging
import com.waz.testutils.{DefaultPatienceConfig, MockGlobalModule}
import com.waz.utils.{IoUtils, returning}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest._

import scala.util.Random

@Ignore class WireContentProviderSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils with ScalaFutures with DefaultPatienceConfig { test =>

  lazy val global = new MockGlobalModule()

  lazy val cache = global.cache

  lazy val provider = {
    ZMessaging.currentGlobal = global
    new WireContentProvider
  }

  scenario("init") {
    val dir = cache.intCacheDir
    info(s"internal dir: " + dir)
    dir.mkdirs()
  }

  feature("Cache uri") {

    val assetData = returning(Array.ofDim[Byte](10000))(Random.nextBytes)

    lazy val entry = cache.addStream(CacheKey("key"), new ByteArrayInputStream(assetData), Mime("text/txt"), Some("file.txt"), Some(cache.intCacheDir)).futureValue
    lazy val uri = CacheUri(entry.data, context)

    scenario("Query cache entry metadata") {
      entry.data.encKey shouldBe empty
      val c = provider.query(URI.unwrap(uri), Array(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null, null)
      c.getCount shouldEqual 1
      c.moveToFirst()
      c.getString(0) shouldEqual "file.txt"
      c.getInt(1) shouldEqual 10000
    }

    scenario("Query without specified project") {
      val c = provider.query(URI.unwrap(uri), null, null, null, null, null)
      c.getCount shouldEqual 1
      c.moveToFirst()
      c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME)) shouldEqual "file.txt"
      c.getInt(c.getColumnIndex(OpenableColumns.SIZE)) shouldEqual 10000
    }

    scenario("Load file data") {
      IoUtils.toByteArray(new FileInputStream(provider.openFile(URI.unwrap(uri), "r").getFileDescriptor)).toSeq shouldEqual assetData.toSeq
    }

    scenario("Load from encrypted cache entry") {
      val entry = cache.addStream(CacheKey("key1"), new ByteArrayInputStream(assetData), Mime("text/txt"), Some("file.txt")).futureValue
      entry.data.encKey should be('defined)
      val uri = CacheUri(entry.data, context)
      val c = provider.query(URI.unwrap(uri), Array(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null, null)
      c.getCount shouldEqual 1
      c.moveToFirst()
      c.getString(0) shouldEqual "file.txt"
      c.getInt(1) shouldEqual 10000

      IoUtils.toByteArray(new FileInputStream(provider.openFile(URI.unwrap(uri), "r").getFileDescriptor)).toSeq shouldEqual assetData.toSeq
    }
  }
}
