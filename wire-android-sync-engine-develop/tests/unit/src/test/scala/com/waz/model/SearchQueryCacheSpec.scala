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
package com.waz.model

import com.waz.db.ZMessagingDB
import com.waz.model.SearchQuery.{Recommended, TopPeople}
import com.waz.model.SearchQueryCache.SearchQueryCacheDao
import com.waz.utils._
import com.waz.utils.wrappers.DB
import org.robolectric.Robolectric
import org.scalatest._
import org.threeten.bp.Instant.now

import scala.concurrent.duration._

@Ignore class SearchQueryCacheSpec extends FeatureSpec with GivenWhenThen with BeforeAndAfter with Matchers with RobolectricTests {

  lazy val dbHelper = new ZMessagingDB(Robolectric.application, "dbName")
  implicit def db: DB = dbHelper.getWritableDatabase

  after {
    dbHelper.close()
  }

  feature("SearchQueryCache") {
    scenario("add query to cache and read it") {
      val entry = SearchQueryCacheDao.insertOrReplace(SearchQueryCache(Recommended("meep"), now, Some(Vector(UserId("a")))))
      SearchQueryCacheDao.getById(Recommended("meep")) shouldBe Some(entry)
    }

    scenario("multiple queries in cache get query") {
      val entry1 = SearchQueryCacheDao.insertOrReplace(SearchQueryCache(Recommended("meep"), now, Some(Vector(UserId("a")))))
      SearchQueryCacheDao.getById(Recommended("meep")) shouldBe Some(entry1)

      val entry2 = SearchQueryCacheDao.insertOrReplace(SearchQueryCache(Recommended("meep"), now, Some(Vector(UserId("b")))))
      SearchQueryCacheDao.getById(Recommended("meep")) shouldBe Some(entry2)

      val entry3 = SearchQueryCacheDao.insertOrReplace(SearchQueryCache(TopPeople, now, Some(Vector(UserId("c")))))
      SearchQueryCacheDao.getById(TopPeople) shouldBe Some(entry3)
    }

    scenario("delete expired") {
      val entry1 = SearchQueryCacheDao.insertOrReplace(SearchQueryCache(Recommended("meep"), now - 1.day, Some(Vector(UserId("a")))))
      val entry2 = SearchQueryCacheDao.insertOrReplace(SearchQueryCache(Recommended("moop"), now - 8.days, Some(Vector(UserId("b")))))
      val entry3 = SearchQueryCacheDao.insertOrReplace(SearchQueryCache(TopPeople, now, Some(Vector(UserId("c")))))

      SearchQueryCacheDao.deleteBefore(now - 7.days)
      SearchQueryCacheDao.list.sortBy(_.timestamp) shouldEqual Vector(entry1, entry3)
    }
  }
}
