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

import com.waz.db.ZMessagingDB
import com.waz.model.Liking.Action._
import com.waz.model.Liking.LikingDao
import com.waz.model.{Liking, MessageId, UserId}
import com.waz.testutils.DefaultPatienceConfig
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.wrappers.DBHelper
import org.robolectric.Robolectric
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.threeten.bp.Instant

import scala.collection.{SortedSet, breakOut}
import scala.util.Random.{nextInt, shuffle}

@Ignore class LikingStorageSpec extends FeatureSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with RobolectricTests with ScalaFutures with DefaultPatienceConfig {

  lazy val msgIds = (1 to 1000) map (_ => MessageId())
  lazy val userIds = (1 to 50) map (_ => UserId())
  lazy val participants: Map[MessageId, SortedSet[UserId]] = msgIds.map(_ -> shuffle(userIds).take(5).to[SortedSet])(breakOut)

  lazy val database = new Database() {
    override implicit val dispatcher: SerialDispatchQueue = new SerialDispatchQueue()
    override val dbHelper: DBHelper = new ZMessagingDB(Robolectric.application, "dbName")
  }

  lazy val storage = new ReactionsStorageImpl(Robolectric.application, database)

  override def beforeAll: Unit = {
    val likings = msgIds flatMap { msgId =>
      val users = participants(msgId)
      val start = sec(nextInt(Int.MaxValue))
      (0 to 2) flatMap { n =>
        users.toVector.zipWithIndex map { case (userId, m) =>
          val t = start.plusSeconds((n * users.size) + m)
          Liking(msgId, userId, t, Liking.Action.decode(1 - (n % 2)))
        }
      }
    }
    println(s"inserting ${likings.size} random likings")
    database { LikingDao.insertOrReplace(likings)(_) }
  }

  feature("Getting the likings for a single message") {
    scenario("One user") {
      val msgId = MessageId()
      val likings = Seq(
        Liking(msgId, userIds(0), sec(2), Like),
        Liking(msgId, userIds(0), sec(1), Unlike),
        Liking(msgId, userIds(0), sec(0), Unlike))

      storage.insertAll(likings).futureValue

      storage.get((msgId, userIds(0))).futureValue shouldEqual Some(Liking(msgId, userIds(0), sec(2), Like))
      storage.getLikes(msgId).futureValue shouldEqual Likes(msgId, Map(userIds(0) -> sec(2)))
    }

    scenario("Several users") {
      val msgId = MessageId()
      val likings = Seq(
        Liking(msgId, userIds(0), sec(2), Like),
        Liking(msgId, userIds(0), sec(1), Unlike),
        Liking(msgId, userIds(0), sec(0), Like),
        Liking(msgId, userIds(0), sec(3), Unlike),
        Liking(msgId, userIds(1), sec(4), Unlike),
        Liking(msgId, userIds(1), sec(0), Like),
        Liking(msgId, userIds(1), sec(1), Unlike),
        Liking(msgId, userIds(1), sec(3), Like),
        Liking(msgId, userIds(2), sec(2), Like),
        Liking(msgId, userIds(3), sec(3), Like),
        Liking(msgId, userIds(3), sec(4), Unlike))

      storage.insertAll(likings).futureValue

      storage.getLikes(msgId).futureValue shouldEqual Likes(msgId, Map(userIds(2) -> sec(2)))
    }
  }

  feature("Getting the likings for several messages") {
    scenario("One user") {
      val msgIds = (1 to 3).map(_ => MessageId())
      val likings = Seq(
        Liking(msgIds(0), userIds(0), sec(2), Like),
        Liking(msgIds(0), userIds(0), sec(1), Unlike),
        Liking(msgIds(0), userIds(0), sec(0), Like),
        Liking(msgIds(1), userIds(0), sec(1), Like),
        Liking(msgIds(2), userIds(0), sec(1), Like),
        Liking(msgIds(2), userIds(0), sec(2), Unlike))

      storage.insertAll(likings).futureValue

      storage.loadAll(msgIds).futureValue should contain only (
        Likes(msgIds(0), Map(userIds(0) -> sec(2))),
        Likes(msgIds(1), Map(userIds(0) -> sec(1))),
        Likes(msgIds(2), Map.empty)
      )
    }

    scenario("Several users") {
      val msgIds = (1 to 3).map(_ => MessageId())
      val usersForMsg = (0 to 2).map(n => msgIds(n) -> userIds.drop(n * 4)).toMap
      def likingsFor(msg: MessageId) = Seq(
        Liking(msg, usersForMsg(msg)(0), sec(2), Like),
        Liking(msg, usersForMsg(msg)(0), sec(1), Unlike),
        Liking(msg, usersForMsg(msg)(0), sec(0), Like),
        Liking(msg, usersForMsg(msg)(1), sec(4), Unlike),
        Liking(msg, usersForMsg(msg)(1), sec(0), Like),
        Liking(msg, usersForMsg(msg)(1), sec(1), Unlike),
        Liking(msg, usersForMsg(msg)(1), sec(3), Like),
        Liking(msg, usersForMsg(msg)(2), sec(2), Like),
        Liking(msg, usersForMsg(msg)(3), sec(3), Like),
        Liking(msg, usersForMsg(msg)(3), sec(4), Unlike))

      val likings = msgIds flatMap likingsFor

      storage.insertAll(likings).futureValue

      storage.loadAll(msgIds).futureValue should contain only (
        Likes(msgIds(0), Map(
          usersForMsg(msgIds(0))(0) -> sec(2),
          usersForMsg(msgIds(0))(2) -> sec(2))),
        Likes(msgIds(1), Map(
          usersForMsg(msgIds(1))(0) -> sec(2),
          usersForMsg(msgIds(1))(2) -> sec(2))),
        Likes(msgIds(2), Map(
          usersForMsg(msgIds(2))(0) -> sec(2),
          usersForMsg(msgIds(2))(2) -> sec(2)))
      )
    }
  }

  def sec(n: Int) = Instant.ofEpochSecond(n)
}
