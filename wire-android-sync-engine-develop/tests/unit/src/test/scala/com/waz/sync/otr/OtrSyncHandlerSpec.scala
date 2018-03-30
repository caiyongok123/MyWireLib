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
package com.waz.sync.otr

import java.util.Date

import com.waz.cache.CacheService
import com.waz.content.{ConversationStorage, UsersStorage}
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GenericMessage.TextMessage
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.assets.AssetService
import com.waz.service.conversation.ConversationsService
import com.waz.service.{ErrorsService, UserService}
import com.waz.service.messages.MessagesService
import com.waz.service.otr.OtrService
import com.waz.service.push.PushService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.{AssetClient, MessagesClient, OtrClient, UsersClient}
import com.waz.sync.client.OtrClient.{ClientMismatch, EncryptedContent}
import com.waz.znet.ZNetClient
import org.scalatest.Ignore


@Ignore class OtrSyncHandlerSpec extends AndroidFreeSpec {

  lazy val selfUser = UserData("test")
  lazy val clientId = ClientId()


  lazy val conv = ConversationData(ConvId(), RConvId(), None, UserId(), ConversationType.Group)

  type EncryptRequest = (ConvId, GenericMessage, Boolean)
  var encryptMsgRequests = Seq.empty[EncryptRequest]

  type PostMsgRequest = (RConvId, OtrMessage, Boolean)
  var postMsgRequests = Seq.empty[PostMsgRequest]
  var postMsgResponse: ClientMismatch = ClientMismatch(new Date)

  var encryptedContent: EncryptedContent = EncryptedContent(Map.empty)

  private val znet                = mock[ZNetClient]
  private val client              = new OtrClient(znet)
  private val msgClient           = new MessagesClient(znet)
  private val assetClient         = mock[AssetClient]
  private val otr                 = mock[OtrService]
  private val assets              = mock[AssetService]
  private val convs               = mock[ConversationsService]
  private val convStorage         = mock[ConversationStorage]
  private val users               = mock[UserService]
  private val messages            = mock[MessagesService]
  private val errors              = mock[ErrorsService]
  private val clientsSyncHandler  = mock[OtrClientsSyncHandler]
  private val cache               = mock[CacheService]
  private val push                = mock[PushService]
  private val usersClient         = new UsersClient(znet)
  private val teamId              = TeamId()
  private val usersStorage        = mock[UsersStorage]

  val handler = new OtrSyncHandlerImpl(client, msgClient, assetClient, otr, assets, convs, convStorage,
    users, messages, errors, clientsSyncHandler, cache, push, usersClient, Some(teamId), usersStorage)

  feature("Error recovery") {

    scenario("Retry three times, use fake content on first retry, and ignore_missing on second") {
      val msg = TextMessage("test", Map.empty)
      val missing = Map(UserId() -> Seq(ClientId()))
      postMsgResponse = ClientMismatch(Map.empty, missing, Map.empty, new Date(1))

      val res = handler.postOtrMessage(conv, msg)
      res shouldEqual Right(new Date(1))

      val otrMsg = OtrMessage(clientId, encryptedContent)

      encryptMsgRequests shouldEqual Seq((conv.id, msg, false), (conv.id, msg, true), (conv.id, msg, true))
      postMsgRequests shouldEqual Seq((conv.remoteId, otrMsg, false), (conv.remoteId, otrMsg, false), (conv.remoteId, otrMsg, true))
    }
  }

}
