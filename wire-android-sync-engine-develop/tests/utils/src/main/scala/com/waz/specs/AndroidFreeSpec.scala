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
package com.waz.specs

import java.util.concurrent.{Executors, ThreadFactory, TimeoutException}

import com.waz.ZLog.{LogTag, error}
import com.waz.log.{InternalLog, SystemLogOutput}
import com.waz.model.AccountId
import com.waz.service.AccountsService.{AccountState, InForeground, LoggedOut}
import com.waz.service.tracking.TrackingService
import com.waz.service.{AccountContext, AccountsService, ZMessaging}
import com.waz.testutils.TestClock
import com.waz.threading.Threading.{Background, IO, ImageDispatcher, Ui}
import com.waz.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import com.waz.utils._
import com.waz.utils.events.Signal
import com.waz.utils.wrappers.{Intent, JVMIntentUtil, JavaURIUtil, URI, _}
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import org.threeten.bp.Instant

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

abstract class AndroidFreeSpec extends FeatureSpec with BeforeAndAfterAll with BeforeAndAfterEach with Matchers with MockFactory with OneInstancePerTest { this: Suite =>

  import AndroidFreeSpec._

  val clock = AndroidFreeSpec.clock

  val account1Id  = AccountId("account1")
  val accounts    = mock[AccountsService]
  val tracking    = mock[TrackingService]

  (tracking.exception(_: Throwable, _: String, _: Option[AccountId])(_: LogTag)).expects(*, *, *, *).anyNumberOfTimes().onCall { (t, description, _, tag) =>
    t match {
      case e: exceptions.TestFailedException => swallowedFailure = Some(e)
      case _ => error(s"Exception sent to HockeyApp: $description", t)(tag)
    }
  }

  val accountStates = Signal[Map[AccountId, AccountState]](Map(account1Id -> InForeground))

  (accounts.accountState _).expects(*).anyNumberOfTimes().onCall { id: AccountId =>
    accountStates.map(_.getOrElse(id, LoggedOut))
  }

  def updateAccountState(id: AccountId, state: AccountState) =
    accountStates.mutate(_ + (id -> state))

  implicit val accountContext = new AccountContext(account1Id, accounts)

  override protected def beforeEach() = {
    super.beforeEach()
    clock.reset()
  }

  //Ensures that Android wrappers are assigned with a non-Android implementation so that tests can run on the JVM
  override protected def beforeAll() = {
    URI.setUtil(JavaURIUtil)

    DB.setUtil(new DBUtil {
      override def ContentValues(): DBContentValues = DBContentValuesMap()
    })

    isTest = true

    ZMessaging.clock = clock

    InternalLog.reset()
    InternalLog.add(new SystemLogOutput)

    Intent.setUtil(JVMIntentUtil)

    Threading.setUi(new SerialDispatchQueue({
      Threading.executionContext(Executors.newSingleThreadExecutor(new ThreadFactory {
        override def newThread(r: Runnable) = {
          new Thread(r, Threading.testUiThreadName)
        }
      }))
    }, Threading.testUiThreadName))
  }

  /**
    * Here we wait for all threads to finish their current tasks as to allow each test to run with a clean threading profile.
    * If there are still tasks pending, we fail, as it likely means an error somewhere.
    */
  override def withFixture(test: NoArgTest) = {
    val res = super.withFixture(test)
    res match {
      case Succeeded =>
        if (!tasksCompletedAfterWait)
          Failed(new TimeoutException(s"Background tasks continued running after test for ${DefaultTimeout.toSeconds} seconds: Potential threading issue!"))
        else if (swallowedFailure.isDefined) {
          returning(Failed(swallowedFailure.get)) { _ =>
            swallowedFailure = None
          }
        }
        else
          Succeeded
      case outcome => outcome
    }
  }

  def await[A](future: Future[A])(implicit defaultDuration: FiniteDuration = DefaultTimeout): A = result(future)(defaultDuration)

  def result[A](future: Future[A])(implicit defaultDuration: FiniteDuration = DefaultTimeout): A = Await.result(future, defaultDuration)

  /**
    * Very useful for checking that something DOESN'T happen (e.g., ensure that a signal doesn't get updated after
    * performing a series of actions)
    */
  def awaitAllTasks(implicit timeout: FiniteDuration = DefaultTimeout) = {
    if (!tasksCompletedAfterWait) fail(new TimeoutException(s"Background tasks didn't complete in ${timeout.toSeconds} seconds"))
  }

  def tasksRemaining = Seq(IO, ImageDispatcher, Ui, Background).exists(_.hasRemainingTasks)

  private def tasksCompletedAfterWait(implicit timeout: FiniteDuration = DefaultTimeout) = {
    val start = Instant.now
    while(tasksRemaining && Instant.now().isBefore(start + timeout)) Thread.sleep(10)
    !tasksRemaining
  }

  def withDelay[T](body: => T, delay: FiniteDuration = 100.millis)(implicit ec: ExecutionContext) = CancellableFuture.delayed(delay)(body)
}

object AndroidFreeSpec {

  val clock = TestClock()

  val DefaultTimeout = 5.seconds
  @volatile private var swallowedFailure = Option.empty[exceptions.TestFailedException]
}
