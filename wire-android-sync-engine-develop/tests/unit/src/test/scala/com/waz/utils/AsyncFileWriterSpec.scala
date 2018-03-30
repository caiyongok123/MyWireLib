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
package com.waz.utils

import java.io.{File, FileInputStream, IOException}
import java.io.File.createTempFile
import java.nio.ByteBuffer

import com.waz.testutils.Matchers._
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

import scala.concurrent.duration._
import scala.util.Random.nextBytes

@Ignore class AsyncFileWriterSpec extends FeatureSpec with Matchers with RobolectricTests {
  lazy val testData = returning(Array.ofDim[Byte](10 << 20))(nextBytes)

  scenario("basic use") {
    val output = returning(createTempFile("tmp", ".data"))(_.deleteOnExit())
    val writer = new AsyncFileWriter(output)

    testData.sliding(1000, 1000).map(ByteBuffer.wrap).foreach(writer.enqueue)
    writer.finish()
    writer.completion.await()

    val read = IoUtils.toByteArray(new FileInputStream(output))

    read shouldEqual testData
  }

  scenario("unable to create") {
    val writer = new AsyncFileWriter(new File("/some/nonexistent/place"))

    an[IOException] shouldBe thrownBy(testData.sliding(1000, 1000).map(ByteBuffer.wrap).foreach(writer.enqueue))
    writer.finish()
    an[IOException] shouldBe thrownBy(writer.completion.await())
  }

  scenario("enqueue after finish") {
    val output = returning(createTempFile("tmp", ".data"))(_.deleteOnExit())
    val writer = new AsyncFileWriter(output)

    val data = testData.sliding(1000, 1000).map(ByteBuffer.wrap).toVector
    data.take(10).foreach(writer.enqueue)
    writer.finish()
    an[IllegalStateException] shouldBe thrownBy(writer.enqueue(data.head))
    writer.completion.await()
    idle(1.second)
    an[IllegalStateException] shouldBe thrownBy(writer.enqueue(data.head))
  }

  scenario("error while writing") {
    val output = returning(createTempFile("tmp", ".data"))(_.deleteOnExit())
    val writer = new AsyncFileWriter(output)

    val data = testData.sliding(100000, 100000).map(ByteBuffer.wrap).toVector
    data.take(100).foreach(writer.enqueue)
    writer.enqueue(null)
    writer.enqueue(data.head) // might actually throw the exception already if IO is *really* fast
    idle(5.seconds)
    a[NullPointerException] shouldBe thrownBy(writer.enqueue(data.head)) // might actually not throw anything if IO is *really* slow
    writer.finish()
    a[NullPointerException] shouldBe thrownBy(writer.completion.await())
  }
}
