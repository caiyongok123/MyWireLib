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
package com.waz.service

import android.content.{ComponentCallbacks2, Context}
import com.evernote.android.job.{JobCreator, JobManager}
import com.softwaremill.macwire._
import com.waz.ZLog._
import com.waz.api.ContentSearchQuery
import com.waz.content.{MembersStorageImpl, UsersStorageImpl, ZmsDatabase, _}
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.EventScheduler.{Interleaved, Sequential, Stage}
import com.waz.service.assets._
import com.waz.service.call._
import com.waz.service.conversation._
import com.waz.service.downloads.{AssetLoader, AssetLoaderImpl}
import com.waz.service.images.{ImageAssetGenerator, ImageLoader, ImageLoaderImpl}
import com.waz.service.invitations.InvitationServiceImpl
import com.waz.service.media._
import com.waz.service.messages._
import com.waz.service.otr._
import com.waz.service.push._
import com.waz.service.teams.{TeamsService, TeamsServiceImpl}
import com.waz.sync.client._
import com.waz.sync.handler._
import com.waz.sync.otr.{OtrSyncHandler, OtrSyncHandlerImpl}
import com.waz.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import com.waz.ui.UiModule
import com.waz.utils.Locales
import com.waz.utils.wrappers.AndroidContext
import com.waz.zms.FetchJob
import com.waz.znet._
import org.threeten.bp.{Clock, Instant}

import scala.concurrent.{Future, Promise}
import scala.util.Try

class ZMessagingFactory(global: GlobalModule) {

  implicit val tracking = global.trackingService

  def baseStorage(accountId: AccountId) = new StorageModule(global.context, accountId, "", global.prefs)

  def auth(accountId: AccountId) = new AuthenticationManager(accountId, global.accountsStorage, global.loginClient, tracking)

  def client(accountId: AccountId, auth: AuthenticationManager): ZNetClient = new ZNetClientImpl(Some(auth), global.client, global.backend.baseUrl)

  def usersClient(client: ZNetClient) = new UsersClient(client)

  def teamsClient(client: ZNetClient) = new TeamsClientImpl(client)

  def credentialsClient(netClient: ZNetClient) = new CredentialsUpdateClient(netClient)

  def cryptobox(accountId: AccountId, storage: StorageModule) = new CryptoBoxService(global.context, accountId, global.metadata, storage.userPrefs)

  def userModule(userId: UserId, account: AccountManager) = wire[UserModule]

  def zmessaging(teamId: Option[TeamId], clientId: ClientId, userModule: UserModule) = wire[ZMessaging]
}

class StorageModule(context: Context, accountId: AccountId, dbPrefix: String, globalPreferences: GlobalPreferences) {
  lazy val db                                         = new ZmsDatabase(accountId, context, dbPrefix)
  lazy val userPrefs                                  = UserPreferences.apply(context, db, globalPreferences)
  lazy val usersStorage:      UsersStorage        = wire[UsersStorageImpl]
  lazy val otrClientsStorage: OtrClientsStorage       = wire[OtrClientsStorageImpl]
  lazy val membersStorage                             = wire[MembersStorageImpl]
  lazy val assetsStorage :    AssetsStorage           = wire[AssetsStorageImpl]
  lazy val reactionsStorage                           = wire[ReactionsStorageImpl]
  lazy val notifStorage:      NotificationStorage     = wire[NotificationStorageImpl]
  lazy val convsStorage:      ConversationStorage     = wire[ConversationStorageImpl]
  lazy val msgDeletions:      MsgDeletionStorage      = wire[MsgDeletionStorageImpl]
  lazy val searchQueryCache:  SearchQueryCacheStorage = wire[SearchQueryCacheStorageImpl]
  lazy val msgEdits:          EditHistoryStorage      = wire[EditHistoryStorageImpl]
}


class ZMessaging(val teamId: Option[TeamId], val clientId: ClientId, val userModule: UserModule) {

  private implicit val logTag: LogTag = logTagFor[ZMessaging]
  private implicit val dispatcher = new SerialDispatchQueue(name = "ZMessaging")

  val account    = userModule.account
  val global     = account.global
  val selfUserId = userModule.userId

  val accountId  = account.id
  val auth       = account.auth
  val zNetClient = account.netClient
  val storage    = account.storage
  val lifecycle  = global.lifecycle

