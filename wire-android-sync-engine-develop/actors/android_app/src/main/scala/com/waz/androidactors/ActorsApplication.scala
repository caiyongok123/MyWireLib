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
package com.waz.androidactors

import android.app.Application
import com.waz.service.MetaDataService
import net.hockeyapp.android.{CrashManager, CrashManagerListener}

class ActorsApplication extends Application {

  lazy val metadata = new MetaDataService(getApplicationContext)
  lazy val remoteActor = new RemoteActorService(getApplicationContext)

  override def onCreate(): Unit = {
    super.onCreate()

    CrashManager.register(getApplicationContext, metadata.metaData.getString("HOCKEY_APP_KEY"), new CrashManagerListener() {
      override def shouldAutoUploadCrashes: Boolean = false
    })
  }
}
