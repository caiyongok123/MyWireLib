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
package com.waz.invitations

import android.net.Uri
import com.waz.api.Invitations.{ConnectionCallback, InvitationUriCallback}
import com.waz.api._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.UserId
import com.waz.service.invitations.WebLink
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.duration._

class WebLinkInviteSpec extends FeatureSpec with Matchers with ProvisionedApiSpec {
  val provisionFile = "/two_users.json"

  implicit val timeout: Timeout = 15.seconds

  lazy val convs = api.getConversations
  lazy val invites = api.getInvitations

  feature("WebLink invites") {

    scenario("initial sync") {
      withDelay {
        awaitUi(3.seconds)
      }
    }

    scenario("generate invite link") {
      var uri = None: Option[Uri]
      invites.generateInvitationUri(new InvitationUriCallback {
        override def onInvitationGenerationFailed(): Unit = fail("uri generation failed")
        override def onInvitationGenerated(inviteUri: Uri): Unit = uri = Some(inviteUri)
      })
      withDelay {
        uri should be('defined)
        WebLink.unapply(uri.get).flatMap(_.userId) shouldEqual Some(UserId(api.getSelf.getUser.getId))
      }
    }

    scenario("try accepting self generated invite link") {
      val link = WebLink(WebLink.Token(UserId(api.getSelf.getUser.getId)))
      var conv = None: Option[IConversation]
      invites.requestConnection(InvitationTokenFactory.genericTokenFromUri(link), "test", new ConnectionCallback {
        override def onRequestFailed(response: ErrorResponse): Unit = fail(s"request failed: $response")
        override def onConnectionRequested(conversation: IConversation): Unit = conv = Some(conversation)
      })
      withDelay {
        conv should be('defined)
        conv.get.getType shouldEqual ConversationType.Self
      }
    }

    scenario("accept auto2 invite link") {
      val link = WebLink(WebLink.Token(provisionedUserId("auto2")))
      var conv = None: Option[IConversation]
      invites.requestConnection(InvitationTokenFactory.genericTokenFromUri(link), "test", new ConnectionCallback {
        override def onRequestFailed(response: ErrorResponse): Unit = fail(s"request failed: $response")
        override def onConnectionRequested(conversation: IConversation): Unit = conv = Some(conversation)
      })
      withDelay {
        conv should be('defined)
        conv.get.getType shouldEqual ConversationType.WaitForConnection
        conv.get.getId shouldEqual provisionedUserId("auto2").str
      }
    }
  }
}
