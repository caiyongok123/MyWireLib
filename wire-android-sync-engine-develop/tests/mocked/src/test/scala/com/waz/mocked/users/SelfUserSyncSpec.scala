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
package com.waz.mocked.users

import com.waz.api.{ImageAssetFactory, MockedClientApiSpec}
import com.waz.cache.LocalData
import com.waz.mocked.MockBackend
import com.waz.model._
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.IoUtils.toByteArray
import com.waz.utils.returning
import com.waz.znet.ZNetClient._
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers, OptionValues}

import scala.concurrent.duration._

class SelfUserSyncSpec extends FeatureSpec with Matchers with OptionValues with BeforeAndAfterAll with MockBackend with MockedClientApiSpec { test =>
  import DefaultPushBehaviour.Implicit

  lazy val self = api.getSelf

  scenario("update self user phone and email address") {
    val phone = "+1234567890"

    soon {
      self.isLoggedIn shouldEqual true
      self.getEmail shouldEqual "email@test.com"
      self.accountActivated shouldBe true
      self.getPhone shouldBe empty
      self.data.value.phone shouldEqual None
      self.data.value.email should be (defined)
      self.accountActivated shouldEqual true
    }

    val selfId = self.getUser.getId

    addNotification(UserUpdateEvent(UserInfo(UserId(selfId), name = Some("name"), email = None, phone = Some(PhoneNumber(phone)))))

    soon {
      self.getName shouldEqual "name"
      self.getEmail shouldEqual "email@test.com"
      self.getPhone shouldEqual phone
      self.accountActivated shouldBe true
      self.data.value.email shouldEqual Some(EmailAddress("email@test.com"))
      self.data.value.phone shouldEqual Some(PhoneNumber(phone))
    }

    addNotification(UserUpdateEvent(UserInfo(UserId(selfId), email = Some(EmailAddress("email1@test.com")), phone = None)))

    soon {
      self.getName shouldEqual "name"
      self.getEmail shouldEqual "email1@test.com"
      self.getPhone shouldEqual phone
      self.accountActivated shouldBe true
      self.data.value.email shouldEqual Some(EmailAddress("email1@test.com"))
      self.data.value.phone shouldEqual Some(PhoneNumber(phone))
    }
  }

  scenario("update self user picture") {
    self.setPicture(ImageAssetFactory.getImageAsset(toByteArray(getClass.getResourceAsStream("/images/penguin.png"))))

    val selfPicture = soon(returning(self.getPicture)(_ should not be empty))

    soon {
      sentUserInfo.value.picture.get.head.remoteId shouldBe Some(RAssetId("medium-picture"))
      selfPicture.data.remoteId shouldBe Some(RAssetId("medium-picture"))
    }
  }

  override def updateSelf(info: UserInfo): ErrorOrResponse[Unit] = {
    sentUserInfo = Some(info)
    super.updateSelf(info)
  }

  @volatile private var sentUserInfo = Option.empty[UserInfo]
}
