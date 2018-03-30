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
package com.waz.sync

import com.waz.RobolectricUtils
import com.waz.api.impl.ErrorResponse
import com.waz.content.UserPreferences.LastSlowSyncTimeKey
import com.waz.model.UserData.UserDataDao
import com.waz.model._
import com.waz.sync.client.UserSearchClient.UserSearchEntry
import com.waz.sync.client._
import com.waz.sync.handler.UsersSyncHandler
import com.waz.testutils.Matchers._
import com.waz.testutils.{EmptySyncService, MockZMessaging}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.wrappers.DB
import com.waz.znet.ZNetClient.ErrorOrResponse
import org.robolectric.Robolectric
import org.scalatest._
import org.threeten.bp.Instant.now

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Try}

@Ignore class UserSearchSyncHandlerSpec extends FeatureSpec with Matchers with BeforeAndAfter with GivenWhenThen with RobolectricTests with RobolectricUtils with OptionValues { test =>
  private lazy implicit val dispatcher = Threading.Background

  def storage = zms.storage.db

  lazy val users = Seq(UserData("other user 1"), UserData("other user 2"), UserData("some name"),
    UserData("related user 1"),
    UserData("related user 2"),
    UserData("other related"),
    UserData("friend user 1"),
    UserData("friend user 2"),
    UserData("some other friend")
  )

  var userSyncRequests = List[UserId]()
  var response: Try[Either[ErrorResponse, Seq[UserSearchEntry]]] = Success(Right(List[UserSearchEntry]()))
  var delay = 0.seconds

  var zms: MockZMessaging = _
  def searchSync = zms.usersearchSync
  def sync = zms.sync

  implicit def db: DB = storage.dbHelper.getWritableDatabase

  def query = SearchQuery.Recommended("query")

  before {
    userSyncRequests = Nil

    zms = new MockZMessaging() {

      override lazy val sync = new EmptySyncService {
        override def syncSearchQuery(q: SearchQuery) = {
          searchSync.syncSearchQuery(q)
          super.syncSearchQuery(q)
        }
        override def syncUsers(ids: UserId*) = {
          ids foreach { id => userSyncRequests ::= id }
          super.syncUsers(ids: _*)
        }
      }

      override lazy val userSearchClient: UserSearchClient = new UserSearchClient(zNetClient) {
        override def getContacts(query: SearchQuery, limit: Int): ErrorOrResponse[Seq[UserSearchEntry]] = CancellableFuture.delay(delay) map { _ => response.get }
      }

      userPrefs.preference(LastSlowSyncTimeKey) := Some(System.currentTimeMillis())
      override lazy val usersSync: UsersSyncHandler = new UsersSyncHandler(
        assetSync, users, usersStorage, assets, usersClient, assetGenerator, otrSync
      ) {
        override def syncUsers(ids: UserId*) = {
          sync.syncUsers(ids: _*)
          CancellableFuture.successful(SyncResult.Success)
        }
      }
    }
  }

  after {
    Await.result(storage.close(), 10.seconds)
    Robolectric.application.getDatabasePath(storage.dbHelper.getDatabaseName).delete()
  }

  scenario("Create new users from successful search result") {
    Given("empty local db and working search client")

    val entries = Vector(
      UserSearchEntry(UserId(), "user 1", Some(1), Handle("user1")),
      UserSearchEntry(UserId(), "user 2", Some(1), Handle("user2"))
    )
    response = Success(Right(entries))

    When("executing sync for new query")
    val cache = zms.searchQueryCache.insert(SearchQueryCache(query, now, None)).await()
    sync.syncSearchQuery(query)

    Then("sync should create new users for received data")
    withDelay {
      UserDataDao.list.filter(_.id != zms.selfUserId).toSet map withoutDisplayName shouldEqual entries.map(UserData(_)).toSet
    }

    And("newly cached query should reference all these new users")
    withDelay {
      zms.searchQueryCache.get(query).await().flatMap(_.entries).value shouldEqual entries.map(_.id)
    }
  }

  scenario("Create new users from succsessful search result and preserve the order") {
    Given("empty local db and working search client")

    val entries = Vector(
      UserSearchEntry(UserId(), "user 1", Some(1), Handle("user1")),
      UserSearchEntry(UserId(), "user 2", Some(1), Handle("user2"))
    )
    response = Success(Right(entries))

    When("executing sync for new query")
    val cache = zms.searchQueryCache.insert(SearchQueryCache(query, now, None)).await()
    sync.syncSearchQuery(query)

    Then("cached query should reference all users in the same order as in the response")
    withDelay {
      zms.searchQueryCache.get(query).await().flatMap(_.entries).value shouldEqual entries.map(_.id)
    }
  }

  scenario("Update users data from search result") {
    val entries = Vector(
        UserSearchEntry(UserId(), "user 1", Some(1), Handle("user1")),
        UserSearchEntry(UserId(), "user 2", Some(1), Handle("user2"))
    )
    response = Success(Right(entries))

    Given("local db with some users")
    zms.usersStorage.insertAll(entries.map(UserData(_).copy(name = "test")))

    When("executing sync returning these users")
    val cache = zms.searchQueryCache.insert(SearchQueryCache(query, now, None)).await()
    sync.syncSearchQuery(query)

    Then("service updates existing users data")
    withDelay {
      zms.usersStorage.list().await().filter(_.id != zms.selfUserId).toSet map withoutDisplayName shouldEqual entries.map(UserData(_)).toSet
    }

    And("newly cached query should reference all users")
    zms.searchQueryCache.get(query).await().flatMap(_.entries).value shouldEqual entries.map(_.id)
  }

  scenario("Schedule sync for newly created users") {
    val entries = Vector(
      UserSearchEntry(UserId(), "user 1", Some(1), Handle("user1")),
      UserSearchEntry(UserId(), "user 2", Some(1), Handle("user2"))
    )
    response = Success(Right(entries))

    When("executing sync for new query")
    val cache = zms.searchQueryCache.insert(SearchQueryCache(query, now, None)).await()
    sync.syncSearchQuery(query)

    Then("service schedules sync for all added users")
    withDelay {
      userSyncRequests.toSet shouldEqual entries.map(_.id).toSet
    }
  }

  scenario("Schedule sync for old users contained in query results") {
    val entries = Vector(
      UserSearchEntry(UserId(), "user 1", Some(1), Handle("user1")),
      UserSearchEntry(UserId(), "user 2", Some(1), Handle("user2")),
      UserSearchEntry(UserId(), "user 3", Some(1), Handle("user3"))
    )
    response = Success(Right(entries))

    Given("two old users in db")
    UserDataDao.insertOrReplace(entries.take(2).map(UserData(_).copy(name = "", syncTimestamp = 1)))

    And("one fresh user")
    UserDataDao.insertOrReplace(entries.drop(2).map(UserData(_).copy(name = "", syncTimestamp = System.currentTimeMillis())))

    When("executing sync returning results for this existing users")
    val cache = zms.searchQueryCache.insert(SearchQueryCache(query, now, None)).await()
    sync.syncSearchQuery(query)

    Then("service schedules sync for old users")
    withDelay {
      userSyncRequests.toSet shouldEqual entries.take(2).map(_.id).toSet
    }
  }

  def withoutDisplayName(data: UserData): UserData = data.copy(displayName = "")
}