  lazy val accounts             = ZMessaging.currentAccounts
  implicit lazy val evContext   = userModule.accountContext
  lazy val cryptoBox            = account.cryptoBox
  lazy val sync                 = userModule.sync
  lazy val syncHandler          = userModule.syncHandler
  lazy val otrClientsService    = userModule.clientsService
  lazy val syncContent          = userModule.syncContent
  lazy val syncRequests         = userModule.syncRequests
  lazy val otrClientsSync       = userModule.clientsSync

  def context           = global.context
  def contextWrapper    = new AndroidContext(context)
  def googleApi         = global.googleApi
  def globalToken       = global.tokenService
  def imageCache        = global.imageCache
  def permissions       = global.permissions
  def phoneNumbers      = global.phoneNumbers
  def prefs             = global.prefs
  def bitmapDecoder     = global.bitmapDecoder
  def timeouts          = global.timeouts
  def cache             = global.cache
  def globalRecAndPlay  = global.recordingAndPlayback
  def tempFiles         = global.tempFiles
  def metadata          = global.metadata
  def network           = global.network
  def blacklist         = global.blacklist
  def backend           = global.backend
  def accountsStorage   = global.accountsStorage
  def teamsStorage      = global.teamsStorage
  def videoTranscoder   = global.videoTranscoder
  def audioTranscader   = global.audioTranscoder
  def avs               = global.avs
  def loadService       = global.loaderService
  def flowmanager       = global.flowmanager
  def mediamanager      = global.mediaManager
  def gNotifcations     = global.notifications
  def tracking          = global.trackingService

  def db                = storage.db
  def userPrefs         = storage.userPrefs
  def usersStorage      = storage.usersStorage
  def otrClientsStorage = storage.otrClientsStorage
  def membersStorage    = storage.membersStorage
  def assetsStorage     = storage.assetsStorage
  def reactionsStorage  = storage.reactionsStorage
  def notifStorage      = storage.notifStorage
  def convsStorage      = storage.convsStorage
  def msgDeletions      = storage.msgDeletions
  def msgEdits          = storage.msgEdits
  def searchQueryCache  = storage.searchQueryCache

  lazy val messagesStorage: MessagesStorage = wire[MessagesStorageImpl]
  lazy val msgAndLikes: MessageAndLikesStorageImpl = wire[MessageAndLikesStorageImpl]
  lazy val messagesIndexStorage: MessageIndexStorage = wire[MessageIndexStorage]
  lazy val eventStorage: PushNotificationEventsStorage = wire[PushNotificationEventsStorageImpl]
  lazy val receivedPushStorage: ReceivedPushStorage = wire[ReceivedPushStorageImpl]

  lazy val youtubeClient      = wire[YouTubeClient]
  lazy val soundCloudClient   = wire[SoundCloudClient]
  lazy val assetClient        = wire[AssetClient]
  lazy val usersClient        = wire[UsersClient]
  lazy val convClient         = wire[ConversationsClient]
  lazy val teamClient         = wire[TeamsClient]
  lazy val pushNotificationsClient: PushNotificationsClient = new PushNotificationsClientImpl(zNetClient)
  lazy val abClient           = wire[AddressBookClient]
  lazy val gcmClient          = wire[PushTokenClient]
  lazy val typingClient       = wire[TypingClient]
  lazy val invitationClient   = wire[InvitationClient]
  lazy val giphyClient        = wire[GiphyClient]
  lazy val userSearchClient   = wire[UserSearchClient]
  lazy val connectionsClient  = wire[ConnectionsClient]
  lazy val messagesClient     = wire[MessagesClient]
  lazy val openGraphClient    = wire[OpenGraphClient]
  lazy val otrClient          = wire[com.waz.sync.client.OtrClient]
  lazy val handlesClient      = wire[HandlesClient]
  lazy val integrationsClient = wire[IntegrationsClient]

  lazy val convsContent: ConversationsContentUpdaterImpl = wire[ConversationsContentUpdaterImpl]
  lazy val messagesContent: MessagesContentUpdater = wire[MessagesContentUpdater]

  lazy val assetLoader: AssetLoader                   = wire[AssetLoaderImpl]
  lazy val imageLoader: ImageLoader                   = wire[ImageLoaderImpl]

