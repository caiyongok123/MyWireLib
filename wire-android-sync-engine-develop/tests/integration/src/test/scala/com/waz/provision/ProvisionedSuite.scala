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

import java.io.InputStream

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.service.GlobalModuleImpl
import com.waz.utils.JsonDecoder
import org.json.JSONObject
import org.scalatest.Suite

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source
import scala.util.Random

trait ProvisionedSuite extends EmailClientSuite { suite: Suite =>
  import com.waz.provision.ProvisionedSuite._

  val provisionFile: String
  def globalModule: GlobalModuleImpl

  override def backendHostname: String = globalModule.backend.baseUrl.toString.stripPrefix("https://")

  val hex = Random.nextLong().toHexString

  lazy val conf = new JSONObject(Source.fromInputStream(resourceStream(provisionFile)).getLines().mkString("\n"))

  implicit lazy val UserDecoder: JsonDecoder[User] = new JsonDecoder[User] {
    import com.waz.utils.JsonDecoder._
    override def apply(implicit js: JSONObject): User = User(userId('email), 'password, 'name, decodeOptBoolean('connected).getOrElse(true))
  }

  implicit lazy val MessageDecoder: JsonDecoder[Message] = new JsonDecoder[Message] {
    import com.waz.utils.JsonDecoder._
    override def apply(implicit js: JSONObject): Message = Message(userId('from), 'text)
  }

  implicit lazy val ConversationDecoder: JsonDecoder[Conversation] = new JsonDecoder[Conversation] {
    import com.waz.utils.JsonDecoder._
    override def apply(implicit js: JSONObject): Conversation = Conversation('name, decodeStringSeq('participants).map(userId), array[Message](js.getJSONArray("messages")))
  }

  lazy val provisionUsers = JsonDecoder.array[User](conf.getJSONArray("users"))
  lazy val provisionEmails = provisionUsers.map(u => provisionedEmail(u.email))
  lazy val provisionConvs = if (conf.has("conversations")) JsonDecoder.array[Conversation](conf.getJSONArray("conversations")) else Nil

  def resourceStream(name: String): InputStream = getClass.getResourceAsStream(name)

  def provisionedUserId(user: String) = userIds(user)

  lazy val userProvs = provisionUsers.map {
    case User(em, pass, name, connected) => em -> new UserProvisioner(provisionedEmail(em), pass, name, connected, globalModule)
  } .toMap

  lazy val userIds = userProvs map {
    case (key, prov) => key -> prov.self
  }

  def provisionedEmail(key: String) = EmailClient.email.replace("@", s"+${key}_$hex@")

  private def userId(email: String) = email.replaceAll("@.*", "")

  override protected def beforeAll(): Unit = {
    import com.waz.ZLog._
    info("--- start of provisioning ----------------------------------------------------------------")
    val registered = userProvs.values.map(_.register())

    if (registered.exists(_.isLeft)) println(s"register failed for emails: $provisionEmails: error message: ${registered.head.left.get}")
    else println(s"registered and verified: $provisionUsers with $provisionEmails")

    val (o2o, group) = provisionConvs.partition(_.users.size == 2)

    o2o foreach { case Conversation(name, us, msgs) =>
      val msg = msgs.headOption.getOrElse(Message(us.head, "hello"))
      val from = msg.user
      val to = us.find(_ != from).get
      val fProv = userProvs(from)
      if (fProv.shouldConnect) {
        val tProv = userProvs(to)
        Await.result(fProv.connect(userIds(to), tProv.name, msg.content), 5.seconds)
        Await.result(tProv.accept(userIds(from)), 5.seconds)
      }
    }

    group foreach { case Conversation(name, us, msgs) =>
      val ids = us map userIds
      userProvs(us.head).createConv(name, ids.tail: _*)
    }

    info("--- end of provisioning ------------------------------------------------------------------")

    super.beforeAll()
  }


  override protected def afterAll(): Unit = {
    userProvs.values.foreach(_.close())
    super.afterAll()
  }
}

object ProvisionedSuite {

  case class User(email: String, pass: String, name: String, connected: Boolean)
  case class Message(user: String, content: String)
  case class Conversation(name: Option[String], users: Seq[String], msgs: Seq[Message])
}
