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
package com.waz.api

import com.waz.model.RConvId
import com.waz.sync.client.ConversationsClient
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._

class ConversationsClientApiSpec extends FeatureSpec with Matchers with ProvisionedApiSpec {

  implicit val timeout = 20.seconds: Timeout
  override val provisionFile = "/provision_api.json"

  def client: ConversationsClient = zmessaging.convClient

  lazy val selfConvId = RConvId(api.getSelf.getUser.getId)

  scenario("Load all conversations") {
    Await.result(client.loadConversations(), timeout) match {
      case Right(conversations) => info(s"received conversations: $conversations")
      case _ => fail("load conversations failed - got None")
    }
  }

  scenario("Load self conversation") {
    withDelay { api.getSelf should not be null }
    Await.result(client.loadConversation(selfConvId), timeout) match {
      case Right(conversation) => info(s"received conversation: $conversation")
      case _ => fail("failed")
    }
  }
}
