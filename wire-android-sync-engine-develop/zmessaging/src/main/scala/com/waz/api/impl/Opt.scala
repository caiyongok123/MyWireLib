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

import com.waz.api
import com.waz.utils.RichOption

case object Empty extends api.Opt[Nothing] {
  override def isEmtpy: Boolean = true
  override def isDefined: Boolean = false
  override def get: Nothing = throw new NoSuchElementException("get on empty Opt")
  override def getOrElse[B >: Nothing](b: B): B = b
}

case class Opt[A](value: A) extends api.Opt[A] {
  override def isEmtpy: Boolean = false
  override def isDefined: Boolean = true
  override def get: A = value
  override def getOrElse[B >: A](b: B): B = value
}

object Opt {
  def none[A]: api.Opt[A] = Empty
  def some[A](a: A): api.Opt[A] = Opt(a)
  def apply[A](o: Option[A]): api.Opt[A] = o.fold2(none, some)
}
