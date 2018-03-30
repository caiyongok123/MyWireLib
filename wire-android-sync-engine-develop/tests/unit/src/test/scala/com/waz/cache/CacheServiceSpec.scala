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
package com.waz.cache

import java.io.FileInputStream

import com.waz.cache.CacheEntryData.CacheEntryDao
import com.waz.content.GlobalDatabase
import com.waz.model.{CacheKey, Mime}
import com.waz.testutils.DefaultPatienceConfig
import com.waz.testutils.Matchers._
import com.waz.threading.Threading.Implicits.Background
import com.waz.utils.IoUtils
import com.waz.utils.wrappers.DB
import org.robolectric.Robolectric
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Await
import scala.concurrent.duration._

@Ignore class CacheServiceSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with ScalaFutures with DefaultPatienceConfig { test =>

  implicit def db: DB = storage.dbHelper.getWritableDatabase

  lazy val cacheDir = Robolectric.application.getCacheDir

  lazy val storage = new GlobalDatabase(Robolectric.application)
  lazy val cacheStorage = CacheStorage(storage, Robolectric.application)
  lazy val service = CacheService(Robolectric.application, storage, cacheStorage, null)

  after {
    Thread.sleep(1000) // because some operations (deleting) are scheduled on background
    cacheStorage.list().map { es => cacheStorage.removeAll(es.map(_.key)) }.await()
  }

  feature("Create") {
    scenario("Create CacheEntry with data") {
      val entry = service.addData(CacheKey("foo"), "bar".getBytes).await()
      entry.data.key should be(CacheKey("foo"))
    }

    scenario("Create CacheEntry with inputStream") {
      val in = getClass.getResourceAsStream("/images/fixedit.jpg")

      val entry = service.addStream(CacheKey("foo"), in, Mime("image/jpeg"), Some("test.jpg"))
      val result = Await.result(entry, 10.seconds)

      result.data.key should be(CacheKey("foo"))
      result.data.mimeType shouldEqual Mime("image/jpeg")
      result.data.fileName shouldEqual Some("test.jpg")
      result.cacheFile.exists() shouldEqual true
    }

    scenario("Create empty CacheEntry") {
      val entry = service.createForFile(CacheKey("foo")).await()

      entry.data.key should be(CacheKey("foo"))
    }

    scenario("Create CacheEntry with data will overwrite") {
      val entry1 = service.addData(CacheKey("foo"), "bar".getBytes).await()
      val entry2 = service.addData(CacheKey("foo"), "bar".getBytes).await()

      entry2.data.fileId should not be entry1.data.fileId
      entry1.cacheFile.exists() shouldEqual false
    }

    scenario("Create CacheEntry with inputStream will overwrite") {
      val entry1 = service.addStream(CacheKey("foo"), getClass.getResourceAsStream("/images/fixedit.jpg"))
      val result1 = Await.result(entry1, 10.seconds)

      val entry2 = service.addStream(CacheKey("foo"), getClass.getResourceAsStream("/images/fixedit.jpg"))
      val result2 = Await.result(entry2, 10.seconds)

      Thread.sleep(250)

      result2.cacheFile should not be result1.cacheFile
      result1.cacheFile.exists() shouldEqual false
    }
  }

  feature("Read") {

    scenario("Create CacheEntry with data and read it") {
      val entry = service.addData(CacheKey("foo"), "bar".getBytes).await()

      assertEquals(service.getEntry(CacheKey("foo")).await().get, entry)
    }

    scenario("Create CacheEntry with inputStream and read it") {
      def in = getClass.getResourceAsStream("/images/fixedit.jpg")

      val entry = service.addStream(CacheKey("foo"), in).futureValue
      val entry1 = service.getEntry(CacheKey("foo")).futureValue.get
      assertEquals(entry1, entry)

      IoUtils.toByteArray(entry1.inputStream).mkString(",") shouldEqual IoUtils.toByteArray(in).mkString(",")
    }

    scenario("Saved file should be encrypted") {
      def in = getClass.getResourceAsStream("/images/fixedit.jpg")

      val entry = service.addStream(CacheKey("foo"), in).futureValue

      IoUtils.toByteArray(entry.inputStream).mkString(",") shouldEqual IoUtils.toByteArray(in).mkString(",")
      IoUtils.toByteArray(new FileInputStream(entry.cacheFile)).toSeq should not equal IoUtils.toByteArray(in).toSeq
    }

    scenario("Create empty CacheEntry and try to read it") { // cache should never return empty entries
      val entry = service.createForFile(CacheKey("foo"))

      Await.result(service.getEntry(CacheKey("foo")), 10.seconds) shouldEqual None
    }
  }

  def assertEquals(e1: CacheEntry, e2: CacheEntry): Unit = assertEquals(e1.data, e2.data)

  def assertEquals(e1: CacheEntryData, e2: CacheEntryData): Unit = {
    e1.fileId shouldEqual e2.fileId
    e1.key shouldEqual e2.key
    e1.timeout shouldEqual e2.timeout
    e1.data.map(_.toSeq) shouldEqual e2.data.map(_.toSeq)
  }

  feature("Delete") {
    scenario("Create CacheEntry with data and delete it") {
      val entry = service.addData(CacheKey("foo"), "bar".getBytes).await()

      Await.result(service.remove(entry), 1.second)
      Await.result(service.getEntry(CacheKey("foo")), 10.seconds) should be(None)
    }

    scenario("Create CacheEntry with file and delete it") {
      def in = getClass.getResourceAsStream("/images/fixedit.jpg")

      val entry = Await.result(service.addStream(CacheKey("foo"), in), 10.seconds)

      Await.result(service.remove(entry), 1.second)
      Await.result(service.getEntry(CacheKey("foo")), 10.seconds) should be(None)
    }
  }

  feature("Sync") {
    scenario("Delete all expired") {
      val Seq(foo, bar) = Seq(CacheKey("foo"), CacheKey("bar")) map (service.createForFile(_)(Expiration(0)).await())
      val Seq(goo, hoo) = Seq(CacheKey("goo"), CacheKey("hoo")) map (service.addData(_, "zeta".getBytes)(Expiration(0)).await())
      Seq(foo, bar) foreach { _.cacheFile.createNewFile() }

      Thread.sleep(1000)
      Seq(foo, bar) foreach { _.cacheFile should exist }

      Await.result(service.deleteExpired(), 10.seconds)
      Thread.sleep(1000)

      Seq(foo, bar, goo, hoo) foreach { _.cacheFile should not(exist) }

      CacheEntryDao.findAll should be(Seq.empty)
    }
  }
}
