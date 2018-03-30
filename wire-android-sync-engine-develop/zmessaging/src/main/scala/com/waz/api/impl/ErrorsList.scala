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
package com.waz.api.impl

import java.util.Date

import com.waz.api.ErrorsList.{ErrorDescription, ErrorListener}
import com.waz.api.{ErrorType, IConversation}
import com.waz.ui.{SignalLoading, UiModule}

import scala.collection.JavaConverters._

class ErrorsList(implicit ui: UiModule) extends com.waz.api.ErrorsList with CoreList[ErrorDescription] with SignalLoading {

  var listeners = List[ErrorListener]()
  var errors = Array[ErrorDescription]()

  var prev = Set[String]()

  addLoader(_.errors.getErrors) { data =>
    errors = data.map { err =>
      new ErrorDescription {
        override def getId: String = err.id.str
        override def getType: ErrorType = err.errType
        override def getUsers = err.users.map(ui.users.getUser).asJava
        override def getMessages = err.messages.map(ui.messages.cachedOrNew).asJava
        override def getConversation: IConversation = err.convId.map(ui.convs.convById).orNull
        override def dismiss(): Unit = ui.zms.flatMapFuture(_.errors.dismissError(err.id))
        override def getTime: Date = err.time
        override def getResponse: ErrorResponse = ErrorResponse(err.responseCode, err.responseMessage, err.responseLabel)
      }
    }.toArray
    errors.foreach(e => listeners.foreach(_.onError(e)))
    prev = errors.map(_.getId).toSet
    notifyChanged()
  }

  override def addErrorListener(listener: ErrorListener): Unit = listeners ::= listener

  override def removeErrorListener(listener: ErrorListener): Unit = listeners = listeners.filter(_ != listener)

  override def dismissAll(): Unit = ui.zms(_.errors.dismissAllErrors())

  override def get(position: Int): ErrorDescription = errors(position)

  override def size(): Int = errors.length
}
