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
package com.waz.service.assets

import java.nio.ByteBuffer
import java.nio.ByteOrder.nativeOrder

import com.waz.utils.returning
import libcore.io.SizeOf
import org.scalatest.{FeatureSpec, Ignore, Matchers}

import scala.math._

@Ignore class AudioLevelsSpec extends FeatureSpec with Matchers {
  import AudioLevels._

  feature("dBFS conversion") {
    scenario("for peak measurements") {
      dbfsSquare(intAsDouble(sine.take(1000).max)) shouldEqual (0d +- 0.1d)
      dbfsSine(intAsDouble(sine.take(1000).max)) shouldEqual (3d +- 0.1d)

      dbfsSquare(intAsDouble(square.take(1000).max)) shouldEqual (0d +- 0.1d)
      dbfsSine(intAsDouble(square.take(1000).max)) shouldEqual (3d +- 0.1d)
    }

    scenario("for RMS measurements") {
      dbfsSquare(rms(bufferOf(4096, sine), 2048).head) shouldEqual (-3d +- 0.1d)
      dbfsSine(rms(bufferOf(4096, sine), 2048).head) shouldEqual (0d +- 0.1d)

      dbfsSquare(rms(bufferOf(4096, square), 2048).head) shouldEqual (0d +- 0.1d)
      dbfsSine(rms(bufferOf(4096, square), 2048).head) shouldEqual (3d +- 0.1d)
    }

    scenario("half amplitude") {
      dbfsSine(rms(bufferOf(4096, sine.map(n => (n / 2).toShort)), 2048).head) shouldEqual (-6d +- 0.1d)
    }
  }

  feature("Intuitive loudness scale (conversion from dBFS)") {
    scenario("max loudness") {
      loudness(0d) shouldEqual 1f
    }

    scenario("min loudness") {
      loudness(Double.NegativeInfinity) shouldEqual 0
    }

    scenario("loudness is halved every -10dBFS") {
      loudness(-10d) shouldEqual 0.5d
      loudness(-20d) shouldEqual 0.25d
      loudness(-30d) shouldEqual 0.125d
      loudness(-40d) shouldEqual 0.0625d
    }

    scenario("half amplitude ~ two thirds loudness") {
      loudness(dbfsSine(rms(bufferOf(4096, sine.map(n => (n / 2).toShort)), 2048).head)) shouldEqual (0.66d +- 0.01d)
    }

    scenario("one tenth amplitude ~ quarter loudness") {
      loudness(dbfsSine(rms(bufferOf(4096, sine.map(n => (n / 10).toShort)), 2048).head)) shouldEqual (0.25d +- 0.01d)
    }
  }

  feature("RMS calculation") {
    scenario("Estimated bucket size equals buffer size") {
      rms(bufferOf(20, sine), 20) should have size 1
    }

    scenario("Estimated bucket size larger than buffer size") {
      println(loudness(dbfsSine(0.5d)))
      rms(bufferOf(20, sine), 30) should have size 1
    }

    scenario("Estimated bucket size smaller than buffer size") {
      rms(bufferOf(10, Stream from 0 map (_.toShort)), 3) should have size 4
      rms(bufferOf(10, Stream from 0 map (_.toShort)), 3) shouldEqual (
        rms(bufferOf(2, shorts(0, 1)), 2) ++
        rms(bufferOf(3, shorts(2, 3, 4)), 3) ++
        rms(bufferOf(2, shorts(5, 6)), 2) ++
        rms(bufferOf(3, shorts(7, 8, 9)), 3))
    }
  }

  def bufferOf(length: Int, s: Stream[Short]): ByteBuffer = returning(ByteBuffer allocate (length * SizeOf.SHORT) order nativeOrder) { b =>
    val bs = b.asShortBuffer
    s.takeWhile(_ => bs.hasRemaining).foreach(bs.put)
    bs.flip
  }.asReadOnlyBuffer

  def sine = Stream from 1 map (n => uintPCM(sin(toRadians(n))))
  def square = Stream from 1 map (n => if ((n / 180) % 2 == 0) Short.MaxValue else Short.MinValue)

  def uintPCM(n: Double): Short = (n * (if (n < 0d) 32768d else 32767d)).toShort

  def shorts(ss: Short*) = ss.toStream
}
