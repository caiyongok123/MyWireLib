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
package com.waz.mocked.registration

import com.waz.api.{CredentialsUpdateListener, MockedClientApiSpec}
import com.waz.mocked.MockBackend
import com.waz.model._
import com.waz.testutils.Matchers._

import org.scalatest.FeatureSpec

class UpdatePhoneNumberAndEmailAddressSpec extends FeatureSpec with MockedClientApiSpec with MockBackend {
  import DefaultPushBehaviour.Implicit
  lazy val self = api.getSelf

  feature("Update email address") {
    scenario("Update an existing email address") {
      {
        self.getEmail shouldEqual "email@test.com"
        self.accountActivated shouldEqual true
      }.soon

      val spy = new UpdateSpy
      self.setEmail("updated@test.com", spy.listener)

      {
        spy.updated shouldEqual true
        self.getEmail shouldEqual "email@test.com"  // not updated until verified
        self.accountActivated shouldEqual true       // still has a valid verified email address (the old one)
      }.soon

      addNotification(UserUpdateEvent(UserInfo(UserId(self.getUser.getId), email = Some(EmailAddress("updated@test.com")), phone = None)))

      {
        self.getEmail shouldEqual "updated@test.com"
        self.accountActivated shouldEqual true
      }.soon
    }
  }

  feature("Update phone number") {
    scenario("Add a phone number to the account") {
      {
        self.getPhone shouldEqual ""
      }.soon

      val spy = new UpdateSpy
      self.setPhone("12345678", spy.listener)

      {
        spy.updated shouldEqual true
        self.getPhone shouldEqual ""           // no phone number yet
      }.soon

      addNotification(UserUpdateEvent(UserInfo(UserId(self.getUser.getId), email = Some(EmailAddress("updated@test.com")), phone = Some(PhoneNumber("12345678")))))

      {
        self.getPhone shouldEqual "12345678"
      }.soon
    }

    scenario("Update an existing phone number") {
      val spy = new UpdateSpy
      self.setPhone("87654321", spy.listener)

      {
        spy.updated shouldEqual true
        self.getPhone shouldEqual "12345678"  // not updated until verified
      }.soon

      addNotification(UserUpdateEvent(UserInfo(UserId(self.getUser.getId), email = Some(EmailAddress("updated@test.com")), phone = Some(PhoneNumber("87654321")))))

      {
        self.getPhone shouldEqual "87654321"
      }.soon

    }
  }

  class UpdateSpy {
    @volatile var updated = false

    def reset(): Unit = updated = false

    val listener: CredentialsUpdateListener = new CredentialsUpdateListener {
      override def onUpdateFailed(code: Int, message: String, label: String): Unit = ()
      override def onUpdated(): Unit = updated = true
    }
  }
}
