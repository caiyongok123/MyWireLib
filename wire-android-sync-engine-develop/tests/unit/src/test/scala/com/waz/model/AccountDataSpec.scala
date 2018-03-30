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

import com.waz.specs.AndroidFreeSpec
import com.waz.utils.JsonEncoder

class AccountDataSpec extends AndroidFreeSpec {

  val email: EmailAddress = EmailAddress("a@b.com")
  val password: String = "123"
  val phone: PhoneNumber = PhoneNumber("+0123456789")
  val code: ConfirmationCode = ConfirmationCode("1234")

  feature("JSON credentials") {
    scenario("Login credentials should use email if both email and password are present") {
      val account = AccountData(email = Some(email), password = Some(password))
      val json = JsonEncoder(account.addToLoginJson)

      json.get("email").shouldBe(email.str)
      json.get("password").shouldBe(password)
    }

    scenario("Login credentials should use phone if phone and code are present") {
      val account = AccountData(phone = Some(phone), code = Some(code))
      val json = JsonEncoder(account.addToLoginJson)

      json.get("phone").shouldBe(phone.str)
      json.get("code").shouldBe(code.str)
    }

    scenario("When all is present, email takes precedence") {
      val account = AccountData(email = Some(email), password = Some(password), phone = Some(phone), code = Some(code))
      val json = JsonEncoder(account.addToLoginJson)

      json.get("email").shouldBe(email.str)
      json.get("password").shouldBe(password)
    }

    scenario("Email with no password isn't used") {
      val account = AccountData(email = Some(email), phone = Some(phone), code = Some(code))
      val json = JsonEncoder(account.addToLoginJson)

      json.get("phone").shouldBe(phone.str)
      json.get("code").shouldBe(code.str)
    }
  }
}
