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

import com.waz.api.{Contacts, MockedClientApiSpec}
import com.waz.mocked.MockBackend
import com.waz.model.NameSource._
import com.waz.model._
import com.waz.service.SearchKey
import com.waz.testutils.Matchers._
import com.waz.testutils._
import com.waz.utils._
import org.scalatest.{FeatureSpec, OptionValues}

import scala.io.Source

class ContactsPerfSpec extends FeatureSpec with OptionValues with MockedClientApiSpec with MockBackend {

  scenario("Login, importing and loading contacts") {
    val start = nanoNow
    afterInitialization { contacts =>
      soon(contacts should have size 5000)
      info(s"... finished in ${start.untilNow}")
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    sampleContacts foreach prepareContacts
  }

  override val initBehaviour = InitManually

  def afterInitialization[A](f: Contacts => A): A = withInitializedApi(f(api.getContacts))

  def sampleContacts: Managed[Vector[Contact]] =
    for {
      stream <- Managed(getClass.getResourceAsStream(s"/contacts.csv"))
      source <- Managed(Source.fromInputStream(stream)(scala.io.Codec.UTF8))
      lines   = source.getLines.map(_.split(','))
    } yield lines.collect { case v =>
      Contact(ContactId(), v(0), StructuredName, v(0), SearchKey(v(0)), Set(PhoneNumber(v(1)), PhoneNumber(v(2))), Set(EmailAddress(v(3)), EmailAddress(v(4))))
    }.toVector

  implicit lazy val SourceCleanup: Cleanup[Source] = new Cleanup[Source] { def apply(a: Source): Unit = a.close() }
}
