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

import android.database.MatrixCursor
import com.waz.Generators._
import com.waz.RobolectricUtils
import com.waz.api.Message
import com.waz.content.MessagesCursor.Entry
import com.waz.model._
import com.waz.service.messages.MessageAndLikes
import com.waz.testutils.MockZMessaging
import com.waz.threading.SerialDispatchQueue
import org.scalacheck.Gen._
import org.scalacheck.{Arbitrary, _}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.PropertyChecks
import org.scalatest.time.{Millis, Seconds, Span}
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

@Ignore class MessagesCursorSpec extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils with ScalaFutures with PropertyChecks { test =>
  implicit val defaultPatience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  implicit val entryGen : Gen[Entry] = resultOf(Entry(_: MessageId, _: Instant))
  implicit val arbEntry = Arbitrary(entryGen)

  import MessagesCursor._
  implicit lazy val cursorDispatcher = new SerialDispatchQueue()

  def entry(i: Int) = new Entry(MessageId(i.toString), Instant.ofEpochMilli(1000 + i))

  lazy val convId = ConvId()
  lazy val items = Seq.tabulate(2000) { i => entry(i * 10) }

  lazy val zms = new MockZMessaging()

  lazy val msgLoader = new MessageAndLikesStorageImpl(UserId(), zms.messagesStorage, zms.reactionsStorage) {
    override def apply(ids: Seq[MessageId]): Future[Seq[MessageAndLikes]] = withLikes(ids map { id =>
      MessageData(id, convId, Message.Type.TEXT, UserId(id.str), Seq(MessageContent(Message.Part.Type.TEXT, id.str)))
    })
    override def withLikes(msgs: Seq[MessageData]): Future[Seq[MessageAndLikes]] = Future.successful(msgs.map { m => MessageAndLikes(m, IndexedSeq.empty, likedBySelf = false)})
  }

  def window(index: Int) = {
    val from = math.max(0, index - WindowSize / 2)
    val until = math.min(items.size, from + WindowSize)
    new IndexWindow(from, items.slice(from, until).toIndexedSeq)
  }

  def createCursor(items: Seq[Entry]) = {
    val c = new MatrixCursor(Array("id", "time"), items.size)
    items foreach { e =>
      c.addRow(Array[AnyRef](e.id.str, new java.lang.Long(e.time.toEpochMilli)))
    }
    c
  }

  feature(s"Index entry") {

    scenario("Ordering") {

      forAll { (e1: Entry, e2: Entry) =>
        if (e1 < e2) {
          e1 should not equal e2
          (e2 < e1) shouldEqual false

          Entry.Order.compare(e1, e2) should be < 0
          Entry.Order.compare(e2, e1) should be > 0
        } else if (e1 == e2) Entry.Order.compare(e1, e2) shouldEqual 0
        else {
          Entry.Order.compare(e1, e2) should be > 0
          Entry.Order.compare(e2, e1) should be < 0
        }
      }

    }
  }

  feature(s"IndexWindow") {

    scenario("compute index") {
      val window = new IndexWindow(100, IndexedSeq.tabulate(100)(i => Entry(MessageId(s"$i"), Instant.ofEpochMilli(1000 + i))))
      window.msgs.zipWithIndex foreach { case (e, i) =>
        withClue(s"$e msgs: ${window.msgs.take(5)}") {
          window.indexOf(e.time) shouldEqual 100 + i
        }
      }

      window.indexOf(Instant.ofEpochMilli(100)) shouldEqual -1
      window.indexOf(Instant.ofEpochMilli(10000)) shouldEqual -1
    }
  }

  feature("WindowLoader") {

    def createLoader(items: Seq[Entry]) = new WindowLoader(createCursor(items))

    def load(loader: WindowLoader, idx: Int) = loader(idx).futureValue

    scenario("try loading from empty cursor") {
      val loader = createLoader(Seq.empty)

      load(loader, 0) shouldEqual new IndexWindow(0, IndexedSeq.empty)
      load(loader, 10) shouldEqual new IndexWindow(0, IndexedSeq.empty)
    }

    scenario("load window for arbitrary index from start") {
      Seq(0, 500, 1000, 250, 1950, 100) foreach { idx =>
        load(createLoader(items), idx) shouldEqual window(idx)
      }
    }

    scenario("move to arbitrary index") {
      val loader = createLoader(items)
      Seq(0, 500, 1000, 250, 1950, 100, 2050) foreach { idx =>
        load(loader, idx) shouldEqual window(idx)
      }
    }

    scenario("reload window in background") {
      val loader = createLoader(items)
      load(loader, 100) shouldEqual window(100)
      load(loader, 200) shouldEqual window(100)
      awaitUi(10.millis)
      load(loader, 210) shouldEqual window(200)
      load(loader, 290) shouldEqual window(200)
      awaitUi(10.millis)
      load(loader, 300) shouldEqual window(290)
      load(loader, 200) shouldEqual window(290)
      awaitUi(10.millis)
      load(loader, 210) shouldEqual window(200)
    }
  }

  feature("MessagesCursor.apply") {

    scenario("load all messages sequentially") {
      val cursor = new MessagesCursor(createCursor(items), 0, Instant.EPOCH, msgLoader, null)

      items.zipWithIndex foreach { case (e, i) =>
        cursor(i).message.id shouldEqual e.id
      }
    }

    scenario("get random items from cursor") {
      val cursor = new MessagesCursor(createCursor(items), 0, Instant.EPOCH, msgLoader, null)

      forAll(Gen.choose(0, items.size - 1)) { index: Int =>
        cursor.apply(index).message.id shouldEqual items(index).id
      }
    }
  }

  feature("MessagesCursor.indexOf") {

    scenario("get index of entries") {
      val cursor = new MessagesCursor(createCursor(items), 0, Instant.EPOCH, msgLoader, null)

      items.zipWithIndex foreach { case (e, i) =>
        cursor.indexOf(e.time) shouldEqual i
      }
    }

    scenario("get index of random entry") {
      val cursor = new MessagesCursor(createCursor(items), 0, Instant.EPOCH, msgLoader, null)
      cursor(0)

      forAll(Gen.choose(0, items.size - 1)) { index: Int =>
        cursor.indexOf(items(index).time) shouldEqual index
      }
    }
  }
}
