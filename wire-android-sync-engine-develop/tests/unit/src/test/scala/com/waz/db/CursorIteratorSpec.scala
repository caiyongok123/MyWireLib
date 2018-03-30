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

import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}
import android.database.MatrixCursor
import com.waz.model.Uid
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import com.waz.db.TestItem.TestItemDao

@Ignore class CursorIteratorSpec extends FeatureSpec with Matchers with GeneratorDrivenPropertyChecks with RobolectricTests {

  def newCursor = new MatrixCursor(Array(TestItemDao.Id.name, TestItemDao.Str.name, TestItemDao.Int.name))
  
  scenario("empty cursor") {
    val iter = new CursorIterator[TestItem](newCursor)

    iter.hasNext shouldEqual false
  }

  scenario("single element cursor") {
    val id = Uid()
    val c = newCursor
    c.addRow(Array[AnyRef](id, "item1", Integer.valueOf(1)))

    new CursorIterator[TestItem](c).toList should be(List(TestItem(id, "item1", 1)))
  }

  scenario("list random data") {
    forAll { items: List[TestItem] =>
      val cursor = newCursor
      items foreach { i => cursor.addRow(Array[AnyRef](i.id, i.str, Integer.valueOf(i.int))) }

      new CursorIterator[TestItem](cursor).toList should be(items)
    }
  }
}
