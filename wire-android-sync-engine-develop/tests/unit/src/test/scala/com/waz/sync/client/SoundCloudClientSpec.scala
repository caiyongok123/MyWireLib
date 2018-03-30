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
package com.waz.sync.client

import com.waz.api.MediaProvider
import com.waz.model.AssetMetaData.Image
import com.waz.model.AssetMetaData.Image.Tag.Medium
import com.waz.model.{AssetData, Dim2}
import com.waz.model.messages.media.MediaAssetData.MediaWithImages
import com.waz.model.messages.media.{ArtistData, PlaylistData, TrackData}
import com.waz.testutils.Matchers._
import com.waz.znet._
import org.scalatest._
import org.threeten.bp.Duration

@Ignore class SoundCloudClientSpec extends FeatureSpec with Matchers with OptionValues with JsonResponseFromResources with RobolectricTests {
  import SoundCloudClient._

  feature("response parsing") {
    scenario("parse track response") {
      sampleTrackResponse should beMatching({
        case SoundCloudResponse(MediaWithImages(TrackData(MediaProvider.SOUNDCLOUD, "Jugg feat. Monty", Some(ArtistData("FettyWap1738", Some(_))), "http://soundcloud.com/harlem_fetty/jugg-feat-monty", Some(_), Some(d), true, Some("https://api.soundcloud.com/tracks/224822099/stream"), None, _), assets)) if d == Duration.ofMillis(200350) && assets.size == 2 => true
      })

      val image = SoundCloudResponse.unapply(sampleTrackResponse).value.images.headOption.value
      image shouldEqual AssetData(metaData = Some(Image(Dim2(500, 500), Medium)))
//      images.map(img => (img.tag, img.width, img.height)) should contain theSameElementsInOrderAs Seq(("small", 32, 32), ("large", 100, 100), ("t500x500", 500, 500))
    }

    scenario("parse playlist response") {
      samplePlaylistResponse should beMatching({
        case SoundCloudResponse(MediaWithImages(PlaylistData(MediaProvider.SOUNDCLOUD, "bluu", Some(ArtistData("queen_diamondblu", Some(_))), "http://soundcloud.com/queen_diamondblu/sets/bluu", Some(_), Some(d), tracks, _), assets)) if d == Duration.ofMillis(7234951) && tracks.size == 34 && assets.size == 70 => true
      })
    }
  }

  lazy val sampleTrackResponse = jsonFromResource("/client/soundcloud-track.json")
  lazy val samplePlaylistResponse = jsonFromResource("/client/soundcloud-playlist.json")
}