  lazy val push: PushService                          = wire[PushServiceImpl]
  lazy val pushToken: PushTokenService                = wire[PushTokenService]
  lazy val errors                                     = wire[ErrorsServiceImpl]
  lazy val reporting                                  = new ZmsReportingService(accountId, global.reporting)
  lazy val pingInterval: PingIntervalService          = wire[PingIntervalService]
  lazy val websocket: WebSocketClientService          = wire[WebSocketClientServiceImpl]
  lazy val userSearch                                 = wire[UserSearchService]
  lazy val assetGenerator                             = wire[ImageAssetGenerator]
  lazy val assetMetaData                              = wire[com.waz.service.assets.MetaDataService]
  lazy val assets: AssetService                       = wire[AssetServiceImpl]
  lazy val users: UserService                         = wire[UserServiceImpl]
  lazy val conversations: ConversationsService        = wire[ConversationsServiceImpl]
  lazy val convsNotifier                              = wire[ConversationsNotifier]
  lazy val convOrder: ConversationOrderEventsService  = wire[ConversationOrderEventsService]
  lazy val convsUi: ConversationsUiService            = wire[ConversationsUiServiceImpl]
  lazy val convsStats: ConversationsListStateService  = wire[ConversationsListStateServiceImpl]
  lazy val teams: TeamsService                        = wire[TeamsServiceImpl]
  lazy val integrations: IntegrationsService          = wire[IntegrationsServiceImpl]
  lazy val messages: MessagesServiceImpl              = wire[MessagesServiceImpl]
  lazy val verificationUpdater                        = wire[VerificationStateUpdater]
  lazy val msgEvents: MessageEventProcessor           = wire[MessageEventProcessor]
  lazy val connection: ConnectionServiceImpl          = wire[ConnectionServiceImpl]
  lazy val calling: CallingService                    = wire[CallingService]
  lazy val contacts: ContactsServiceImpl              = wire[ContactsServiceImpl]
  lazy val typing: TypingService                      = wire[TypingService]
  lazy val invitations                                = wire[InvitationServiceImpl]
  lazy val richmedia                                  = wire[RichMediaService]
  lazy val giphy                                      = wire[GiphyService]
  lazy val youtubeMedia                               = wire[YouTubeMediaService]
  lazy val soundCloudMedia                            = wire[SoundCloudMediaService]
  lazy val otrService: OtrServiceImpl                 = wire[OtrServiceImpl]
  lazy val genericMsgs: GenericMessageService         = wire[GenericMessageService]
  lazy val reactions: ReactionsService                = wire[ReactionsService]
  lazy val notifications: NotificationService         = wire[NotificationService]
  lazy val recordAndPlay                              = wire[RecordAndPlayService]
  lazy val receipts                                   = wire[ReceiptService]
  lazy val ephemeral                                  = wire[EphemeralMessagesService]
  lazy val handlesService                             = wire[HandlesService]
  lazy val gsmService                                 = wire[GsmInterruptService]

  lazy val assetSync                                  = wire[AssetSyncHandler]
  lazy val usersearchSync                             = wire[UserSearchSyncHandler]
  lazy val usersSync                                  = wire[UsersSyncHandler]
  lazy val conversationSync                           = wire[ConversationsSyncHandler]
  lazy val teamsSync                                  = wire[TeamsSyncHandler]
  lazy val connectionsSync                            = wire[ConnectionsSyncHandler]
  lazy val addressbookSync                            = wire[AddressBookSyncHandler]
  lazy val gcmSync                                    = wire[PushTokenSyncHandler]
  lazy val typingSync                                 = wire[TypingSyncHandler]
  lazy val richmediaSync                              = wire[RichMediaSyncHandler]
  lazy val invitationSync                             = wire[InvitationSyncHandler]
  lazy val messagesSync                               = wire[MessagesSyncHandler]
  lazy val otrSync: OtrSyncHandler                    = wire[OtrSyncHandlerImpl]
  lazy val reactionsSync                              = wire[ReactionsSyncHandler]
  lazy val lastReadSync                               = wire[LastReadSyncHandler]
  lazy val clearedSync                                = wire[ClearedSyncHandler]
  lazy val openGraphSync                              = wire[OpenGraphSyncHandler]
  lazy val handlesSync                                = wire[HandlesSyncHandler]
  lazy val integrationsSync: IntegrationsSyncHandler  = wire[IntegrationsSyncHandlerImpl]
  lazy val expiringUsers                              = wire[ExpiredUsersService]

  lazy val eventPipeline: EventPipeline = new EventPipelineImpl(Vector(), eventScheduler.enqueue)

