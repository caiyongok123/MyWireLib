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

import org.scalatest.{Ignore, BeforeAndAfter, FeatureSpec, Matchers, RobolectricTests}
import com.waz.db.ZGlobalDB
import org.robolectric.Robolectric
import java.lang.System.currentTimeMillis

import scala.concurrent.duration._
import CacheEntryData.CacheEntryDao
import com.waz.model.CacheKey
import com.waz.testutils._
import com.waz.utils.wrappers.DB

@Ignore class CacheEntryDaoSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {

  import CacheEntryDao._
  var dbHelper: ZGlobalDB = _

  implicit def db: DB = dbHelper.getWritableDatabase

  val timeout = 1.hour.toMillis

  def mkEntry(name: String) = CacheEntryData(CacheKey(name), data = Some("Bar".getBytes), path = None, timeout = timeout)
  def mkExpiredEntry(name: String) = mkEntry(name).copy(lastUsed = currentTimeMillis() - 90.minutes.toMillis, timeout = timeout)

  before {
    dbHelper = new ZGlobalDB(Robolectric.application)
  }

  after {
    Robolectric.application.getDatabasePath(dbHelper.getDatabaseName).delete()
  }

  feature("CRUD") {
    scenario("insert cacheEntry with data and load it") {
      val entry = mkEntry("foo")
      insertOrReplace(entry)
      getByKey(CacheKey("foo")) shouldEqual Some(entry)
    }

    scenario("insert cacheEntry with stream and load it") {
      val entry = CacheEntryData(CacheKey("foo"), path = None)
      insertOrReplace(entry)
      getByKey(CacheKey("foo")) shouldEqual Some(entry)
    }

    scenario("insert cacheEntry and find it by key") {
      val entry = mkEntry("foo")
      insertOrReplace(entry)
      getByKey(entry.key) shouldEqual Some(entry)
    }

    scenario("delete by id") {
      val entry = mkEntry("foo")
      insertOrReplace(entry)

      deleteByKey(CacheKey("key"))
      getByKey(CacheKey("key")) should be(None)
    }

    scenario("delete by key") {
      val entry = mkEntry("foo")
      val entry1 = mkEntry("foo1")

      insertOrReplace(entry)
      insertOrReplace(entry1)

      deleteByKey(entry.key)
      findAll should contain theSameElementsAs List(entry1)
    }

    scenario("insert cacheEntries and find all") {
      val entries = List("foo", "bar", "woo", "hoo") map mkEntry
      entries foreach insertOrReplace

      findAll should contain theSameElementsAs entries
    }

    scenario("insert cacheEntries and find all expired") {
      val entries = List("aoo", "bar", "coo", "doo") map mkEntry
      val expiredEntries = List("eoo", "foo", "goo", "hoo") map mkExpiredEntry

      entries ++ expiredEntries foreach insertOrReplace

      findAllExpired(currentTimeMillis()) should contain theSameElementsAs expiredEntries
    }

    scenario("insert cacheEntries and delete all Expired") {
      val entries = List("aoo", "bar", "coo", "doo") map mkEntry
      val expiredEntries = List("eoo", "foo", "goo", "hoo") map mkExpiredEntry

      entries ++ expiredEntries foreach insertOrReplace

      deleteExpired(currentTimeMillis())
      findAll should contain theSameElementsAs entries
    }
  }
}
