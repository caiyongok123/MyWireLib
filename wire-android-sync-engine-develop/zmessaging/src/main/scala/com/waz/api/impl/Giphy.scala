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

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api
import com.waz.model.AssetData
import com.waz.service.media.GiphyService
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.ui.UiModule

class Giphy(implicit ui: UiModule) extends com.waz.api.Giphy {
  import Threading.Implicits.Background
  
  override def random() = executeGiphyRequest((g, _, _) => g.getRandomGiphyImage)

  override def search(searchQuery: String) = executeGiphyRequest(_.searchGiphyImage(searchQuery, _, _))

  override def trending() = executeGiphyRequest(_.trending(_, _))

  private def executeGiphyRequest(f: (GiphyService, Int, Int) => CancellableFuture[Seq[(Option[AssetData], AssetData)]]): GiphyResults = {
    def load(offset: Int) = ui.zms.flatMap(zms => f(zms.giphy, offset, GiphyResults.PageSize)).recover {
      case e: Exception =>
        warn(s"giphy request processing failed", e)
        Iterable.empty[(Option[AssetData], AssetData)]
    }

    new GiphyResults(load)
  }
}

class GiphyResults(fetch: Int => CancellableFuture[Iterable[(Option[AssetData], AssetData)]])(implicit ui: UiModule) extends com.waz.api.GiphyResults with CoreList[api.ImageAsset] with EventualReadiness {
  import GiphyResults._

  var images     = IndexedSeq.empty[(Option[AssetData], AssetData)]
  var completed  = false
  var loadFuture = fetchNextPage()

  override def get(position: Int): api.ImageAsset = {
    if (!completed && loadFuture.isCompleted && position + RefreshThreshold >= images.size)
      loadFuture = fetchNextPage()

    val (preview, data) = images(position)
    ui.images.getLocalImageAssetWithPreview(preview, data)
  }

  private def fetchNextPage() =
    fetch(images.size) .map { res =>
      images = (images ++ res).distinct
      completed = res.size < PageSize
      ready()
      if (res.nonEmpty) notifyChanged()
    } (Threading.Ui)

  override def size(): Int = images.size
}

object GiphyResults {
  val RefreshThreshold = 5
  val PageSize = 25
}
