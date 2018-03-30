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

import java.util
import com.waz.utils.returning

trait CoreList[T] extends UiObservable with com.waz.api.CoreList[T] {
  override def iterator(): util.Iterator[T] = new util.Iterator[T] {
    var pos = 0

    override def next(): T =
      returning(get(pos)) { _ => pos += 1 }

    override def remove(): Unit = throw new UnsupportedOperationException("Can not remove from CoreList")

    override def hasNext: Boolean = pos < size()
  }
}
