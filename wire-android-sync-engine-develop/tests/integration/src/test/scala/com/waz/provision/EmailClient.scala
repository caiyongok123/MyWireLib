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

import java.net.{URI, HttpURLConnection}
import javax.mail._
import javax.mail.internet.{MimeMultipart, MimeMessage}

import com.waz.build.InternalCredentials
import com.waz.threading.CancellableFuture

import scala.collection.{mutable, breakOut}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object EmailClient {
  val (email, password) = InternalCredentials.email
}
class EmailClient(val host: String = "imap.gmail.com", val user: String = EmailClient.email, val pass: String = EmailClient.password, val backendHostname: String) {
  private lazy val messageTextCache = mutable.WeakHashMap.empty[String, String]

  private lazy val store = {
    val props = System.getProperties
    props.setProperty("mail.store.protocol", "imaps")
    val session = Session.getDefaultInstance(props, null)
    val st = session.getStore("imaps")
    st.connect(this.host, this.user, this.pass)
    st

  }

  @volatile private var initialized = false

  val VerificationLink = "\"https://[^\"\\s]+/\\?key=[^\"]+\"".r

  lazy val inbox = {
    val folder = store.getFolder("Inbox")
    folder.open(Folder.READ_WRITE)
    prefetch(folder)
    initialized = true
    println("inbox size: " + folder.getMessageCount)
    folder
  }

  private def prefetch(folder: Folder) = {
    val fetchProfile = new FetchProfile()
    fetchProfile.add(FetchProfile.Item.ENVELOPE)
    fetchProfile.add(FetchProfile.Item.CONTENT_INFO)
    fetchProfile.add(FetchProfile.Item.FLAGS)
    folder.fetch(folder.getMessages, fetchProfile)
  }

  lazy val trash = {
    val folder = store.getFolder("[Gmail]/Trash")
    folder.open(Folder.READ_WRITE)
    folder
  }

  def close(): Unit = if (initialized) {
    inbox.close(true)
    trash.close(true)
    store.close()
  }

  private[waz] def verificationEmails =
    inbox.getMessages
      .filterNot(_.isExpunged)
      .filter(_.getFrom.exists(addr => addr.toString.contains("accounts@wire.com")))

  def getVerificationLink(emailAddresses: String): Option[URI] = getVerificationLinks(emailAddresses).get(emailAddresses)

  def getVerificationLinks(emailAddresses: String*): Map[String, URI] = getVerificationMessages(emailAddresses:_*).flatMap { case (k, v) => activationEndpointUri(v).map(u => k -> u) }

  def activationEndpointUri(email: Message): Option[URI] = VerificationLink findFirstIn messageString(email) map { s =>
    val frontendLink = new URI(s.stripPrefix("\"").stripSuffix("\""))
    new URI("https", null, backendHostname, 443, "/activate", frontendLink.getQuery, null)
  }

  def getVerificationMessages(emailAddresses: String*): Map[String, Message] = {
    def addressThatMatches(email: Message) = messageString(email) match { case s => emailAddresses.find(s.contains) }
    verificationEmails
      .flatMap(email => addressThatMatches(email).map(emailAddress => emailAddress -> email))
      .groupBy(_._1)
      .map { case (sender, emails) => sender -> emails.map(_._2).maxBy(_.getSentDate) }(breakOut)
  }

  def countVerificationEmails(emailAddresses: String*): Int =
    verificationEmails
      .map(messageString)
      .count(c => emailAddresses.exists(c.contains(_)))

  def deleteVerificationEmails(emailAddresses: String*): Int = {
    def isRequested(email: Message) = messageString(email) match { case s => emailAddresses.exists(s.contains) }
    val msgs = verificationEmails.filter(m => emailAddresses.isEmpty || isRequested(m)).toArray
    msgs.foreach(_.setFlag(Flags.Flag.DELETED, true))
    inbox.expunge
    println(s"deleted: ${msgs.size}")
    msgs.size
  }

  def verifyEmail(emailAddresses: String*): Map[String, Boolean] = {
    val retryDelay = 5.seconds

    def attemptToFetchLinks(): CancellableFuture[Map[String, URI]] = CancellableFuture {
      getVerificationLinks(emailAddresses: _*)
    } .flatMap { links =>
      if (emailAddresses.forall(links.contains)) CancellableFuture.successful(links)
      else {
        val missing = (emailAddresses.toSet -- links.keys).size
        println(s"attempted to fetch activation links but there are still $missing activation mails missing; retrying in $retryDelay")
        CancellableFuture.delay(retryDelay) flatMap { _ => attemptToFetchLinks() }
      }
    }

    val verify: ((String, URI)) => CancellableFuture[(String, Boolean)] = {
      case (emailAddress, link) => CancellableFuture.delayed(250.millis) {
        val conn = link.toURL.openConnection().asInstanceOf[HttpURLConnection]
        try {
          conn.connect()
          println(s"verification link for $emailAddress: $link \nreturned: ${conn.getResponseCode}")
          (emailAddress, conn.getResponseCode / 100 == 2)
        } finally conn.disconnect()
      }
    }

    val future = attemptToFetchLinks() flatMap { links =>
      // doing this one after the other to avoid HTTP 420 from the backend
      CancellableFuture.traverseSequential(links.toSeq)(verify)
    }

    val verified = Await.result(future.withTimeout(6.minutes), 6.minutes).toMap.withDefaultValue(false)

    deleteVerificationEmails(emailAddresses:_*)
    verified
  }

  def withFolder[A](name: String, mode: Int = Folder.READ_ONLY)(body: Folder => A): A = {
    val folder = store.getFolder(name)
    folder.open(mode)
    try {
      body(folder)
    } finally {
      folder.close(true)
    }
  }

  private[waz] def messageString(msg: Message): String = {
    def key = msg.getFrom.mkString + msg.getSentDate.getTime + msg.getSize
    messageTextCache synchronized {
      messageTextCache.getOrElseUpdate(key, {
        val mime = new MimeMessage(msg.asInstanceOf[MimeMessage]) // this rather obscure hack speeds up content retrieval significantly
        mime.getContent match {
          case m: MimeMultipart =>
            def extract(i: Int) = m.getBodyPart(i).getContent.asInstanceOf[String]
            (0 until m.getCount) map extract mkString "\n"
          case _ => ""
        }
      })
    }
  }
}
