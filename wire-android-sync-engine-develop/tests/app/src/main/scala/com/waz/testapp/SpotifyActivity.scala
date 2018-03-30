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
package com.waz.testapp

import android.app.Activity
import android.content.Intent
import com.spotify.sdk.android.authentication.{AuthenticationClient, AuthenticationResponse}

import scala.concurrent.Promise

class SpotifyActivity extends Activity {

  private val promisedAccessToken = Promise[String]

  def accessToken = promisedAccessToken.future

  override protected def onActivityResult(requestCode: Int, resultCode: Int, intent: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, intent)

    if (requestCode == 87654321) {
      val response = AuthenticationClient.getResponse(resultCode, intent)
      response.getType match {
        case AuthenticationResponse.Type.TOKEN => promisedAccessToken.success(response.getAccessToken)
        case AuthenticationResponse.Type.ERROR => promisedAccessToken.failure(new RuntimeException("error"))
        case _ => promisedAccessToken.failure(new RuntimeException("unhandled"))
      }
    }
  }
}
