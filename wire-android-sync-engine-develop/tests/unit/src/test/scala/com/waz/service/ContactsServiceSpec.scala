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
package com.waz.service

import java.util.concurrent.atomic.AtomicInteger

import android.database.sqlite.SQLiteDatabase
import com.waz.RobolectricUtils
import com.waz.model.AddressBook.ContactHashes
import com.waz.model._
import com.waz.service.ContactsServiceImpl.{CurrentAddressBookVersion, MayNotYetCheckAgainException, zUserAndTimeOfLastCheck}
import com.waz.testutils.Matchers._
import com.waz.testutils.{EmptySyncService, MockZMessaging, prepareAddressBookEntries}
import com.waz.threading.CancellableFuture.delay
import com.waz.utils._
import org.scalatest._
import org.threeten.bp.Instant
import org.threeten.bp.Instant.{EPOCH, now}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.sequence
import scala.concurrent.duration._
import scala.math.abs
import scala.util.Random.nextDouble
import com.waz.ZLog.ImplicitTag._

@Ignore class ContactsServiceSpec extends FeatureSpec with Matchers with OptionValues with BeforeAndAfter with RobolectricTests with RobolectricUtils { test =>
  implicit val timeout: FiniteDuration = 2.seconds

  @volatile var uploadedAddressBook = Option.empty[AddressBook]

  lazy val zms = new MockZMessaging() {
    override lazy val sync = new EmptySyncService {
      override def postAddressBook(ab: AddressBook) = {
        uploadedAddressBook = Some(ab)
        super.postAddressBook(ab)
      }
    }
    override lazy val timeouts = new Timeouts {
      override val contacts = new Contacts {
        override val uploadCheckInterval = 10.seconds
      }
    }

  }
  
  lazy val service = zms.contacts

  implicit def db: SQLiteDatabase = zms.db.dbHelper.getWritableDatabase

  before {
    uploadedAddressBook = None
    setLastCheckTime(EPOCH)
  }

  scenario("hash contacts") {
    val contacts = Seq(
      "max.musterman@example.com",
      "john@example.com",
      "john.appleseed@example.com",
      "+123456789012",
      "+4915324568954"
    )

    contacts map sha2 shouldEqual Seq(
      "z5x1qzsgFZvG8iqvOklPoG8Mzr9SqPnLa1UJ2V1rr6I=",
      "hV+W6YPx+Oi+lEaStvcZ/VQymCbLYumAFe/uji4HHdQ=",
      "9lXmq9KuYNODDOAPmizuAUWDRKDBqcLrIBzHmbQrGBw=",
      "0jTPlMu+7kPPA/ilpG/ikUFK8Hs2xMFVRLHs53JQsgI=",
      "IHI4pX3lC+tkaaLOxbYpubZLA6127QE1R6oJDt5BQu0="
    )
  }

  scenario("normalize phone number") {
    zms.phoneNumbers.defaultRegion shouldEqual "US"

    zms.phoneNumbers.normalize(PhoneNumber("+4915324568954")) should eventually(be(Some(PhoneNumber("+4915324568954"))))
    zms.phoneNumbers.normalize(PhoneNumber("5324568954")) should eventually(be(Some(PhoneNumber("+15324568954"))))
  }

  feature("Normalize ab") {

    scenario("normalize empty ab") {
      val ab = AddressBook(Nil, Nil)
      ab.withoutDuplicates shouldEqual ab
    }

    scenario("remove duplicate card") {
      val self = Seq("hash1", "hash2")
      val ab = AddressBook(self, Seq(ContactHashes(ContactId("card1"), Set("hash1", "hash2")), ContactHashes(ContactId("card2"), Set("hash2", "hash1"))))
      ab.withoutDuplicates shouldEqual AddressBook(self, Seq(ContactHashes(ContactId("card1"), Set("hash1", "hash2"))))
    }

    scenario("remove duplicate cards") {
      val ab = AddressBook(Nil, Seq(
        ContactHashes(ContactId("card1"), Set("hash1", "hash2")),
        ContactHashes(ContactId("card2"), Set("hash3", "hash2")),
        ContactHashes(ContactId("card3"), Set("hash2", "hash1")),
        ContactHashes(ContactId("card4"), Set("hash2", "hash3")),
        ContactHashes(ContactId("card5"), Set("hash5"))
      ))
      val ab1 = ab.withoutDuplicates
      ab1.self shouldEqual Nil
      ab1.contacts.toSet shouldEqual Set(
        ContactHashes(ContactId("card1"), Set("hash1", "hash2")),
        ContactHashes(ContactId("card2"), Set("hash2", "hash3")),
        ContactHashes(ContactId("card5"), Set("hash5"))
      )
    }
  }

  feature("address book storage") {

    scenario("save and load address book") {
      val ab = AddressBook(Seq("self_hash1"), Seq(ContactHashes(ContactId("id1"), Set("id1_hash1", "id1_hash2")), ContactHashes(ContactId("id2"), Set("id2_hash1"))))
      Await.result(zms.db { AddressBook.save(ab)(_) }, 5.seconds)
      val ab1 = Await.result(zms.db { AddressBook.load(_) }, 1.second)

      ab1.contacts shouldEqual ab.contacts

      (ab - ab1).isEmpty shouldEqual true
    }

    scenario("diff same") {
      val ab = AddressBook(Seq("self_hash1"), Seq(ContactHashes(ContactId("id1"), Set("id1_hash1", "id1_hash2")), ContactHashes(ContactId("id2"), Set("id2_hash1"))))
      val diff = ab - ab
      diff.isEmpty shouldEqual true
    }
  }

