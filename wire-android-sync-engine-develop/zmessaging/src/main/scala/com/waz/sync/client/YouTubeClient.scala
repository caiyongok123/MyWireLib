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

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.MediaProvider
import com.waz.model.messages.media.MediaAssetData.{MediaWithImages, Thumbnail}
import com.waz.model.AssetData
import com.waz.model.messages.media._
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.znet.Response.SuccessHttpStatus
import com.waz.znet.ZNetClient.ErrorOr
import com.waz.znet._

import org.json.JSONObject

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

// TODO at some point, we probably should support paging of long playlists (> 50 items), too, but for now, let's just work on supporting playlists at all

class YouTubeClient(netClient: ZNetClient) {
  import Threading.Implicits.Background
  import YouTubeClient._

  def loadVideo(id: String): ErrorOr[MediaWithImages[TrackData]] =
    netClient.withFutureErrorHandling("loadVideo", requestById("videos", id)) {
      case Response(SuccessHttpStatus(), TrackResponse(track), _) => track
    }

  def loadPlaylist(id: String): ErrorOr[MediaWithImages[PlaylistData]] =
    netClient.chainedFutureWithErrorHandling("loadPlaylist", requestById("playlists", id)) {
      case Response(SuccessHttpStatus(), PlaylistResponse(pl), _) => loadPlaylistItems(id).mapRight { case (tracks, images)  =>
        pl.copy(media = pl.media.copy(tracks = tracks), images = pl.images ++ images) }
    }

  private def loadPlaylistItems(id: String): ErrorOr[(Vector[TrackData], Set[AssetData])] =
    netClient.withFutureErrorHandling("loadPlaylistItems", requestById("playlistItems", id, idName = "playlistId", limit = Some(50))) {
      case Response(SuccessHttpStatus(), PlaylistItemsResponse((tracks, images)), _) => (tracks, images)
    }
}

object YouTubeClient {

  val domainNames = Set("youtube.com", "youtu.be")

  val Base = "/proxy/youtube/v3"

  def requestById(resource: String, id: String, idName: String = "id", limit: Option[Int] = None) =
    Request.Get(s"$Base/$resource?part=snippet&$idName=$id${limit.fold("")("&maxResults=" + _)}")

  import JsonDecoder._

  case class Snippet(title: String, artist: Option[ArtistData], thumbs: Option[Vector[Thumbnail]], playlistId: Option[String], position: Option[Int], videoId: Option[String])

  def thumb(tag: String)(implicit js: JSONObject) = Thumbnail(tag, 'url, 'width, 'height)

  lazy val SnippetDecoder: JsonDecoder[Snippet] = new JsonDecoder[Snippet] {
    override def apply(implicit js: JSONObject) = Snippet(
      title = 'title,
      artist = decodeOptString('channelTitle) map { ArtistData(_, avatar = None) },
      thumbs = decodeThumbnails(js),
      playlistId = decodeOptString('playlistId),
      position = 'position,
      videoId = Option(js.optJSONObject("resourceId")) flatMap { res => Option(res.optString("videoId")) })
  }

  object TrackResponse {
    def unapply(response: ResponseContent): Option[MediaWithImages[TrackData]] = parse[TrackData](response, "youtube#videoListResponse")(TrackDecoder) .flatMap { case (media, assets) => media.headOption map (t => MediaWithImages(t, assets)) }

    lazy val TrackDecoder: JsonDecoder[MediaWithImages[TrackData]] = new JsonDecoder[MediaWithImages[TrackData]] {
      override def apply(implicit js: JSONObject): MediaWithImages[TrackData] = {
        val snippet = SnippetDecoder(js.getJSONObject("snippet"))
        val url = uri("https://www.youtube.com/watch")(_ :? ("v", decodeString('id))).toString
        val asset = snippet.thumbs map MediaAssetData.imageAsset

        MediaWithImages(TrackData(
          provider = MediaProvider.YOUTUBE,
          title = snippet.title,
          artist = snippet.artist,
          linkUrl = url,
          artwork = asset map (_.id),
          duration = None,
          streamable = true,
          streamUrl = Some(url),
          previewUrl = None,
          expires = MediaAssetData.defaultExpiry), asset.toSet)
      }
    }
  }

  object PlaylistResponse {
    def unapply(response: ResponseContent): Option[MediaWithImages[PlaylistData]] = parse[PlaylistData](response, "youtube#playlistListResponse")(PlaylistDecoder) .flatMap { case (media, assets) => media.headOption map (t => MediaWithImages(t, assets)) }

    lazy val PlaylistDecoder: JsonDecoder[MediaWithImages[PlaylistData]] = new JsonDecoder[MediaWithImages[PlaylistData]] {
      override def apply(implicit js: JSONObject): MediaWithImages[PlaylistData] = {
        val snippet = SnippetDecoder(js.getJSONObject("snippet"))
        val asset = snippet.thumbs map MediaAssetData.imageAsset

        MediaWithImages(PlaylistData(
          provider = MediaProvider.YOUTUBE,
          title = snippet.title,
          artist = snippet.artist,
          linkUrl = s"https://www.youtube.com/playlist?list=${decodeString('id)}",
          artwork = asset map (_.id),
          duration = None,
          tracks = Vector.empty,
          expires = MediaAssetData.defaultExpiry), asset.toSet)
      }
    }
  }

  object PlaylistItemsResponse {
    def unapply(response: ResponseContent): Option[(Vector[TrackData], Set[AssetData])] = parse[TrackData](response, "youtube#playlistItemListResponse")(TrackDecoder)

    lazy val TrackDecoder: JsonDecoder[MediaWithImages[TrackData]] = new JsonDecoder[MediaWithImages[TrackData]] {
      override def apply(implicit js: JSONObject): MediaWithImages[TrackData] = {
        val snippet = SnippetDecoder(js.getJSONObject("snippet"))
        val url = uri("https://www.youtube.com/watch") { _ :? ("list", snippet.playlistId) :& ("index", snippet.position map (_.toString)) :& ("v", snippet.videoId) } .toString
        val asset = snippet.thumbs map MediaAssetData.imageAsset

        MediaWithImages(TrackData(
          provider = MediaProvider.YOUTUBE,
          title = snippet.title,
          artist = snippet.artist,
          linkUrl = url,
          artwork = asset map (_.id),
          duration = None,
          streamable = true,
          streamUrl = Some(url),
          previewUrl = None,
          expires = MediaAssetData.defaultExpiry), asset.toSet)
      }
    }
  }

  private def parse[T <: MediaAssetData](response: ResponseContent, kind: String)(implicit dec: JsonDecoder[MediaWithImages[T]]): Option[(Vector[T], Set[AssetData])] = try {
    response match {
      case JsonObjectResponse(js) if Option(js.optString("kind")).contains(kind) && js.has("items") =>
        Some(MediaAssetData.extractImageAssets(arrayColl[MediaWithImages[T], Vector](js.getJSONArray("items"))))
      case resp =>
        warn(s"unknown response content: $resp")
        None
    }
  } catch {
    case NonFatal(e) =>
      warn(s"response: $response parsing failed", e)
      None
  }

  private def decodeThumbnails(js: JSONObject): Option[Vector[Thumbnail]] = Option(js.optJSONObject("thumbnails")) map { thumbsJs =>
    thumbsJs.keys().asInstanceOf[java.util.Iterator[String]].asScala.toVector map { key => thumb(key)(thumbsJs.getJSONObject(key)) }
  } map (_ sortBy (_.width))
}
