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
package com.waz.service.invitations

import java.util.{Date, UUID}

import android.net.Uri
import com.waz.model.UserId
import org.scalacheck.Gen
import org.scalacheck.Gen.Choose
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FeatureSpec, Ignore, Matchers, RobolectricTests}

import scala.util.Success

// if this fails, it might be that you need to install the Java Cryptography Extensions (unlimited strength)
@Ignore class WebLinkSpec extends FeatureSpec with Matchers with GeneratorDrivenPropertyChecks with RobolectricTests {

  feature("Invite tokens") {

    val arrGen = Gen.listOfN(32, Choose.chooseByte.choose(0, 256.toByte)).map(_.toArray)

    scenario("Encode decode random bytes") {
      forAll(arrGen) { bytes: Array[Byte] =>
        WebLink.Decipher.get.doFinal(WebLink.Encipher.get.doFinal(bytes)).toSeq shouldEqual bytes
      }
    }

    scenario("Generate and decode random web link tokens") {
      forAll { (m: Long, l: Long) =>
        WebLink.Token(UserId(new UUID(m, l).toString)).userId shouldEqual Some(UserId(new UUID(m, l).toString))
      }
    }

    scenario("Don't include base64 padding chars in generated token") {
      WebLink.Token(UserId()).code should not contain '='
    }
  }

  feature("Weblink") {

    scenario("Decode/encode random Uris") {
      forAll { (m: Long, l: Long, t: Short) =>
        val hours = t & 0xffff
        val time = WebLink.TimeZero + hours * 3600000L
        val userId = UserId(new UUID(m, l).toString)
        val link = WebLink(WebLink.Token(userId, time))
        WebLink.decodeToken(WebLink.Token(link).code) shouldEqual Success((hours, UUID.fromString(userId.str)))
      }
      forAll { (m: Long, l: Long) =>
        val userId = UserId(new UUID(m, l).toString)
        val link = WebLink(WebLink.Token(userId))
        WebLink.unapply(link).get.userId shouldEqual Some(userId)
      }
    }

    scenario("Decode invite Uri token") {
      val date = new Date(1419349808 * 1000L)
      val uid = "0f86b28a-85b1-46f4-a387-29d5ab420001"

      val link = WebLink(WebLink.Token(UserId(uid), date.getTime))
      info(s"link: $link")
      val token = WebLink.Token(link)
      token.code shouldEqual link.getLastPathSegment

      WebLink.decodeToken(token.code) shouldEqual Success((WebLink.timestamp(date.getTime), UUID.fromString(uid)))

      val token1 = WebLink.Token(Uri.parse("https://www.wire.com/c/TgCil9_UdL3Z1bQV4bDwwnrR3qVbVjXp4e9ANq0-8Sc%3D"))
      WebLink.decodeToken(token1.code) shouldEqual Success((WebLink.timestamp(date.getTime), UUID.fromString(uid)))
    }
  }
}
