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
package com.waz.avs

import java.io.File

import com.waz.utils._

object AVSystem {
  private object lazyInit {
    val paths = sys.props.get("java.library.path").fold2(Array.empty, _.split(File.pathSeparatorChar).map(new File(_)))
    val baseName = System.mapLibraryName("avs")
    paths.flatMap(d => Option(d.listFiles).getOrElse(Array.empty)).find(f => f.isFile && (f.getName == baseName || f.getName == "libavs.jnilib")) match {
      case None =>
        println(s"AVS native lib not found in java.library.path: $paths")
        System.loadLibrary("avs")
      case Some(in) =>
        val out = File.createTempFile("libavs", "")
        IoUtils.copy(in, out)
        System.load(out.getAbsolutePath)
        out.delete()
    }
  }

  def load: Unit = lazyInit
}
