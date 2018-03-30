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
package com.waz.service.push

import java.io.IOException

import com.waz.api.NetworkMode
import com.waz.content.GlobalPreferences.PushEnabledKey
import com.waz.content.{AccountsStorage, GlobalPreferences}
import com.waz.model._
import com.waz.service.AccountsService.{InBackground, InForeground}
import com.waz.service.{BackendConfig, NetworkModeService, UiLifeCycle}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.testutils.{TestBackoff, TestGlobalPreferences}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.utils.wrappers.GoogleApi
import com.waz.ZLog.ImplicitTag._

import scala.concurrent.Future

class PushTokenServiceSpec extends AndroidFreeSpec {

  val google         = mock[GoogleApi]
  val lifecycle      = mock[UiLifeCycle]
  val accStorage     = mock[AccountsStorage]
  val networkService = mock[NetworkModeService]
  val prefs          = new TestGlobalPreferences()

  val sync        = mock[SyncServiceHandle]
  val pushEnabled         = prefs.preference(PushEnabledKey)
  await(pushEnabled := true)

  val currentToken        = prefs.preference(GlobalPreferences.PushToken)
  val resetToken          = prefs.preference(GlobalPreferences.ResetPushToken)
  await(resetToken := false)

  val googlePlayAvailable = Signal(false)
  val accountSignal       = Signal[AccountData]()

  val loggedInAccounts    = Signal(Set.empty[AccountData])

  val networkMode         = Signal(NetworkMode.WIFI)

  def accountData(accountId: AccountId, token: Option[PushToken]): AccountData = AccountData(accountId, registeredPush = token)
  def accountData(accountId: AccountId, token: PushToken): AccountData = accountData(accountId, Some(token))

  feature("Token generation") {
    scenario("Fetches token on init if GCM available") {
      val token = PushToken("token")
      (google.getPushToken _).expects().returning(token)
      val service = initTokenService()

      googlePlayAvailable ! true
      result(service.currentToken.signal.filter(_.contains(token)).head)
    }

    scenario("Remove Push Token event should create new token and delete all previous tokens") {

      val oldToken = PushToken("oldToken")
      val newToken = PushToken("newToken")
      currentToken := Some(oldToken)
      googlePlayAvailable ! true

      (google.getPushToken _).expects().once().returning(newToken)
      //This needs to be called
      (google.deleteAllPushTokens _).expects().once()

      val service = initTokenService()

      //wait for first token to be set
      result(service.currentToken.signal.filter(_.contains(oldToken)).head)
      //delete first token in response to BE event
      service.eventProcessingStage(RConvId(), Vector(PushTokenRemoveEvent(oldToken, "sender", Some("client"))))
      //new token should be set
      result(service.currentToken.signal.filter(_.contains(newToken)).head)
    }

    scenario("Failing push token generation should continually retry on IOException, if there is a network connection") {

      googlePlayAvailable ! true

      /*
      The network starts offline - we want the global token service to try registering once - this is because I'm not entirely sure
      what happens inside the google play services. Maybe sometimes, a token can be available if there is no internet, no idea. Anyway,
      try once, and then if it fails with an IOException - it's most likely because there is no connection, so then wait for the device
      to come back online, then try again
      */
      networkMode ! NetworkMode.OFFLINE

      val newToken = PushToken("new_token")
      var calls = 0
      (google.getPushToken _).expects.twice().onCall { _ =>
        calls += 1
        calls match {
          case 1 => throw new IOException()
          case 2 => newToken
          case _ => fail("unexpected number of calls")
        }
      }

      PushTokenService.ResetBackoff = TestBackoff()

      val (global, _) = initTokenServicesWithGlobal()

      global.setNewToken()

      awaitAllTasks
      calls shouldEqual 1
      networkMode ! NetworkMode._4G
      result(currentToken.signal.filter(_.contains(newToken)).head)

    }

    scenario("Multiple simultaneous calls to set token only generate one while still processing") {

      googlePlayAvailable ! true

      await(currentToken := Some(PushToken("oldToken")))

      val token1 = PushToken("token1")
      val token2 = PushToken("token2")
      (google.getPushToken _).expects.returning(token1)
      (google.getPushToken _).expects.returning(token2)

      val (global, _) = initTokenServicesWithGlobal()

      //it's hard to only call `setNewToken` while currently setting one for the sake of the test, so just fire two calls
      //quickly, and hope the second one always slips in before the first one is set.
      val res = global.setNewToken()
      global.setNewToken()
      result(currentToken.signal.filter(_.contains(token1)).head)
      await(res)

      //Repeat to make sure the future is freed up
      global.setNewToken()
      global.setNewToken()
      result(currentToken.signal.filter(_.contains(token2)).head)
    }
  }

