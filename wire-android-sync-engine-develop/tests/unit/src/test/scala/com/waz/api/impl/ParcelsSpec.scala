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

import android.os.Parcelable
import com.waz.Generators._
import com.waz.model.{ConversationData, UserData}
import com.waz.service.messages.MessageAndLikes
import com.waz.testutils._
import com.waz.{RobolectricUtils, api}
import org.robolectric.Robolectric
import org.scalatest._
import org.scalatest.prop.GeneratorDrivenPropertyChecks

@Ignore class ParcelsSpec extends FeatureSpec with Matchers with GeneratorDrivenPropertyChecks with BeforeAndAfterAll with RobolectricTests with RobolectricUtils  {

  scenario("Generic invitation token")(forAll((_: api.Invitations.GenericToken).shouldRemainEquivalentAfterParcelingInTermsOf(identity)))
  scenario("Personal invitation token")(forAll((_: api.Invitations.PersonalToken).shouldRemainEquivalentAfterParcelingInTermsOf(identity)))
  scenario("Conversation that exists in cache")(forAll((c: ConversationData) => ui.convs.getConversation(c).shouldRemainEquivalentAfterParcelingInTermsOf(_.data)))
  scenario("Conversation that is not cached yet")(forAll((c: ConversationData) => new Conversation(c.id, c).shouldRemainEquivalentAfterParcelingInTermsOf(_.data)))
  scenario("User that exists in cache")(forAll((u: UserData) => ui.users.getUser(u).shouldRemainEquivalentAfterParcelingInTermsOf(_.data)))
  scenario("User that is not cached yet")(forAll((u: UserData) => new User(u.id, u)(ui).shouldRemainEquivalentAfterParcelingInTermsOf(_.data)))

  scenario("Create a parcel from a message and vice versa"){
    implicit val generatorDrivenConfig = PropertyCheckConfig(minSize = 0, maxSize = 10)

    forAll { m: MessageAndLikes =>
      val msg = ui.messages.cachedOrUpdated(m)

      val (data, likes, likedBySelf) = (msg.data, msg.likes, msg.likedBySelf)

      val clone = recreated(msg)

      clone.data shouldEqual m.message
      clone.likes shouldEqual m.likes
      clone.likedBySelf shouldEqual m.likedBySelf

      clone should be theSameInstanceAs msg

      clone.data shouldEqual data
      clone.likes shouldEqual likes
      clone.likedBySelf shouldEqual likedBySelf
    }
  }

  implicit class RichParcelable[A <: Parcelable](a: A) {
    def shouldRemainEquivalentAfterParcelingInTermsOf[B](f: A => B): Unit = f(recreated(a)) shouldEqual f(a)
  }

  def recreated[A <: Parcelable](original: A): A = withParcel { parcel =>
    parcel.writeParcelable(original, 0)
    parcel.setDataPosition(0)
    parcel.readParcelable[A](getClass.getClassLoader)
  }

  lazy val zmessaging = new MockZMessaging()

  implicit lazy val ui = new MockUiModule(zmessaging)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    ui.onCreate(Robolectric.application)
    ui.onResume()
  }
}
