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
package com.waz.api.impl

import com.waz.RobolectricUtils
import com.waz.api.impl.otr.{OtrClient, OtrClients}
import com.waz.model.UserId
import com.waz.model.otr.{Client, ClientId}
import com.waz.service.ZMessaging
import com.waz.testutils.{MockUiModule, MockZMessaging}
import org.robolectric.Robolectric
import org.scalatest._
import org.scalatest.prop.Tables
import org.threeten.bp.Instant
import org.scalatest.prop.TableDrivenPropertyChecks._

import scala.util.Random

@Ignore class OtrClientSpec  extends FeatureSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with RobolectricTests with RobolectricUtils with Tables {

  implicit lazy val ui = new MockUiModule(new MockZMessaging())

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    ZMessaging.context = Robolectric.application
    ui.onCreate(Robolectric.application)
    ui.onResume()
  }

  scenario("Client ordering") {

    val clients = Seq(
      Client(ClientId(), "", "A", Some(Instant.ofEpochMilli(10))),
      Client(ClientId(), "", "Client", Some(Instant.ofEpochMilli(11))),
      Client(ClientId(), "", "Model 1", Some(Instant.ofEpochMilli(20))),
      Client(ClientId(), "", "Model 2", Some(Instant.ofEpochMilli(14))),
      Client(ClientId(), "", "Model 2", Some(Instant.ofEpochMilli(13))),
      Client(ClientId(), "", "Z", Some(Instant.ofEpochMilli(1)))
    )

    Random.shuffle(clients).toVector.sorted(OtrClients.ClientOrdering).map(_.id) shouldEqual clients.map(_.id)
  }

  scenario("Ids are padded with leading zeroes") {

    val clientIds = Table(
      ("111111111111",      "[[00]] 00 [[11]] 11 [[11]] 11 [[11]] 11"),
      ("1111111111111111",  "[[11]] 11 [[11]] 11 [[11]] 11 [[11]] 11"),
      ("abcd111111111111",  "[[AB]] CD [[11]] 11 [[11]] 11 [[11]] 11")
    )

    forAll(clientIds) { (raw: String, formatted: String) =>
      val client = new OtrClient(UserId(""), ClientId(raw), Client(ClientId(raw), ""))
      client.getDisplayId shouldEqual formatted
    }
  }
}