  feature("Token registration") {
    scenario("If current user does not have matching registeredPush token, remove the old token and register the new one with our BE") {

      val oldToken = PushToken("oldToken")
      val newToken = PushToken("token")

      loggedInAccounts ! Set(accountData(account1Id, oldToken))

      currentToken := Some(newToken)
      googlePlayAvailable ! true

      lazy val service = initTokenService()

      (sync.deletePushToken _).expects(oldToken).once().returning(Future.successful(SyncId()))

      (sync.registerPush _).expects(newToken).once().onCall { _: PushToken =>
        Future {
          service.onTokenRegistered(newToken)
          SyncId()
        } (Threading.Background)
      }

      result(service.currentToken.signal.filter(_.contains(newToken)).head)
      result(loggedInAccounts.filter(_.exists(acc => acc.id == account1Id && acc.registeredPush.contains(newToken))).head)
    }

    scenario("Instance Id token refresh should trigger re-registration for current user") {
      val oldToken = PushToken("oldToken")
      val newToken = PushToken("newToken")

      loggedInAccounts ! Set(accountData(account1Id, oldToken))
      googlePlayAvailable ! true
      currentToken := Some(oldToken)

      lazy val (globalToken, Seq(service)) = initTokenServicesWithGlobal()

      (sync.deletePushToken _).expects(oldToken).once().returning(Future.successful(SyncId()))
      (sync.registerPush _).expects(newToken).once().onCall { _: PushToken =>
        Future {
          service.onTokenRegistered(newToken)
          SyncId()
        } (Threading.Background)
      }
      (google.getPushToken _).expects().returning(newToken)

      result(service.currentToken.signal.filter(_.contains(oldToken)).head)
      result(loggedInAccounts.filter(_.exists(acc => acc.id == account1Id && acc.registeredPush.contains(oldToken))).head)

      globalToken.setNewToken() //InstanceIDService triggers new token

      result(service.currentToken.signal.filter(_.contains(newToken)).head)

      result(accountSignal(account1Id).filter(_.registeredPush.contains(newToken)).head)
    }

    scenario("After user is logged out, clearing their current push token should NOT trigger new registration") {
      val token = PushToken("token")

      loggedInAccounts ! Set(accountData(account1Id, token))
      googlePlayAvailable ! true
      currentToken := Some(token)
      updateAccountState(account1Id, InBackground)

      (google.getPushToken _).expects().never()
      (sync.registerPush _).expects(*).never()

      lazy val service = initTokenService()

      result(service.pushActive.filter(_ == true).head)

      loggedInAccounts ! Set.empty //user is logged out
      updateAccountState(account1Id, InBackground)

      result(service.pushActive.filter(_ == false).head)

      /**
        * There can be a couple of instances of zms (and therefore the push token service) available.
        * So lets make sure we only register the account assigned to our instance. Logging in then with another account
        * shouldn't change the state of this instance.
        */
      loggedInAccounts ! Set(AccountData())

      awaitAllTasks
      result(service.pushActive.filter(_ == false).head)
    }
  }

  feature("Push active") {

    scenario("Push should be active if push is enabled and inactive if not") {
      val token = PushToken("token")
      loggedInAccounts ! Set(accountData(account1Id, token))
      googlePlayAvailable ! true
      currentToken := Some(token)
      updateAccountState(account1Id, InBackground)

      val service = initTokenService()

      result(service.pushActive.filter(_ == true).head)

      pushEnabled := false //set inactive
      result(service.pushActive.filter(_ == false).head)
    }

    scenario("Push should be active if app is in background and inactive if not") {
      val token = PushToken("token")
      loggedInAccounts ! Set(accountData(account1Id, token))
      googlePlayAvailable ! true
      updateAccountState(account1Id, InForeground) //websocket should be open
      currentToken := Some(token)

      val service = initTokenService()

      result(service.pushActive.filter(_ == false).head)

      updateAccountState(account1Id, InBackground) //websocket should be off - use push again
      result(service.pushActive.filter(_ == true).head)
    }

    scenario("Push should be inactive if play services are unavailable") {
      val token = PushToken("token")
      loggedInAccounts ! Set(accountData(account1Id, token))
      googlePlayAvailable ! true
      updateAccountState(account1Id, InBackground)
      currentToken := Some(token)

      val service = initTokenService()

      result(service.pushActive.filter(_ == true).head)

      googlePlayAvailable ! false //Set true and then false again to cause the signal to change. Maybe the user disables play services?
      result(service.pushActive.filter(_ == false).head)
    }

    scenario("Push should be inactive if user is not currently registered") {

      val token = PushToken("token")
      loggedInAccounts ! Set(accountData(account1Id, None))
      googlePlayAvailable ! true
      currentToken := Some(token)

      (sync.registerPush _).expects(*).once().returning(Future.successful(SyncId()))
      val service = initTokenService()
      result(service.pushActive.filter(_ == false).head)
    }
  }

