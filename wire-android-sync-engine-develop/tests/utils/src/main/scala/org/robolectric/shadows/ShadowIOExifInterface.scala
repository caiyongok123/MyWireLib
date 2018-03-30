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
package org.robolectric.shadows

import java.io.File

import android.media.ExifInterface
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifReader
import org.robolectric.annotation.Implements

import scala.collection.JavaConverters._

@SuppressWarnings(Array("UnusedDeclaration"))
@Implements(classOf[ExifInterface]) class ShadowIOExifInterface {

  var file: File = _
  lazy val metadata = ImageMetadataReader.readMetadata(file)

  val reader = new ExifReader()

  def __constructor__(fileName: String): Unit = {
    this.file = new File(fileName)
  }

  @Implements def getAttributeInt(tag: String, defaultValue: Int): Int = {
    metadata.getDirectories.asScala foreach { dir =>
      dir.getTags.asScala foreach { t =>
        if (t.getTagName == tag) return dir.getInt(t.getTagType)
      }
    }
    defaultValue
  }
}
