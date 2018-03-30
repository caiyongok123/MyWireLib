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
package com.waz.users

import com.waz.api.{ProvisionedApiSpec, UsernameValidation, UsernamesRequestCallback}
import com.waz.model.Handle
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

import scala.concurrent.duration._


class HandlesSpec extends FeatureSpec with Matchers with GivenWhenThen with ProvisionedApiSpec {
  implicit val timeout: Timeout = 30.seconds
  override val provisionFile: String = "/provision_handles.json"

  scenario("Test if handles are available") {
    userProvs("auto1").addHandle(Handle("auto1_handle"))
    withDelay{
      val usernamesToValidate = Array("auto1_handle", "bar")
      api.getUsernames.validateUsernames(usernamesToValidate)
      withDelay{
        val validations = api.getUsernames.getValidatedUsernames.getValidations(usernamesToValidate)
        validations should have size 1
        validations(0).username should be ("bar")
      }
    }
  }

  scenario("Test if single used handle is available") {
    var valid = false
    api.getUsernames.isUsernameAvailable("auto1_handle", new UsernamesRequestCallback{
      override def onUsernameRequestResult(usernameValidation: Array[UsernameValidation]): Unit = {
        valid = usernameValidation(0).isValid
      }

      override def onRequestFailed(errorCode: Integer): Unit = {
      }
    })
    withDelay{
      valid should be(false)
    }
  }

  scenario("Test if single unused handle is available") {
    var valid = false
    api.getUsernames.isUsernameAvailable("foo", new UsernamesRequestCallback{
      override def onUsernameRequestResult(usernameValidation: Array[UsernameValidation]): Unit = {
        valid = usernameValidation(0).isValid
      }

      override def onRequestFailed(errorCode: Integer): Unit = {
      }
    })
    withDelay{
      valid should be(true)
    }
  }
}