  feature("multiple accounts") {
    scenario("Reset global token") {
      val oldToken = PushToken("oldToken")
      val newToken = PushToken("newToken")

      currentToken := Some(oldToken)
      googlePlayAvailable ! true
      await(currentToken.signal.filter(_.contains(oldToken)).head)

      (google.getPushToken _).expects().once().returning(newToken)
      //This needs to be called
      (google.deleteAllPushTokens _).expects().once()

      val (globalToken, _) = initTokenServicesWithGlobal()

      globalToken.resetGlobalToken()
      await(currentToken.signal.filter(_.contains(newToken)).head)
    }

    scenario("Reset global token via reset preference") {
      val oldToken = PushToken("oldToken")
      val newToken = PushToken("newToken")

      currentToken := Some(oldToken)
      googlePlayAvailable ! true
      await(currentToken.signal.filter(_.contains(oldToken)).head)

      (google.getPushToken _).expects().once().returning(newToken)
      //This needs to be called
      (google.deleteAllPushTokens _).expects().once()

      val (globalToken, _) = initTokenServicesWithGlobal()

      resetToken := true
      await(currentToken.signal.filter(_.contains(newToken)).head)
      await(resetToken.signal.filter(_ == false).head)
    }

    scenario("Add second account and it should get the current global token") {

      val token = PushToken("oldToken")

      //Start off
      val account1 = accountData(account1Id, Some(token))
      val account2 = accountData(AccountId(), None)

      loggedInAccounts ! Set(account1, account2)
      updateAccountState(account2.id, InBackground)
      googlePlayAvailable ! true
      currentToken := Some(token)

      val sync2 = mock[SyncServiceHandle]

      lazy val (_, Seq(service1, service2)) = initTokenServicesWithGlobal(Seq(account1Id, account2.id), Seq(sync, sync2))

      (sync2.registerPush _).expects(token).once().onCall { _: PushToken =>
        Future {
          service2.onTokenRegistered(token)
          SyncId()
        } (Threading.Background)
      }

      //trigger lazy vals
      service1
      service2

      result(accountSignal(account1Id).filter(_.registeredPush.contains(token)).head)
      result(accountSignal(account2.id).filter(_.registeredPush.contains(token)).head)
    }
  }

  def accountSignal(id: AccountId) = loggedInAccounts.map(_.find(_.id == id)).collect { case Some(acc) => acc }

  def initTokenService(accountId: AccountId = account1Id, sync: SyncServiceHandle = sync) = initTokenServicesWithGlobal(Seq(accountId), Seq(sync))._2.head

  def initTokenServicesWithGlobal(accountIds: Seq[AccountId] = Seq(account1Id), syncs: Seq[SyncServiceHandle] = Seq(sync)) = {

    (google.isGooglePlayServicesAvailable _).expects().anyNumberOfTimes().returning(googlePlayAvailable)
    (networkService.networkMode _).expects.anyNumberOfTimes().returning(networkMode)
    val global = new GlobalTokenService(google, prefs, networkService)

    (accStorage.signal _).expects(*).anyNumberOfTimes().onCall { id: AccountId =>
      loggedInAccounts.map(_.find(_.id == id)).collect { case Some(acc) => acc }
    }

    (accStorage.update _).expects(*, *).anyNumberOfTimes().onCall { (id, f) =>
      Future.successful {
        val account = loggedInAccounts.currentValue("").flatMap(_.find(_.id == id))

        returning(account.fold(Option.empty[(AccountData, AccountData)])(p => Some((p, f(p))))) {
          case Some((_, updated)) => loggedInAccounts.mutate { accs =>
            val removed = accs - accs.find(_.id == updated.id).get
            val res = removed + updated
            res
          }
          case _ =>
        }
      }
    }

    (global, accountIds.zip(syncs).map { case (id, sync) =>
      new PushTokenService(google, BackendConfig.StagingBackend, global, prefs, id, accounts, accStorage, sync)
    })
  }
}
