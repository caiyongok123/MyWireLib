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

import java.util.UUID.randomUUID

import com.waz.api.Invitations.InvitationDetailsCallback
import com.waz.api.ZMessagingApi.RegistrationListener
import com.waz.api._
import com.waz.api.impl.AccentColor
import com.waz.model.otr.ClientId
import com.waz.model.{Contact, _}
import com.waz.provision.InternalBackendClient
import com.waz.service
import com.waz.service._
import com.waz.sync.client.InvitationClient
import com.waz.sync.client.InvitationClient.ConfirmedInvitation
import com.waz.testutils.Matchers._
import com.waz.testutils.Implicits._
import com.waz.testutils._
import com.waz.threading.CancellableFuture.lift
import com.waz.threading.Threading
import com.waz.znet.ZNetClient._
import org.robolectric.shadows.ShadowContentResolver
import org.scalatest.{Matchers, _}

import scala.collection.JavaConverters._
import scala.concurrent._
import scala.util.Success

class PersonalInvitationSpec extends FeatureSpec with Matchers with BeforeAndAfterAll with OptionValues with ProvisionedApiSpec {
  import DefaultPatience._
  import Threading.Implicits.Background

  feature("Personal invitations") {
    scenario("Inviting a contact") {
      withInitializedApi {
        login() shouldBe true
        lazy val contacts = api.getContacts
        def firstContactMethod = contacts.get(0).getDetails.getContactMethods.asScala.head
        soon {
          contacts should have size 1
          firstContactMethod.getStringRepresentation shouldEqual contact.emailAddresses.head.str
        }
        firstContactMethod.invite("Meep!", null)
        soon {
          invitation.value.method.left.get shouldEqual contact.emailAddresses.head
          contacts.get(0).getDetails.hasBeenInvited shouldBe true
        }
        zmessaging.users.getSelfUserId.foreach(inviter = _)
        soon {
          inviter should not be empty
        }
      }

      val token = internalBackendClient.getInvitationToken(inviter.value, invitation.value.id).map(_.right.toOption).await()
      token should not be empty
      info(s"Invitation token: ${token.value}")

      withInitializedApi {
        val invitations = api.getInvitations
        val nameAndEmail = Promise[(String, String)]

        invitations.retrieveInvitationDetails(token.value, new InvitationDetailsCallback {
          override def onEmailAdressRetrieved(nameOfInvitee: String, emailAddress: String): Unit = nameAndEmail.success((nameOfInvitee, emailAddress))
          override def onPhoneNumberRetrieved(nameOfInvitee: String, phoneNumber: String): Unit = nameAndEmail.failure(new IllegalStateException("got phone instead of email"))
          override def onRetrievalFailed(response: ErrorResponse): Unit = nameAndEmail.failure(new IllegalStateException(s"got error response: $response"))
        })

        val (name, emailAddress) = nameAndEmail.future.await()
        name shouldEqual contact.name
        emailAddress shouldEqual contact.emailAddresses.head.str

        val registered = Promise[Unit]
        val credentials = CredentialsFactory.emailInvitationCredentials(emailAddress, "12345678", token.value)
        api.register(credentials, s"$name Invited", AccentColor(), new RegistrationListener {
          override def onRegistered(user: Self): Unit = registered.success(())
          override def onRegistrationFailed(code: Int, message: String, label: String): Unit = registered.failure(new IllegalStateException(s"registration failed; code: $code, message: $message, label: $label"))
        })
        registered.future.await()

        lazy val self = api.getSelf
        lazy val convs = api.getConversations
        soon {
          self.isLoggedIn shouldBe true
          self.getName shouldEqual s"$name Invited"
          convs should have size 1
          convs.get(0).getOtherParticipant.getName shouldEqual "auto1 user"
        }
      }

      withInitializedApi {
        login(contact.emailAddresses.head.str, "12345678") shouldBe true

        lazy val self = api.getSelf
        lazy val convs = api.getConversations
        soon {
          self.isLoggedIn shouldBe true
          self.getName shouldEqual "Android Test Invited"
          convs should have size 1
          convs.get(0).getOtherParticipant.getName shouldEqual "auto1 user"
          listMessages(convs.get(0).id) should have size 1 // started using device
        }
      }

      withInitializedApi {
        login() shouldBe true
        lazy val contacts = api.getContacts
        soon {
          contacts should have size 1
          contacts.get(0).getUser.getName shouldEqual "Android Test Invited"
        }
      }
    }
  }

  override val autoLogin = false
  override val initBehaviour = InitManually
  override val provisionFile = "/one_user.json"

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    ShadowContentResolver.reset()
    prepareContacts(contact)
  }

  lazy val contact = Contact(ContactId(), "Android Test", NameSource.StructuredName, "Android Test", SearchKey("Android Test"), Set(), Set(EmailAddress(s"android.test+$randomUUID@wire.com")))

  @volatile var invitation = Option.empty[ConfirmedInvitation]
  @volatile var inviter = Option.empty[UserId]
  @volatile var token = Option.empty[PersonalInvitationToken]

  lazy val internalBackendClient = new InternalBackendClient(globalModule.client, testBackend)

  override lazy val zmessagingFactory = new ZMessagingFactory(globalModule) {
    override def zmessaging(teamId: Option[TeamId], clientId: ClientId, user: UserModule): service.ZMessaging =
      new service.ZMessaging(teamId, clientId, user) {
        override lazy val invitationClient = new InvitationClient(zNetClient) {
          override def postInvitation(i: Invitation): ErrorOrResponse[Either[UserId, ConfirmedInvitation]] =
            lift(super.postInvitation(i).andThen { case Success(Right(Right(inv))) => invitation = Some(inv) })
        }
      }
  }
}