  lazy val eventScheduler = {

    new EventScheduler(
      Stage(Sequential)(
        Stage(Interleaved)(
          connection.connectionEventsStage,
          connection.contactJoinEventsStage,
          users.userUpdateEventsStage,
          users.userDeleteEventsStage,
          calling.callMessagesStage,
          teams.eventsProcessingStage,
          typing.typingEventStage,
          otrClientsService.otrClientsProcessingStage,
          pushToken.eventProcessingStage,
          Stage(Sequential)(
            convOrder.conversationOrderEventsStage,
            conversations.convStateEventProcessingStage,
            Stage(Interleaved)(
              msgEvents.messageEventProcessingStage,
              genericMsgs.eventProcessingStage
            )
          )
        ),
        notifications.notificationEventsStage,
        notifications.lastReadProcessingStage
      )
    )
  }

  // force loading of services which should run on start
  {
    conversations
    users
    expiringUsers
    gsmService

    push // connect on start

    // services listening on lifecycle verified login events
    contacts
    syncRequests

    // services listening for storage updates
    richmedia
    ephemeral
    receipts

    tempFiles
    recordAndPlay

    messagesIndexStorage

    verificationUpdater

    reporting.addStateReporter { pw =>
      Future {
        userPrefs foreachCached {
          case KeyValueData(k, v) if k.contains("time") |
                                     (Try(v.toLong).toOption.isDefined && v.length == 13) => pw.println(s"$k: ${Instant.ofEpochMilli(Try(v.toLong).getOrElse(0L))}")
          case KeyValueData(k, v) => pw.println(s"$k: $v")
        }
      }
    }
  }
}

object ZMessaging { self =>

  def accountTag[A: reflect.Manifest](accountId: AccountId): LogTag = s"${implicitly[reflect.Manifest[A]].runtimeClass.getSimpleName}#${accountId.str.take(8)}"

  private implicit val logTag: LogTag = logTagFor(ZMessaging)

  private[waz] var context: Context = _

  //var for tests - and set here so that it is globally available without the need for DI
  var clock = Clock.systemUTC()

  private var backend = BackendConfig.StagingBackend

  def useBackend(conf: BackendConfig) = {
    assert(context == null, "ZMessaging.useBackend should only be called before any ZMessagingApi instance is created, do that only once, asap in Application.onCreate")
    backend = conf
  }
  def useStagingBackend(): Unit = useBackend(BackendConfig.StagingBackend)
  def useProdBackend(): Unit = useBackend(BackendConfig.ProdBackend)

  private lazy val _global: GlobalModuleImpl = new GlobalModuleImpl(context, backend)
  private lazy val _accounts: AccountsServiceImpl = new AccountsServiceImpl(_global)
  private lazy val ui: UiModule = new UiModule(_accounts)

  //Try to avoid using these - map from the futures instead.
  private [waz] var currentUi: UiModule = _
  private [waz] var currentGlobal: GlobalModuleImpl = _
  var currentAccounts: AccountsServiceImpl = _

  def getCurrentGlobal(): GlobalModule = currentGlobal // for Java

  private lazy val globalReady = Promise[GlobalModule]()
  private lazy val accsReady = Promise[AccountsServiceImpl]()

  lazy val globalModule:    Future[GlobalModule]    = globalReady.future
  lazy val accountsService: Future[AccountsServiceImpl] = accsReady.future

  def exceptionEvent(e: Throwable, description: String): Future[Unit] = globalModule.map(_.trackingService.exception(e, description))(Threading.Background)

  def onCreate(context: Context) = {
    Threading.assertUiThread()

    if (this.currentUi == null) {
      this.context = context.getApplicationContext
      currentUi = ui
      currentGlobal = _global
      currentAccounts = _accounts

      globalReady.success(_global)
      accsReady.success(_accounts)

      JobManager.create(context).addJobCreator(new JobCreator {
        override def create(tag: String) =
          if (tag.contains(FetchJob.Tag)) new FetchJob
          else null
      })

      Threading.Background { Locales.preloadTransliterator(); ContentSearchQuery.preloadTransliteration(); } // "preload"... - this should be very fast, normally, but slows down to 10 to 20 seconds when multidexed...
    }
  }

  // should be called on low memory events
  def onTrimMemory(level: Int): CancellableFuture[Unit] = level match {
    case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN |
         ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW |
         ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL =>
      exceptionEvent(new RuntimeException(s"onTrimMemory($level)"), null)
      Threading.Background {
        currentGlobal.cache.deleteExpired()
        currentGlobal.imageCache.clear()
      }
    case _ => CancellableFuture.successful {}
  }



}
