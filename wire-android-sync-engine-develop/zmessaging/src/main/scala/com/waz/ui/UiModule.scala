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
package com.waz.ui

import android.content.Context
import android.net.Uri
import android.os.{Handler, Looper}
import com.waz.Control.getOrUpdate
import com.waz.ZLog.ImplicitTag._
import com.waz.api._
import com.waz.api.impl.Invitations
import com.waz.model.{AssetId, ConvId, UserId}
import com.waz.service._
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events._

import scala.concurrent.{ExecutionContext, Future}

trait ZMessagingResolverComponent {
  val zms: ZMessagingResolver
}

class ZMessagingResolver(ui: UiModule) {

  def account(implicit ec: ExecutionContext = Threading.Background) = ui.currentAccount.head.collect { case Some(acc) => acc }

  def withAccount[A](f: AccountManager => A)(implicit ec: ExecutionContext = Threading.Background): Future[A] =
    ui.currentAccount.head.collect { case Some(acc) => f(acc) }

  def apply[A](f: ZMessaging => A)(implicit ec: ExecutionContext = Threading.Background): CancellableFuture[A] =
    CancellableFuture.lift(ui.getCurrent.collect { case Some(zms) => f(zms) })

  def flatMap[A](f: ZMessaging => CancellableFuture[A])(implicit ec: ExecutionContext = Threading.Background) = apply(identity) flatMap f
  def flatMapFuture[A](f: ZMessaging => Future[A])(implicit ec: ExecutionContext = Threading.Background) = apply(identity).future flatMap f
}

trait UiEventContext {
  implicit val eventContext = new EventContext() {}
  def context: Context

  private[ui] var createCount = 0
  private[ui] var resumeCount = 0

  val onCreated = new SourceSignal[Option[Context]](None) with ForcedEventSource[Option[Context]]
  val onResumed = new SourceSignal[Option[Context]](None) with ForcedEventSource[Option[Context]]
  val onReset = new Publisher[Boolean] with ForcedEventSource[Boolean]

  def onCreate(context: Context): Unit = {
    Threading.assertUiThread()
    createCount += 1

    if (createCount == 1) {
      onCreated ! Some(context)
      eventContext.onContextStart()
    }
  }

  def onResume(): Unit = {
    Threading.assertUiThread()
    resumeCount += 1

    if (resumeCount == 1) {
      eventContext.onContextStart()
      onResumed ! Some(context)
    }
  }

  def onPause(): Unit = {
    Threading.assertUiThread()
    assert(resumeCount > 0, "onPause should be called exactly once for each onResume")
    resumeCount -= 1

    if (resumeCount == 0) {
      onResumed ! None
      eventContext.onContextStop()
    }
  }

  def onDestroy(): Unit = {
    Threading.assertUiThread()
    assert(createCount > 0, "onDestroy should be called exactly once for each onCreate")
    createCount -= 1
    if (createCount == 0) {
      eventContext.onContextStop()
      onCreated ! None
    }
  }
}

class UiModule(val accounts: AccountsServiceImpl) extends UiEventContext with ZMessagingResolverComponent {
  import com.softwaremill.macwire._

  private implicit val ui: UiModule = this

  val zms = new ZMessagingResolver(this)
  val uiCache = new UiCache[Uri, AnyRef](0)(this)

  val global = accounts.global
  def context = global.context
  def cache = global.cache
  def prefs = global.prefs
  def imageCache = global.imageCache
  def bitmapDecoder = global.bitmapDecoder
  val tracking = global.trackingService

  val currentAccount = accounts.activeAccountManager
  val currentZms = accounts.activeZms

  currentZms.onChanged { _ => onReset ! true }

  def getAccount = currentAccount.head.collect { case Some(acc) => acc } (Threading.Background)
  def getUserModule = getAccount.flatMap(_.userModule.head) (Threading.Background)
  def getZms = currentZms.head.collect { case Some(zms) => zms } (Threading.Background)

  def getCurrent = accounts.getActiveZms

  lazy val images: Images = new Images(context, bitmapDecoder, tracking)
  lazy val messages: Messages = new Messages
  lazy val users: Users = new Users
  lazy val convs: Conversations = new Conversations()
  lazy val invitations = new Invitations(zms, convs, global.regClient)
  lazy val contactDetails = wire[ContactDetailsCache]
  lazy val assets = new UiCache[AssetId, Asset](10)(this)

  lazy val globalImageLoader = global.imageLoader
  lazy val network = global.network

  def getOtherParticipantForOneToOneConv(id: ConvId): User = users.getUser(UserId(id.str)) // one-to-one conversation has the same id as the other user, so we can access it directly

  def cached[A <: AnyRef](uri: Uri, default: => A) = getOrUpdate(uiCache)(uri, default).asInstanceOf[A]

}

object UiModule {
  val UiHandler = new Handler(Looper.getMainLooper)
}
