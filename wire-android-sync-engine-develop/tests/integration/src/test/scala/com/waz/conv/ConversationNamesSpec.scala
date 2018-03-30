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
package com.waz.conv

import com.waz.api.{IConversation, ProvisionedApiSpec}
import com.waz.model.ConversationData.ConversationType
import com.waz.testutils.Implicits._
import com.waz.utils._
import org.scalatest.{FeatureSpec, Matchers}

import scala.concurrent.duration._

class ConversationNamesSpec extends FeatureSpec with Matchers with ProvisionedApiSpec { test =>

  implicit val timeout: Timeout = 3.seconds
  override val provisionFile = "/conversation_names.json"

  lazy val conversations = api.getConversations
  lazy val self = api.getSelf

  lazy val freddyMoop = oneToOneWith("auto2")
  lazy val freddyC = oneToOneWith("auto3")
  lazy val sandra = oneToOneWith("auto4")
  lazy val convo = groupWith(_.getName == "convo")
  lazy val unnamed = groupWith(_.getName != "convo")

  def selfConv: IConversation = conversations.getSelfConversation
  def oneToOneWith(user: String): IConversation = conv(ConversationType.OneToOne, _.getOtherParticipant.getId == provisionedUserId(user).str)
  def groupWith(p: IConversation => Boolean): IConversation = conv(ConversationType.Group, p)

  def conv(tpe: ConversationType, p: IConversation => Boolean = _ => true) =
    returning(conversations.find(c => c.getType == tpe && p(c))) { _ should be (defined) } .get

  scenario("initial sync") {
    withDelay(conversations should have size 5)(30.seconds)
  }

  scenario("Self conversation should have the correct name to begin with.") {
    withDelay { self.getName shouldEqual "Hans van Renaming" }
  }

  scenario("1-to-1 conversations should have correct names to begin with.") {
    withDelay { sandra.getName shouldEqual "Sandra Smith" }
    withDelay { freddyMoop.getName shouldEqual "Freddy Moop" }
    withDelay { freddyC.getName shouldEqual "Freddy Clooney" }
  }

  scenario("Group conversations should have the correct name to begin with.") {
    withDelay { convo.getName shouldEqual "convo" }
    withDelay { unnamed.getName.split(", ").toSet shouldEqual Set("Freddy M", "Freddy C", "Sandra") }
  }

  scenario("change self name without conflicts") {
    self.setName("Yeti yolo")
    withDelay { self.getName shouldEqual "Yeti yolo" }
    withDelay { self.getUser.getDisplayName shouldEqual "Yeti" }
    withDelay { self.getUser.getInitials shouldEqual "YY" }
  }

  scenario("Change self name with conflicts.") {
    self.setName("Freddy Meep")
    withDelay { self.getName shouldEqual "Freddy Meep" }
    withDelay { self.getUser.getDisplayName shouldEqual "Freddy Meep" }
    withDelay { self.getUser.getInitials shouldEqual "FM" }
  }

  scenario("Other user's display names should be updated.") {
    withDelay { unnamed.getUsers.toSeq.exists(_.getDisplayName == "Freddy Moop") shouldEqual true }
  }
  scenario("1-to-1 conversations should still have full names.") {
    withDelay { sandra.getName shouldEqual "Sandra Smith" }
    withDelay { freddyMoop.getName shouldEqual "Freddy Moop" }
    withDelay { freddyC.getName shouldEqual "Freddy Clooney" }
  }

  scenario("Unnamed group conversations should have updated names.") {
    withDelay { convo.getName shouldEqual "convo" }
    withDelay { unnamed.getName.split(", ").toSet shouldEqual Set("Freddy Moop", "Freddy C", "Sandra") }
  }
}
