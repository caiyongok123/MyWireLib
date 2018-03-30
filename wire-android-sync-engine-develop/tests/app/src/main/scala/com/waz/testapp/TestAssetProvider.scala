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

import android.content.res.AssetFileDescriptor
import android.content.{ContentProvider, ContentValues}
import android.database.Cursor
import android.net.Uri
import com.waz.ZLog._
import com.waz.model.Mime

class TestAssetProvider extends ContentProvider {
  private implicit val Tag: LogTag = logTagFor[TestAssetProvider]

  override def getType(uri: Uri): String = Mime.fromFileName(uri.getLastPathSegment).str

  override def update(uri: Uri, values: ContentValues, selection: String, selectionArgs: Array[String]): Int = 0

  override def insert(uri: Uri, values: ContentValues): Uri = null

  override def delete(uri: Uri, selection: String, selectionArgs: Array[String]): Int = 0

  override def onCreate(): Boolean = true

  override def query(uri: Uri, projection: Array[String], selection: String, selectionArgs: Array[String], sortOrder: String): Cursor = null

  lazy val assets = getContext.getAssets

  override def openAssetFile(uri: Uri, mode: LogTag): AssetFileDescriptor = assets.openFd(uri.getPath.substring(1)) // skip leading slash
}
