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
package com.waz.api

import java.io.{File, FileWriter}
import java.util.Date

import com.waz.RobolectricUtils
import com.waz.service.ZMessaging
import com.waz.utils._
import net.hockeyapp.android.Constants
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

@Config(manifest = "/src/it/AndroidManifest.xml")
class HockeyCrashReporterSpec extends FeatureSpecLike with Matchers with BeforeAndAfter with BeforeAndAfterAll with RobolectricTests with RobolectricUtils {

  scenario("Report fake crash") {
    ShadowLog.stream = System.out

    ZMessaging.onCreate(Robolectric.application)

    val dump = returning(File.createTempFile("dump", ".txt")) { f =>
      IoUtils.withResource(new FileWriter(f)) { w =>
        w.write("test dump file")
      }
    }

    val log = returning(File.createTempFile("log", ".txt")) { f =>
      IoUtils.withResource(new FileWriter(f)) { write =>
        write.write("Package: " + Constants.APP_PACKAGE + "\n")
        write.write("Version: " + Constants.APP_VERSION + "\n")
        write.write("Android: " + Constants.ANDROID_VERSION + "\n")
        write.write("Manufacturer: " + Constants.PHONE_MANUFACTURER + "\n")
        write.write("Model: " + Constants.PHONE_MODEL + "\n")
        write.write("Date: " + new Date + "\n")
        write.write("\n")
        write.write("MinidumpContainer")
      }
    }

    Await.result(HockeyCrashReporter.uploadCrashReport("1f5602987d1617ab35573c2202438aaf", dump, log), 10.seconds) shouldEqual true
  }
}
