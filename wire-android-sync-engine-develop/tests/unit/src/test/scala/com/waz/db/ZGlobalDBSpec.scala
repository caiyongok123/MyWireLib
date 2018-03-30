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

import com.waz.cache.CacheEntryData.CacheEntryDao
import com.waz.model.AccountData.AccountDataDao
import com.waz.model.KeyValueData.KeyValueDataDao
import com.waz.model.{EmailAddress, PhoneNumber, _}
import com.waz.utils.wrappers.{DB, DBHelper}
import com.waz.utils.{DbLoader, Managed}
import com.waz.znet.AuthenticationManager.Cookie
import org.robolectric.Robolectric
import org.scalatest._

import scala.util.Random

class ZGlobalDBSpec extends FeatureSpec with Matchers with OptionValues with Inspectors with BeforeAndAfter with RobolectricTests with DbLoader {
  lazy val dbHelper: DBHelper = new ZGlobalDB(Robolectric.application)

  after {
    dbHelper.close()
    Robolectric.application.getDatabasePath(dbHelper.getDatabaseName).delete()
  }

  feature("Database migrations") {

    def createZmessagingDb(id: AccountId, userId: UserId) = {
      val zdb = new ZMessagingDB(Robolectric.application, id.str)
      implicit val db: DB  = zdb.getWritableDatabase
      KeyValueDataDao.insertOrIgnore(KeyValueData("self_user_id", userId.str))
      db.close()
      zdb.close()
    }

    lazy val userId1 = UserId()
    lazy val userId2 = UserId()

    scenario("Prepare zms databases") {
      createZmessagingDb(AccountId("8546c628-c9e8-45d6-82dd-7f6dcb56e171"), userId1)
      createZmessagingDb(AccountId("09621ddd-736f-4ec5-b4b5-d24cbb56b9f3"), userId2)
    }

    scenario("Migrate ZUsers from 6") {
      Managed(loadDb("/db/ZGlobal_6.db")) foreach { implicit db: DB =>
        dbHelper.onUpgrade(db, 6, ZGlobalDB.DbVersion)

        AccountDataDao.list should have size 2
        AccountDataDao.list foreach { user =>
          user.phone shouldBe empty
        }
        AccountDataDao.list shouldEqual Seq(
          AccountData(AccountId("8546c628-c9e8-45d6-82dd-7f6dcb56e171"), Right(None), None, Some(EmailAddress("joachim.hofer+001@wearezeta.com")), None, handle = Some(Handle()), None, None, None, Some(Cookie("nK4NNJ7XN9-riGCcJ6YDCIXYEpHSYJWV2L9s3at1brf33Nb5TcFjY341iQHhQ7GjAS8sDgfXNx6NvzmSyXDXBQ==.v=1.k=1.d=1458844442.t=u.l=.u=e222adf6-22a0-4180-b628-936049f0899f.r=ccaae5e6")), userId = Some(userId1), firstLogin = false),
          AccountData(AccountId("09621ddd-736f-4ec5-b4b5-d24cbb56b9f3"), Right(None), None, Some(EmailAddress("joachim.hofer+003@wearezeta.com")), None, handle = Some(Handle()), None, None, None, Some(Cookie("u0mEC2etISwrAAf-_pNwG204HG5-Uf7EIRFFTp1TEqKGcSIXDbFC9_i8PftnKRTWSjUsAbZ-PHVIxS3eZDK-AQ==.v=1.k=1.d=1459026632.t=u.l=.u=9a01b792-42f6-4dee-a3c0-e22179d742f8.r=591684f6")), userId = Some(userId2), firstLogin = false )
        )
      }
    }

    scenario("Migrate ZUsers from 7") {
      Managed(loadDb("/db/ZGlobal_7.db")) foreach { implicit db: DB =>
        dbHelper.onUpgrade(db, 7, ZGlobalDB.DbVersion)
        AccountDataDao.list shouldEqual Seq(
          AccountData(AccountId("8546c628-c9e8-45d6-82dd-7f6dcb56e171"), Right(None), None, Some(EmailAddress("joachim.hofer+001@wearezeta.com")), None, handle = Some(Handle()), None, None, None, Some(Cookie("nK4NNJ7XN9-riGCcJ6YDCIXYEpHSYJWV2L9s3at1brf33Nb5TcFjY341iQHhQ7GjAS8sDgfXNx6NvzmSyXDXBQ==.v=1.k=1.d=1458844442.t=u.l=.u=e222adf6-22a0-4180-b628-936049f0899f.r=ccaae5e6")), userId = Some(userId1), firstLogin = false),
          AccountData(AccountId("09621ddd-736f-4ec5-b4b5-d24cbb56b9f3"), Right(None), None, Some(EmailAddress("joachim.hofer+003@wearezeta.com")), Some(PhoneNumber("+0123456789")), handle = Some(Handle()), None, None, None, Some(Cookie("u0mEC2etISwrAAf-_pNwG204HG5-Uf7EIRFFTp1TEqKGcSIXDbFC9_i8PftnKRTWSjUsAbZ-PHVIxS3eZDK-AQ==.v=1.k=1.d=1459026632.t=u.l=.u=9a01b792-42f6-4dee-a3c0-e22179d742f8.r=591684f6")), userId = Some(userId2), firstLogin = false)
        )
      }
    }

    scenario("Add length to CacheEntry") {
      Managed(loadDb("/db/ZGlobal_7.db")) foreach { implicit db: DB =>
        dbHelper.onUpgrade(db, 7, ZGlobalDB.DbVersion)
        val entries = CacheEntryDao.list
        entries should not be empty
        forAll(entries)(_.length shouldBe None)
        val changed = entries.map(_.copy(length = Some(Random.nextLong)))
        CacheEntryDao.insertOrReplace(changed)
        val updated = CacheEntryDao.list
        updated shouldEqual changed
      }
    }

  }
}
