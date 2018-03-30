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

import com.waz.api.ProvisionedApiSpec
import com.waz.api.impl.ErrorResponse
import com.waz.model.UserConnectionEvent
import org.scalatest.{EitherValues, FeatureSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class ConnectionsClientBackendSpec extends FeatureSpec with Matchers with EitherValues with ProvisionedApiSpec {

  val provisionFile = "/four_users_connected.json"

  implicit val timeout = 10.seconds: Timeout

  scenario("Load connections") {
    val client = zmessaging.connectionsClient

    val connections: Either[ErrorResponse, Seq[UserConnectionEvent]] = Await.result(client.loadConnections(pageSize = 2), timeout)

    connections should be('right)
    connections.right.value should have size 3
  }
}
