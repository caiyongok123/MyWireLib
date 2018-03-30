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
package com.waz.db

import java.util.concurrent.{CyclicBarrier, TimeUnit}

import android.database.sqlite.SQLiteGlobal
import com.waz.RobolectricUtils
import com.waz.content.Database
import com.waz.db.CompositeKeyTestItem.CompositeKeyTestItemDao
import com.waz.db.TestItem.TestItemDao
import com.waz.testutils.Implicits._
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.wrappers.{DB, DBHelper}
import org.robolectric.Robolectric
import org.scalatest._
import org.scalatest.prop.GeneratorDrivenPropertyChecks

import scala.util.Random

@Ignore class DaoSpec extends FeatureSpec with Matchers with BeforeAndAfter with GeneratorDrivenPropertyChecks with RobolectricTests with RobolectricUtils { outer =>

  lazy val dbHelper: DBHelper = new DaoDB(Robolectric.application, "testdb", null, 1, Seq(TestItemDao, CompositeKeyTestItemDao), Seq.empty[Migration])

  implicit def database: DB = dbHelper.getWritableDatabase

  after {
    dbHelper.close()
  }

  implicit override val generatorDrivenConfig = PropertyCheckConfig(minSuccessful = 40, maxDiscarded = 200)

  feature("CRUD") {

    import TestItemDao._

    scenario("insert") {
      forAll { items: List[TestItem] =>
        insertOrReplace(items)
        items foreach { i => single(find(Id, i.id)) should be(Some(i)) }
      }
    }

    scenario("delete") {
      forAll { items: List[TestItem] =>
        insertOrReplace(items)

        for (_ <- 1 to items.length) {
          val i = items(Random.nextInt(items.length))
          delete(Id, i.id)
          single(find(Id, i.id)) shouldBe None
        }
      }
    }

    scenario("deleteAll") {
      forAll { items: List[TestItem] =>
        insertOrReplace(items)
        deleteAll

        list shouldBe empty
      }
    }

    scenario("deleteEvery") {
      forAll { items: List[TestItem] =>
        insertOrReplace(items)
        deleteEvery(items.map(_.id))

        list shouldBe empty
      }
    }

    scenario("list") {
      forAll { items: List[TestItem] =>
        deleteAll
        insertOrReplace(items)

        list.toSet shouldEqual items.toSet
      }
    }
  }

  feature("CRUD with composite key") {

    import CompositeKeyTestItemDao._

    scenario("insert") {
      forAll { items: List[CompositeKeyTestItem] =>
        insertOrReplace(items)
        items foreach { i => getById(i.id) should be(Some(i)) }
      }
    }

    scenario("delete") {
      forAll { items: List[CompositeKeyTestItem] =>
        insertOrReplace(items)

        for (_ <- 1 to items.length) {
          val i = items(Random.nextInt(items.length))
          delete(i.id)
          getById(i.id) shouldBe None
        }
      }
    }

    scenario("deleteAll") {
      forAll { items: List[CompositeKeyTestItem] =>
        insertOrReplace(items)
        deleteAll

        list shouldBe empty
      }
    }

    scenario("list") {
      forAll { items: List[CompositeKeyTestItem] =>
        deleteAll
        insertOrReplace(items)

        list.toSet shouldEqual items.toSet
      }
    }
  }

  feature("Shared read locks") {
    import TestItemDao._

    scenario("concurrent reads", Tag("MEEP")) {
      forAll { items: List[TestItem] => insertOrReplace(items) }

      lazy val dbase = new Database {
        override implicit val dispatcher: SerialDispatchQueue = new SerialDispatchQueue()
        override val dbHelper: DBHelper = outer.dbHelper
      }

      val n = SQLiteGlobal.getWALConnectionPoolSize
      info(s"number of concurrent reads: $n")
      val barrier = new CyclicBarrier(n + 1)

      n times {
        dbase.read { db =>
          list(db).toSet should not be empty
          barrier.await
        }
      }

      barrier.await(5, TimeUnit.SECONDS)
    }
  }
}
