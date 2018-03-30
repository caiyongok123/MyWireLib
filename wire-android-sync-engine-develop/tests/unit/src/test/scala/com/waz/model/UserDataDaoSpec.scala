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
import com.waz.model.UserData.{ConnectionStatus, UserDataDao}
import com.waz.utils.wrappers.DB
import org.robolectric.Robolectric
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest._

@Ignore class UserDataDaoSpec extends FeatureSpec with Matchers with BeforeAndAfter with GeneratorDrivenPropertyChecks with RobolectricTests {

  var dbHelper: ZMessagingDB = _

  lazy val users = Seq(
    UserData("other user 1"),
    UserData("other user 2"),
    UserData("some name"),
    UserData("related user 1").copy(relation = Relation.Third),
    UserData("related user 2").copy(relation = Relation.Second),
    UserData("other related").copy(relation = Relation.First),
    UserData("friend user 1").copy(connection = UserData.ConnectionStatus.Accepted),
    UserData("friend user 2").copy(connection = UserData.ConnectionStatus.Accepted),
    UserData("some other friend").copy(connection = UserData.ConnectionStatus.Accepted)
  )

  lazy val connectedUsers = Seq(
    UserData("connected user 1").copy(connection = UserData.ConnectionStatus.Accepted),
    UserData("connected user 2").copy(connection = UserData.ConnectionStatus.Accepted),
    UserData("connected user 3").copy(connection = UserData.ConnectionStatus.Accepted),
    UserData("connected user 4").copy(connection = UserData.ConnectionStatus.Accepted)
  )

  lazy val emailUsers = Seq(
    UserData("email user 1").copy(email = Some(EmailAddress("user1@zeta.com"))),
    UserData("email user 2").copy(email = Some(EmailAddress("user2@zeta.com"))),
    UserData("email user 3").copy(email = Some(EmailAddress("user3@zeta.com"))),
    UserData("email user 4").copy(email = Some(EmailAddress("user4@zeta.com")))
  )

  before {
    dbHelper = new ZMessagingDB(Robolectric.application, "dbName")
  }

  after {
    dbHelper.close()
    Robolectric.application.getDatabasePath("dbName").delete()
  }

  import UserDataDao.{findByConnectionStatus, get, insertOrReplace, list}
  implicit def db: DB = dbHelper.getWritableDatabase

  def search(str: String) = UserDataDao.recommendedPeople(str).acquire(_.map(_.id).to[Vector])

  feature("CRUD") {

    scenario("insert a user and load it") {
      val user = UserData("test name")

      insertOrReplace(Seq(user))
      get(user.id) should be(Some(user))
      list should be(List(user))
    }

    scenario("insert multiple users and load them one by one") {
      insertOrReplace(users)

      users foreach { u =>
        get(u.id) should be(Some(u))
      }

      get(UserId()) should be(None)
      list.toSet should be(users.toSet)
    }

    scenario("load users for ids") {
      insertOrReplace(users)

      val u1 = users.filter(_.name.contains("u"))

      UserDataDao.getAll(u1.map(_.id).toSet).toSet shouldEqual u1.toSet
    }
  }

  feature("Searching for recommended people") {

    scenario("search by name part") {
      insertOrReplace(users)

      search("friend").toSet shouldBe empty

      search("f").toSet shouldBe empty

      search("s").toSet shouldBe empty

      val relatedUsers = users.filter(_.name.contains("related")).map(_.id).toSet
      search("r").toSet shouldEqual relatedUsers
      search("re").toSet shouldEqual relatedUsers
      search("rel").toSet shouldEqual relatedUsers
      search("rela").toSet shouldEqual relatedUsers

      search("1").toSet shouldEqual users.find(_.name == "related user 1").map(_.id).toSet

      search("z").toSet shouldBe empty

      search("user").toSet shouldEqual users.filter(_.name.contains("related user")).map(_.id).toSet
    }

    scenario("search by whole name") {
      insertOrReplace(users)

      search("related user 1").toSet shouldEqual users.find(_.name == "related user 1").map(_.id).toSet
    }
  }

  feature("Finding") {
    scenario("find by connection status") {
      insertOrReplace(users ++ connectedUsers)

      findByConnectionStatus(Set(UserData.ConnectionStatus.Accepted)).acquire(_.to[Vector]) should be(users.drop(6) ++ connectedUsers)
      findByConnectionStatus(Set(UserData.ConnectionStatus.Unconnected)).acquire(_.to[Vector]) should be(users.take(6))
    }
  }

  scenario("Connection Status by code") {
    ConnectionStatus("ignored") should be(ConnectionStatus.Ignored)
    ConnectionStatus("unconnected") should be(ConnectionStatus.Unconnected)
    ConnectionStatus("sent") should be(ConnectionStatus.PendingFromUser)
    ConnectionStatus("pending") should be(ConnectionStatus.PendingFromOther)
    ConnectionStatus("blocked") should be(ConnectionStatus.Blocked)
    ConnectionStatus("accepted") should be(ConnectionStatus.Accepted)
    ConnectionStatus("self") should be(ConnectionStatus.Self)
  }
}
