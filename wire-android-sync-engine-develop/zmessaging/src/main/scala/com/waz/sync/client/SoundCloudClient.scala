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

import java.net.URLEncoder

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.MediaProvider
import com.waz.api.impl.ErrorResponse
import com.waz.model.AssetData
import com.waz.model.messages.media.MediaAssetData.{MediaWithImages, Thumbnail}
import com.waz.model.messages.media.{ArtistData, MediaAssetData, PlaylistData, TrackData}
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.wrappers.URI
import com.waz.znet.Response.{ResponseBodyDecoder, SuccessHttpStatus}
import com.waz.znet.ResponseConsumer.JsonConsumer
import com.waz.znet.ZNetClient.ErrorOr
import com.waz.znet._
import org.json.JSONObject
import org.threeten.bp.Duration

class SoundCloudClient(netClient: ZNetClient) {
  import SoundCloudClient._
  import Threading.Implicits.Background

  def resolve(soundCloudUrl: String): ErrorOr[MediaWithImages[MediaAssetData]] =
    netClient.withFutureErrorHandling("SoundCloud resolve", get(proxyPath("resolve", soundCloudUrl))) {
      case Response(SuccessHttpStatus(), SoundCloudResponse(audioAsset), _) => audioAsset
    }

  private def get(path: String) = Request[Unit](Request.GetMethod, Some(path), decoder = Some(new ResponseBodyDecoder {
    // XXX we have to explicitly ignore the content type here because often, SoundCloud tells us that it returns application/xml when it really returns application/json...
    def apply(contentType: String, contentLength: Long): ResponseConsumer[_ <: ResponseContent] = new JsonConsumer(contentLength)
  }), followRedirect = false)

  def streamingLocation(url: String): ErrorOr[URI] =
    netClient(Request[Unit](Request.GetMethod, Some(proxyPath("stream", url)), followRedirect = false)).future map {
      case Response(Response.HttpStatus(Response.Status.MovedTemporarily, _), _, headers) => headers("Location").fold2(Left(ErrorResponse.internalError("no location header available")), { loc => Right(URI.parse(loc)) })
      case other => Left(ErrorResponse(other.status.status, s"Unexpected response when retrieving streaming uri for $url: $other", "unexpected-soundcloud-response"))
    }
}

object SoundCloudClient {
  import com.waz.utils.JsonDecoder._

  val domainNames = Set("soundcloud.com")

  def proxyPath(resource: String, url: String) = s"/proxy/soundcloud/$resource?url=${URLEncoder.encode(url, "utf8")}"

  implicit lazy val TrackDataDecoder: JsonDecoder[MediaWithImages[TrackData]] = new JsonDecoder[MediaWithImages[TrackData]] {
    override def apply(implicit js: JSONObject): MediaWithImages[TrackData] = {
      val (artist, artistImages) = decodeArtist(js)
      val artwork = decodeOptString('artwork_url) map decodeThumbnails
      val images = artistImages ++ artwork.toSet

      MediaWithImages(TrackData(
        provider = MediaProvider.SOUNDCLOUD,
        title = 'title,
        artist = artist,
        linkUrl = 'permalink_url,
        artwork = artwork map (_.id),
        duration = Some(Duration.ofMillis('duration)),
        streamable = 'streamable,
        streamUrl = 'stream_url,
        previewUrl = None,
        expires = MediaAssetData.defaultExpiry), images)
    }
  }

  implicit lazy val PlaylistDataDecoder: JsonDecoder[MediaWithImages[PlaylistData]] = new JsonDecoder[MediaWithImages[PlaylistData]] {
    override def apply(implicit js: JSONObject) = {
      val (artist, artistImages) = decodeArtist(js)
      val artwork = decodeOptString('artwork_url) map decodeThumbnails
      val (tracks, trackImages) = MediaAssetData.extractImageAssets(decodeSeq[MediaWithImages[TrackData]]('tracks))
      val images = artistImages ++ artwork.toSet ++ trackImages

      MediaWithImages(PlaylistData(
        provider = MediaProvider.SOUNDCLOUD,
        title = 'title,
        artist = artist,
        linkUrl = 'permalink_url,
        artwork = artwork map (_.id),
        duration = Some(Duration.ofMillis('duration)),
        tracks = tracks,
        expires = MediaAssetData.defaultExpiry), images)
    }
  }

  object SoundCloudResponse {
    def unapply(resp: ResponseContent): Option[MediaWithImages[MediaAssetData]] = resp match {
      case JsonObjectResponse(js) =>
        if (js has "tracks") Some(PlaylistDataDecoder(js))
        else if (js has "stream_url") Some(TrackDataDecoder(js))
        else {
          warn(s"unrecognized json for audio assets: ${js.toString(2)}")
          None
        }

      case other =>
        warn(s"unknown response content: $resp")
        None
    }
  }

  private def decodeArtist(js: JSONObject): (Option[ArtistData], Set[AssetData]) = {
    Option(js.optJSONObject("user")) map { implicit user =>
      val images = decodeOptString('avatar_url) map decodeThumbnails

      (Some(ArtistData(name = 'username, avatar = images map (_.id))), images.toSet)
    } getOrElse (None, Set.empty[AssetData])
  }

  private def decodeThumbnails(url: String): AssetData = {
    def thumb(tag: String, size: Int): Thumbnail = Thumbnail(tag = tag, url = url.replaceFirst("\\-large\\.jpg", s"-$tag.jpg"), width = size, height = size)

    MediaAssetData.imageAsset(Vector(thumb("small", 32), thumb("large", 100), thumb("t500x500", 500)))
  }
}
