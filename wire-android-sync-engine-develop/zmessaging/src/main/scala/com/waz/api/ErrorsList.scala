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

import java.util.Date

object ErrorsList {

  trait ErrorDescription {
    def getId: String
    def getType: ErrorType
    def getResponse: ErrorResponse
    def getConversation: IConversation
    def getUsers: java.lang.Iterable[_ <: User]
    def getMessages: java.lang.Iterable[_ <: Message]
    def getTime: Date

    /**
     * Removes error from list.
     */
    def dismiss(): Unit
  }

  trait ErrorListener {
    def onError(error: ErrorsList.ErrorDescription): Unit
  }
}

trait ErrorsList extends CoreList[ErrorsList.ErrorDescription] {
  def addErrorListener(listener: ErrorsList.ErrorListener): Unit
  def removeErrorListener(listener: ErrorsList.ErrorListener): Unit
  def dismissAll(): Unit
}