  feature("load address book") {

    scenario("prepare contacts") {
      prepareAddressBookEntries(
        Seq("1" -> "meep@wearezeta.com", "2" -> "other@wearezeta.com", "2" -> "third@wearezeta.com"),
        Seq("1" -> "+493045564", "3" -> "+49303252468543"))
    }

    scenario("load contacts when sharing preference is not set, defaults to true") {
      val ab = service.addressBook.await()
      ab.self should not be empty
      ab.contacts.map(_.id).toSet shouldEqual oneTwoThree
    }

    scenario("return empty address book when sharing is disabled") {
      (service.shareContactsPref := false).await()
      val ab = Await.result(service.addressBook, 5.seconds)
      ab.self should not be empty
      ab.contacts should be(empty)
    }

    scenario("load contacts when sharing is enabled") {
      (service.shareContactsPref := true).await()
      val ab = service.addressBook.await()
      ab.self should not be empty
      ab.contacts.map(_.id).toSet shouldEqual oneTwoThree
    }
  }

  feature("request upload") {

    scenario("request post on start if wasn't yet uploaded") {
      service.requestUploadIfNeeded().await()
      uploadedAddressBook should be('defined)
      uploadedAddressBook.get.contacts.map(_.id).toSet shouldEqual oneTwoThree
    }

    scenario("don't request post on start if contacts changed (within min upload delay)") {
      service.onAddressBookUploaded(AddressBook(Nil, Nil), Seq()).await()
      service.requestUploadIfNeeded() should failWith[NoSuchElementException]
      uploadedAddressBook shouldBe None
    }

    scenario("don't request post on start if contacts did not change (within min upload delay)") {
      service.addressBook.flatMap(ab => service.onAddressBookUploaded(ab, Seq())).await()
      service.requestUploadIfNeeded() should failWith[NoSuchElementException]
      uploadedAddressBook shouldBe None
    }

    scenario("request post on start if max upload delay elapsed") {
      (service.lastUploadTime := Some(now minus (zms.timeouts.contacts.uploadMaxDelay + 100.millis))).await()
      service.requestUploadIfNeeded().await()
      uploadedAddressBook should be('defined)
      uploadedAddressBook.get.contacts.map(_.id).toSet shouldEqual oneTwoThree
    }

    scenario("post only self / empty on start if sharing is disabled and version is up-to-date and max upload delay elapsed") {
      (service.addressBookVersionOfLastUpload := Some(CurrentAddressBookVersion)).await()
      (service.shareContactsPref := false).await()
      service.requestUploadIfNeeded().await()
      uploadedAddressBook should be('defined)
      uploadedAddressBook.get.self should not(be(empty))
      uploadedAddressBook.get.contacts should be(empty)
    }

    scenario("don't post on start if sharing is disabled and version is up-to-date and within max upload delay") {
      (service.lastUploadTime := Some(now minus (zms.timeouts.contacts.uploadMaxDelay - 2.seconds))).await()
      (service.addressBookVersionOfLastUpload := Some(CurrentAddressBookVersion)).await()
      (service.shareContactsPref := false).await()
      service.requestUploadIfNeeded() should failWith[NoSuchElementException]
      uploadedAddressBook shouldBe None
    }

    scenario("post only self / empty on start if sharing is disabled and version needs to be upgraded") {
      (service.addressBookVersionOfLastUpload := None).await()
      (service.shareContactsPref := false).await()

      service.requestUploadIfNeeded().await()
      uploadedAddressBook should be('defined)
      uploadedAddressBook.get.self should not(be(empty))
      uploadedAddressBook.get.contacts should be(empty)
    }
  }

  feature("Limited change checking rate") {
    scenario("check again, when the check interval elapsed") {
      (service.shareContactsPref := true).await()
      setLastCheckTime(EPOCH)
      service.requestUploadIfNeeded() should succeed
      setLastCheckTime(now minus zms.timeouts.contacts.uploadCheckInterval minus 1.second)
      service.requestUploadIfNeeded() should succeed
    }

    scenario("don't check again, when the check interval did not yet elapse") {
      service.requestUploadIfNeeded() should succeed
      service.requestUploadIfNeeded() should failWith[MayNotYetCheckAgainException.type]
    }

    scenario("only one request should make it through during the check interval") {
      val successfulRequests = new AtomicInteger(0)
      def delayed = randomDelay flatMap (_ => undelayed)
      def undelayed = service.requestUploadIfNeeded() map (_ => successfulRequests.getAndIncrement) recover { case MayNotYetCheckAgainException => () }

      Await.result(sequence((1 to 75).map(_ => delayed) ++ (1 to 25).map(_ => undelayed)), zms.timeouts.contacts.uploadCheckInterval)
      successfulRequests.get shouldEqual 1
    }
  }

  lazy val oneTwoThree = Set("1", "2", "3") map sha2 map ContactId
  def randomDelay = delay((abs(nextDouble()) * zms.timeouts.contacts.uploadCheckInterval.toMillis / 2d).toLong.millis).future
  def setLastCheckTime(instant: Instant) = zUserAndTimeOfLastCheck.set((zms.accountId, instant))
}
