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
package com.waz.utils.wrappers

import com.waz.db.DaoDB

import scala.language.implicitConversions

trait DBHelper {
  def onUpgrade(db: DB, i: Int, dbVersion: Int): Unit

  def getWritableDatabase: DB
  def getReadableDatabase: DB
  def getDatabaseName: String
  def close(): Unit
}

class SQLiteDBHelperWrapper(val helper: DaoDB) extends DBHelper {
  override def onUpgrade(db: DB, i: Int, dbVersion: Int): Unit = helper.onUpgrade(db, i, dbVersion)

  override def getWritableDatabase: DB = helper.getWritableDatabase
  override def getReadableDatabase: DB = helper.getReadableDatabase
  override def getDatabaseName: String = helper.getDatabaseName
  override def close(): Unit = helper.close()
}

object DBHelper {
  def apply(helper: DaoDB): DBHelper = new SQLiteDBHelperWrapper(helper)

  implicit def fromAndroid(helper: DaoDB): DBHelper = apply(helper)
}
