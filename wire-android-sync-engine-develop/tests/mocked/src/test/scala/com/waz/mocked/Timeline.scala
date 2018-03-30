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
package com.waz.mocked

import java.util.Date

import com.waz.utils.compareAndSet
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import org.threeten.bp.Instant
import scala.annotation.tailrec
import scala.util.Random.nextInt

trait Timeline {
  def next(): Date
}

object Timeline {
  def sometimeInThePast = ArtificialTimeline(Instant.now minusSeconds 1000000L plusSeconds nextInt(10000))
}

case class ArtificialTimeline(start: Instant) extends Timeline {
  private val current = new AtomicReference(start)
  def next() = new Date(compareAndSet(current)(_ plusMillis 1).toEpochMilli)
}

object SystemTimeline extends Timeline {
  private val previous = new AtomicLong(System.currentTimeMillis)
  def next() = {
    @tailrec def cas: Long = {
      val p = previous.get
      val n = System.currentTimeMillis
      if (n > p && previous.compareAndSet(p, n)) n else cas
    }
    
    new Date(cas)
  }
}
