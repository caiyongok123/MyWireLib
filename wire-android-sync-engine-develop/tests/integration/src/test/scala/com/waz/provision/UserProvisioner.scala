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
package com.waz.provision

import com.waz.ZLog._
import com.waz.api.KindOfVerification
import com.waz.api.impl.{EmailCredentials, ErrorResponse, PhoneCredentials}
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.service.GlobalModuleImpl
import com.waz.sync.client.{ConnectionsClient, ConversationsClient, CredentialsUpdateClient, UsersClient}
import com.waz.threading.CancellableFuture
import com.waz.threading.Threading.Implicits.Background
import com.waz.znet.ZNetClient.ErrorOrResponse
import com.waz.znet._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

class UserProvisioner(val email: String, val pass: String, val name: String, val shouldConnect: Boolean, global: GlobalModuleImpl) {
  private implicit val logTag: LogTag = logTagFor[UserProvisioner]

  val regClient = global.regClient
  val internalBackend = new InternalBackendClient(global.client, global.backend)

  def register(): Either[ErrorResponse, UserInfo] = Await.result(retryWithBackOff() {
    regClient.register(AccountData(email = Some(EmailAddress(email)), password = Some(pass)), name, None) } .flatMap {
      case Right((info, cookie)) => internalBackend.activateEmail(EmailAddress(email)) map (_ => Right(info))
      case Left(error) => CancellableFuture.successful(Left(error))
    }, 60.seconds)

  lazy val self = Await.result(retryWithBackOff() { userClient.loadSelf() }, 30.seconds) match {
    case Right(user) => user.id
    case Left(error) => throw new RuntimeException(s"unable to load self: $error")
  }

  def retryWithBackOff[T](maxRetries: Int = 8)(f: => CancellableFuture[Either[ErrorResponse, T]]): CancellableFuture[Either[ErrorResponse, T]] = {
    def loop(retry: Int, delay: FiniteDuration): ErrorOrResponse[T] = f flatMap {
      case Left(err) if Set(400, 420).contains(err.code) && retry < maxRetries => CancellableFuture.delay(delay) flatMap {_ => loop(retry + 1, delay * 3 / 2 + Random.nextInt(100).millis) }
      case other => CancellableFuture.successful(other)
    }
    loop(0, 1.second)
  }

  lazy val client = new ZNetClientImpl(null, null, null)
  lazy val userClient = new UsersClient(client)
  lazy val connClient = new ConnectionsClient(client)
  lazy val convClient = new ConversationsClient(client)
  lazy val credentialsClient = new CredentialsUpdateClient(client)

  def addPhone(phone: PhoneNumber) =
    credentialsClient.updatePhone(phone) flatMap { _ =>
      internalBackend.getPhoneActivationCode(phone) flatMap {
        case Left(error) =>
          CancellableFuture successful Left(error)
        case Right(code) =>
          regClient.verifyPhoneNumber(PhoneCredentials(phone, Some(code)), KindOfVerification.VERIFY_ON_UPDATE)
      }
    }

  def addHandle(handle: Handle) =
    credentialsClient.updateHandle(handle)

  def connect(user: UserId, name: String, msg: String) = connClient.createConnection(user, name, msg)

  def accept(user: UserId) = connClient.updateConnection(user, ConnectionStatus.Accepted)

  def createConv(name: Option[String], users: UserId*) = {
    val (create, join) = users.splitAt(64)

    convClient.postConversation(create, name, None) flatMap {
      case Right(conv) if join.nonEmpty => convClient.postMemberJoin(conv.conversation.remoteId, join)
      case resp => CancellableFuture.successful(resp)
    }
  }

  def close() = client.close()
}
