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
package com.waz.bitmap.gif

import java.io.{ByteArrayInputStream, File}
import java.nio.{ByteBuffer, ByteOrder}

import com.waz.utils.IoUtils
import org.scalatest._

@Ignore class DataSourceSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with RobolectricTests {

  val testData = {
    val data = new Array[Byte](1024)
    val buffer = ByteBuffer.wrap(data)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(10.toByte)
    buffer.put(11.toByte)
    buffer.put(Byte.MaxValue)
    buffer.putShort(12.toShort)
    buffer.putShort(Short.MaxValue)
    for (_ <- 1 to 100) buffer.put(0.toByte)
    for (_ <- 1 to 100) buffer.put(1.toByte)
    for (_ <- 1 to 100) buffer.put(2.toByte)
    for (_ <- 1 to 100) buffer.put(3.toByte)
    data
  }

  val testFile = {
    val file = File.createTempFile("test", "dat")
    IoUtils.copy(new ByteArrayInputStream(testData), file)
    file
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    testFile.delete()
  }

  def readBytes(source: DataSource) = {
    source.read() shouldEqual 10
    source.read() shouldEqual 11
    source.read() shouldEqual (Byte.MaxValue & 0xff)
  }

  def readShort(source: DataSource) = {
    source.skipBytes(3)
    source.readShort() shouldEqual 12
    source.readShort() shouldEqual (Short.MaxValue & 0xffff)
  }

  feature("ByteArrayDataSource") {

    def source = new ByteArrayDataSource(testData)

    scenario("Read bytes") {
      readBytes(source)
    }

    scenario("Read short") {
      readShort(source)
    }
  }

  feature("FileDataSource") {

    def source = new FileDataSource(testFile)

    scenario("Read bytes") {
      readBytes(source)
    }

    scenario("Read short") {
      readShort(source)
    }
  }
}
