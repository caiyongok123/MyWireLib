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

import java.lang.Iterable

import com.waz.api.ConversationsList.ConversationCallback
import com.waz.api.OtrClient.DeleteCallback
import com.waz.api._
import com.waz.api.impl.EmailCredentials
import com.waz.model.ConversationData.{ConversationDataDao, ConversationType}
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.{AccountId, EmailAddress}
import com.waz.provision.{EmailClientSuite, UserProvisioner}
import com.waz.testutils.Implicits._
import com.waz.threading.Threading
import org.scalatest.{BeforeAndAfterAll, FeatureSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal

class LargeConversationsListSpec extends FeatureSpec with BeforeAndAfterAll with Matchers with ApiSpec with EmailClientSuite { test =>

  implicit lazy val dispatcher = Threading.Background

  override val autoLogin: Boolean = false

  def idFor(id: Int) = f"$id%04d"
  def emailFor(id: Int) = s"android.test+many_conversations_${idFor(id)}@wire.com"

  val indices = 1 to 320
  val emails = indices map emailFor
  val names = indices map { idx => s"test ${idFor(idx)}"}

  override def backendHostname: String = testBackend.baseUrl.toString.stripPrefix("https://")

  lazy val userProvs = emails zip names map { case (em, name) => new UserProvisioner(em, password, name, shouldConnect = true, globalModule) }

  override val email = emails.head

  lazy val userId = AccountId()

  lazy val regClient = globalModule.regClient
  lazy val client = globalModule.loginClient

  lazy val convs = api.getConversations

  override def beforeAll(): Unit = {
    super.beforeAll()
    //registerSpecificUsers()
    //createOneToOneConversations()
  }

  feature("Large conversation lists") {
    scenario("login") {
      login()
      withDelay { api.getSelf.isLoggedIn shouldEqual true }

      info(s"client reg state: ${api.getSelf.getClientRegistrationState}")
      if (api.getSelf.getClientRegistrationState == ClientRegistrationState.LIMIT_REACHED) {
        val clients = api.getSelf.getOtherOtrClients
        withDelay { clients.size should be > 0 }
        clients foreach (_ delete (password, new DeleteCallback {
          override def onClientDeleted(client: OtrClient): Unit = info(s"client deleted: ${client.getDisplayId}")
          override def onDeleteFailed(error: String): Unit = info(s"failed to delete otr client: $error")
        }))
      }

      //createRandomGroupConversations(320, 180)
    }

    scenario("more than 100 conversations are retrieved") {
      withDelay { convs.size shouldEqual 499 } (60.seconds)
      val oneToOnes = convs filter { _.getType == ConversationType.OneToOne }
      oneToOnes should have size 319
    }

    scenario("logout and log back in again") {
      api.logout()
      awaitUi(1.seconds)

      pauseApi()
      awaitUi(1.seconds)

      api.onDestroy()
      awaitUi(1.seconds)

      api = new impl.ZMessagingApi()
      api.onCreate(context)
      awaitUi(1.seconds)

      initApi()
      awaitUi(1.seconds)

      login()
      awaitUi(1.seconds)
    }

    scenario("retrieving more than 100 conversations after relogging") {
      val convs2 = api.getConversations
      withDelay { convs2.size shouldEqual 499 } (60.seconds)
      val oneToOnes = convs2 filter { _.getType == ConversationType.OneToOne }
      oneToOnes should have size 319
    }

    scenario("logout, clear conversations and log back in again") {
      ConversationDataDao.deleteAll(zmessaging.db.dbHelper.getWritableDatabase)
      pauseApi()
      api.logout()
      api.onDestroy()

      awaitUi(1.second)

      api = new impl.ZMessagingApi()
      api.onCreate(context)
      initApi()
      login()
    }

    scenario("retrieving more than 100 conversations after relogging and clearing data") {
      val convs2 = api.getConversations
      withDelay { convs2.size shouldEqual 499 } (60.seconds)
      val oneToOnes = convs2 filter { _.getType == ConversationType.OneToOne }
      oneToOnes should have size 319

      oneToOnes foreach { oneToOne =>
        println(s"email: ${oneToOne.getOtherParticipant.getId}")
        withDelay {
          oneToOne.getName.length should be > 1
        }
        println(s"1-to-1 name: ${oneToOne.getName}")
      }
    }

    scenario("logout") {
      api.logout()
    }
  }

  private def login(): Unit = {
    @volatile var success = false
    api.login(CredentialsFactory.emailCredentials(email, password), new LoginListener {
      override def onSuccess(user: Self): Unit = success = true
      override def onFailed(code: Int, message: String, label: String): Unit =()
    })

    withDelay { success shouldEqual true }
  }

  // call this in beforeAll() after a user wipe
  private def registerSpecificUsers(): Unit = {
    emails.zip(names) foreach { case (em, name) =>
//      Await.result(regClient.register(AccountId(), EmailCredentials(EmailAddress(em), Some(password)), name, None), 15.seconds) match {
//        case Right((user, _)) => println(s"created user: $user")
//        case Left(err) =>
//          println(s"got error: $err")
//          Await.result(client.requestVerificationEmail(EmailAddress(em)), 10.seconds) match {
//            case Right(res) => println(s"resent verification email to $em")
//            case Left(verr) => println(s"resending failed: $verr")
//          }
//      }
      awaitUi(2.seconds)
    }

    awaitUi(60.seconds)
    println("verified: " + emailClient.verifyEmail(emails: _*).count(_._2))
  }

  private def createOneToOneConversations(): Unit = {
    userProvs.tail map { to =>
      println(s"create to: ${to.name}, ${to.email}")
      try Await.result(userProvs.head.connect(to.self, to.name, s"hello ${to.name}"), 15.seconds)
      catch { case NonFatal(e) => println(s"connecting failed: $e") }
      try Await.result(to.accept(userProvs.head.self), 15.seconds)
      catch { case NonFatal(e) => println(s"accepting failed: $e") }
    } foreach { res =>
      println(s"Connected: $res")
    }
  }

  private def createRandomGroupConversations(currentNumConvs: Int, numConvsToAdd: Int): Unit = {
    withDelay { convs should have size currentNumConvs } (60.seconds)

    val others = convs.filter(_.getType == ConversationType.OneToOne).map(_.getOtherParticipant)

    withDelay { others should have size (indices.size - 1) } (60.seconds)

    withDelay { others foreach { _.getConnectionStatus shouldEqual ConnectionStatus.Accepted } }(30.seconds)

    1 to numConvsToAdd foreach { idx =>
      val numUsersToAdd = Random.nextInt(15) + 1
      val usersToAdd = (1 to numUsersToAdd map { _ =>
        others(Random.nextInt(others.size))
      }) :+ api.getSelf.getUser

      val before = convs.size()
      convs.createGroupConversation(usersToAdd.toSet.toSeq, new ConversationCallback {
        override def onConversationsFound(conversations: Iterable[IConversation]): Unit = ()
      })
      withDelay { convs should have size (before + 1) } (30.seconds)
    }

    awaitUi(60.seconds) // wait for sync to complete
  }
}
