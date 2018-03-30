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

import android.content.Context

trait Usernames {
  def generateUsernameFromName(name: String, context: Context): String

  def validateUsernames(usernames: Array[String]): Unit
  def getValidatedUsernames: ValidatedUsernames
}

trait UsernamesRequestCallback{
  def onUsernameRequestResult(usernameValidation: Array[UsernameValidation]) : Unit = {}
  def onRequestFailed(errorCode: Integer): Unit
}

case class UsernameValidation(username: String, reason: UsernameValidationError) {
  def isValid: Boolean = reason == UsernameValidationError.NONE
}
