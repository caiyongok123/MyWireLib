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
package com.waz.model

import com.waz.model.AssetMetaData.Image.Tag.Medium
import com.waz.utils.JsonDecoder
import com.waz.znet.ContentEncoder.ByteArrayRequestContent
import org.json.JSONObject
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest._

@Ignore class UserInfoSpec extends FeatureSpec with Matchers with BeforeAndAfter with GeneratorDrivenPropertyChecks with RobolectricTests {

  val json = """{"email":"zbigniew@wearezeta.com","phone":null,"tracking_id":"15e38d10-b0cf-4877-9906-0923a74da3b0","accent_id":2,"picture":[{"content_length":7286,"data":null,"content_type":"image\/jpeg","id":"e56d823d-6c33-5ea5-9965-21230d4dc985","info":{"height":280,"tag":"smallProfile","original_width":512,"width":280,"name":"","correlation_id":"308133e9-6fd8-4652-baf4-3db41904e912","original_height":512,"nonce":"308133e9-6fd8-4652-baf4-3db41904e912","public":true}},{"content_length":28940,"data":null,"content_type":"image\/jpeg","id":"f0718f9b-8f92-5a2f-815a-8f85fca52690","info":{"height":512,"tag":"medium","original_width":512,"width":512,"name":"","correlation_id":"308133e9-6fd8-4652-baf4-3db41904e913","original_height":512,"nonce":"308133e9-6fd8-4652-baf4-3db41904e912","public":true}}],"name":"Zbigniew Szymanski","id":"5604c40a-4fec-454f-a388-e3bbe1bb6e5b"}"""


  feature("Json encoding") {

    scenario("Encode picture array") {
      val convId = RConvId()
      val userInfo = UserInfo(UserId(), Some("name"), picture = Some(Seq(AssetData(convId = Some(convId), metaData = Some(AssetMetaData.Image(Dim2(10, 10), Medium)), remoteId = Some(RAssetId())))))
      val json = UserInfo.ContentEncoder(userInfo).toString
      info(json)

      json should not contain "[["

      val js = new JSONObject(new String(UserInfo.ContentEncoder(userInfo).asInstanceOf[ByteArrayRequestContent].sourceData))
      val id = UserId()
      js.put("id", id.str)
      UserInfo.Decoder.getPicture(id)(js) shouldEqual userInfo.picture
    }
  }

  feature("Json decoding") {

    scenario("Use first correlation Id as assetId in every imageassetdata") { // this is needed because iOS sends images with random correlation_id

      val info = JsonDecoder.decode[UserInfo](json)
      info.picture should not be 'empty
      val pic = info.picture.get.head
      pic.id shouldEqual AssetId("308133e9-6fd8-4652-baf4-3db41904e912")
    }
  }
}
