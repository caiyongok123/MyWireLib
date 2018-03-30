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
import com.waz.api.impl.ErrorResponse
import com.waz.model.{AssetData, Dim2}
import com.waz.utils.wrappers.URI
import com.waz.model.AssetMetaData.Image
import com.waz.model.AssetMetaData.Image.Tag.Medium
import com.waz.model.messages.media.MediaAssetData.MediaWithImages
import com.waz.model.messages.media.{ArtistData, PlaylistData, TrackData}
import com.waz.testutils.Matchers._
import com.waz.threading.CancellableFuture
import com.waz.znet.Response.HttpStatus
import com.waz.znet.ZNetClient.EmptyClient
import com.waz.znet._
import org.json.JSONObject
import org.scalatest._

@Ignore class YouTubeClientSpec extends FeatureSpec with Matchers with JsonResponseFromResources with OptionValues with RobolectricTests {

  import YouTubeClient._

  feature("request") {
    scenario("generate get snippet request") {
      val req = requestById("videos", "mTWfqi3-3qU")
      req.httpMethod shouldEqual "GET"
      req.requiresAuthentication shouldEqual true
      req.absoluteUri should be('empty)
      req.resourcePath shouldEqual Some("/proxy/youtube/v3/videos?part=snippet&id=mTWfqi3-3qU")
    }
  }

  feature("response parsing") {

    scenario("parse snippet response") {
      sampleTrackResponse should beMatching({
        case TrackResponse(MediaWithImages(
        TrackData(MediaProvider.YOUTUBE, "HOW TO FIGHT A BABY", Some(ArtistData("Gavin McInnes", None)), "https://www.youtube.com/watch?v=mTWfqi3-3qU", Some(assetId), None, true, Some("https://www.youtube.com/watch?v=mTWfqi3-3qU"), None, _), img)) if img.exists(_.id == assetId) => ()
      })

      val image = TrackResponse.unapply(sampleTrackResponse).value.images.headOption.value
      image shouldEqual AssetData(metaData = Some(Image(Dim2(1280, 720), Medium)), source = Some(URI.parse("https://i.ytimg.com/vi/mTWfqi3-3qU/maxresdefault.jpg")))
//      image.map(img => (img.tag, img.url.value, img.width, img.height)) should contain theSameElementsInOrderAs Seq(
//        ("default", "https://i.ytimg.com/vi/mTWfqi3-3qU/default.jpg", 120, 90),
//        ("medium", "https://i.ytimg.com/vi/mTWfqi3-3qU/mqdefault.jpg", 320, 180),
//        ("high", "https://i.ytimg.com/vi/mTWfqi3-3qU/hqdefault.jpg", 480, 360),
//        ("standard", "https://i.ytimg.com/vi/mTWfqi3-3qU/sddefault.jpg", 640, 480),
//        ("maxres", "https://i.ytimg.com/vi/mTWfqi3-3qU/maxresdefault.jpg", 1280, 720))
    }

    scenario("parse 'no video found' snippet response") {
      TrackResponse.unapply(new JsonObjectResponse(new JSONObject(VideoNotFoundResponse))) shouldEqual None
    }

    scenario("parse playlist") {
      val Some(MediaWithImages(playlist, imageAssets)) = PlaylistResponse.unapply(samplePlaylistResponse)

      playlist should beMatching({
        case PlaylistData(MediaProvider.YOUTUBE, "Programming", Some(ArtistData("Hakka Labs", None)), "https://www.youtube.com/playlist?list=PLAesBe-zAQmEqM77n8XT59Hkjy0LDF6KX", Some(assetId), None, Vector(), _) if assetId == imageAssets.headOption.value.id => true
      })

      imageAssets should have size 1
    }

    scenario("parse playlist items") {
      val Some((tracks, imageAssets)) = PlaylistItemsResponse.unapply(samplePlaylistItemsResponse)

      tracks should beMatching({
        case Vector(
        TrackData(MediaProvider.YOUTUBE, "Building Applications with Containers, Kubernetes, and Mesos - Patrick Reilly", Some(ArtistData("Hakka Labs", None)),
        "https://www.youtube.com/watch?list=PLAesBe-zAQmEqM77n8XT59Hkjy0LDF6KX&index=0&v=00oWWFQ64Vc", Some(_), None, true, Some(_), None, _),
        _, _, _, _, _, _, _,
        TrackData(MediaProvider.YOUTUBE, "Media Queries: Responsible and Responsive Javascript - Alex Lee", Some(ArtistData("Hakka Labs", None)),
        "https://www.youtube.com/watch?list=PLAesBe-zAQmEqM77n8XT59Hkjy0LDF6KX&index=8&v=oe_crcsz_Ho", Some(_), None, true, Some(_), None, _),
        TrackData(MediaProvider.YOUTUBE, "Making Accessibility Part of Your Workflow - Estella Gonzalez Madison", Some(ArtistData("Hakka Labs", None)),
        "https://www.youtube.com/watch?list=PLAesBe-zAQmEqM77n8XT59Hkjy0LDF6KX&index=9&v=yrP_LY6Sgx4", Some(_), None, true, Some(_), None, _)) => true
      })

      imageAssets should have size 10

      val assetOfFirstTrack = for {
        t <- tracks.headOption
        a <- t.artwork
        asset <- imageAssets find (_.id == a)
      } yield asset

      withClue(assetOfFirstTrack) {


        assetOfFirstTrack.value shouldEqual AssetData(metaData = Some(Image(Dim2(1280, 720), Medium)), source = Some(URI.parse("https://i.ytimg.com/vi/mTWfqi3-3qU/maxresdefault.jpg")))
//        assetOfFirstTrack.value.versions.map(img => (img.tag, img.width, img.height)) should contain theSameElementsInOrderAs Seq(
//          ("default", 120, 90),
//          ("medium", 320, 180),
//          ("high", 480, 360),
//          ("standard", 640, 480),
//          ("maxres", 1280, 720))
      }
    }
  }

  feature("Load snippet") {

    var response: Response = Response(HttpStatus(200))

    lazy val client = new YouTubeClient(new EmptyClient() {
      override def apply[A](r: Request[A]): CancellableFuture[Response] = CancellableFuture.successful(response)
    })

    scenario("Load sample snippet") {
      response = Response(HttpStatus(200), sampleTrackResponse)
      client.loadVideo("id") should eventually(beMatching({
        case Right(MediaWithImages(track: TrackData, images)) => true
      }))
    }

    scenario("Load no video response") {
      response = Response(HttpStatus(200), new JsonObjectResponse(new JSONObject(VideoNotFoundResponse)))
      client.loadVideo("id") should eventually(beMatching({
        case Left(_) => true
      }))
    }

    scenario("Load deleted video response") {
      response = Response(HttpStatus(200), new JsonObjectResponse(new JSONObject(VideoDeletedResponse)))
      client.loadVideo("id") should eventually(beMatching({
        case Right(MediaWithImages(track: TrackData, images)) if track.title == "Deleted video" && images.isEmpty => true
      }))
    }

    scenario("403 response") {
      response = Response(HttpStatus(403), EmptyResponse)
      client.loadVideo("id") should eventually(beMatching({
        case Left(err: ErrorResponse) if err.code == 403 && err.label == "internal-error" => true
      }))
    }
  }

  feature("Load playlist") {
    lazy val client = new YouTubeClient(new EmptyClient() {
      @volatile var first = true

      override def apply[A](r: Request[A]): CancellableFuture[Response] = CancellableFuture.successful(Response(HttpStatus(200),
        if (first) {
          first = false; samplePlaylistResponse
        } else samplePlaylistItemsResponse
      ))
    })

    scenario("Load playlist") {
      client.loadPlaylist("id") should eventually(beMatching({
        case Right(MediaWithImages(PlaylistData(MediaProvider.YOUTUBE, "Programming", Some(ArtistData("Hakka Labs", None)), "https://www.youtube.com/playlist?list=PLAesBe-zAQmEqM77n8XT59Hkjy0LDF6KX", Some(_), None, tracks, _), assets)) if tracks.size == 10 && assets.size == 11 => true
      }))
    }
  }

  val VideoNotFoundResponse =
    """{
      |   "kind":"youtube#videoListResponse",
      |   "etag":"\"43qFkeEQBKio26KDSq1ZQMzjhSo\/FoCsU7yzN8iy5FBrKm0m_B4IVLI\"",
      |   "pageInfo":{
      |      "totalResults":0,
      |      "resultsPerPage":0
      |   },
      |   "items":[
      |
      |   ]
      |}
    """.stripMargin

  val VideoDeletedResponse =
    """
      |{
      |   "kind":"youtube#videoListResponse",
      |   "etag":"\"9iWEWaGPvvCMMVNTPHF9GiusHJA\/-JU0J5ZWguDL9EPVck8knzdiRcY\"",
      |   "pageInfo":{
      |      "totalResults":1,
      |      "resultsPerPage":1
      |   },
      |   "items":[
      |      {
      |         "kind":"youtube#video",
      |         "etag":"\"9iWEWaGPvvCMMVNTPHF9GiusHJA\/TM3HBBz0oZohAPuPU129kGKlvI8\"",
      |         "id":"YJ50YCpYkqM",
      |         "snippet":{
      |            "publishedAt":"1970-01-01T00:00:00.000Z",
      |            "channelId":"UCBR8-60-B28hp2BmDPdntcQ",
      |            "title":"Deleted video",
      |            "description":"This video is unavailable.",
      |            "channelTitle":"YouTube Spotlight",
      |            "categoryId":"22"
      |         }
      |      }
      |   ]
      |}
    """.stripMargin

  lazy val sampleTrackResponse = jsonFromResource("/client/youtube-track.json")
  lazy val samplePlaylistResponse = jsonFromResource("/client/youtube-playlist.json")
  lazy val samplePlaylistItemsResponse = jsonFromResource("/client/youtube-playlistitems.json")
}
