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
package com.waz.sync.queue

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.api.SyncState
import com.waz.api.impl.ErrorResponse
import com.waz.model.SyncId
import com.waz.model.sync.{SerialExecutionWithinConversation, SyncJob, SyncRequest}
import com.waz.service.NetworkModeService
import com.waz.service.tracking.TrackingService
import com.waz.sync.{SyncHandler, SyncRequestServiceImpl, SyncResult}
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils._

import scala.concurrent.duration._
import scala.concurrent.{Future, TimeoutException}
import scala.util.Failure
import scala.util.control.NoStackTrace

class SyncExecutor(scheduler:   SyncScheduler,
                   content:     SyncContentUpdater,
                   network:     NetworkModeService,
                   handler: =>  SyncHandler,
                   tracking:    TrackingService) {
  private implicit val dispatcher = new SerialDispatchQueue(name = "SyncExecutorQueue")

  def apply(job: SyncJob): Future[SyncResult] = job.request match {
    case r: SerialExecutionWithinConversation =>
      scheduler.withConv(job, r.convId) { convLock =>
        execute(job.id) {
          case r: SerialExecutionWithinConversation => handler(r, convLock)
          case req => throw new RuntimeException(s"WTF - SyncJob request type has changed to: $req")
        }
      }
    case _ => execute(job.id)(handler(_))
  }

  private def execute(id: SyncId)(sync: SyncRequest => Future[SyncResult]): Future[SyncResult] = {

    def withJob(f: SyncJob => Future[SyncResult]) =
      content.getSyncJob(id) flatMap {
        case Some(job) => f(job)
        case None =>
          Future.successful(SyncResult(ErrorResponse.internalError(s"No sync job found with id: $id")))
      }

    withJob { job =>
      scheduler.awaitPreconditions(job) {
        withJob { execute(_, sync) }
      } flatMap {
        case SyncResult.Failure(_, true) => execute(id)(sync)
        case res => Future.successful(res)
      }
    }
  }

  private def execute(job: SyncJob, sync: SyncRequest => Future[SyncResult]): Future[SyncResult] = {
    verbose(s"executeJob: $job")

    if (job.optional && job.timeout > 0 && job.timeout < System.currentTimeMillis()) {
      info(s"Optional request timeout elapsed, dropping: $job")
      content.removeSyncJob(job.id) map { _ => SyncResult.Success}
    } else {
      val future = content.updateSyncJob(job.id)(job => job.copy(attempts = job.attempts + 1, state = SyncState.SYNCING, error = None, offline = !network.isOnlineMode))
        .flatMap {
          case None => Future.successful(SyncResult(ErrorResponse.internalError(s"Could not update job: $job")))
          case Some(updated) =>
            (sync(updated.request) recover {
              case e: Throwable =>
                error(s"syncHandler($updated) failed with unexpected error", e)
                SyncResult(ErrorResponse.internalError(s"syncHandler($updated) failed with unexpected error: ${e.getMessage}"))
            }) flatMap { res =>
              processSyncResult(updated, res)
            }
        }

      // this is only to check for any long running sync requests, which could mean very serious problem
      CancellableFuture.lift(future).withTimeout(10.minutes).onComplete {
        case Failure(e: TimeoutException) => tracking.exception(new RuntimeException(s"SyncRequest: ${job.request.cmd} runs for over 10 minutes", e), s"SyncRequest taking too long: $job")
        case _ =>
      }
      future
    }
  }

  private def processSyncResult(job: SyncJob, result: SyncResult): Future[SyncResult] = {

    def delete() = content.removeSyncJob(job.id) map { _ => result }

    def drop() = content.removeSyncJob(job.id) map { _ => result }

    result match {
      case SyncResult.Success =>
        debug(s"SyncRequest: $job completed successfully")
        delete()
      case SyncResult.Failure(error, false) =>
        warn(s"SyncRequest: $job, failed permanently with error: $error")
        if (error.exists(_.shouldReportError)) {
          tracking.exception(new RuntimeException(s"Request ${job.request.cmd} failed permanently with error: ${error.map(_.code)}") with NoStackTrace, s"Got fatal error, dropping request: $job\n error: $error")
        }
        drop()
      case SyncResult.Failure(error, true) =>
        warn(s"SyncRequest: $job, failed with error: $error")
        if (job.attempts > SyncRequestServiceImpl.MaxSyncAttempts) {
          tracking.exception(new RuntimeException(s"Request ${job.request.cmd} failed with error: ${error.map(_.code)}") with NoStackTrace, s"MaxSyncAttempts exceeded, dropping request: $job\n error: $error")
          drop()
        } else if (job.timeout > 0 && job.timeout < job.startTime) {
          tracking.exception(new RuntimeException(s"Request ${job.request.cmd} timed-out with error: ${error.map(_.code)}") with NoStackTrace, s"Request timeout elapsed, dropping request: $job\n error: $error")
          drop()
        } else {
          verbose(s"will schedule retry for: $job, $job")
          val nextTryTime = System.currentTimeMillis() + SyncExecutor.failureDelay(job)
          content.updateSyncJob(job.id)(job => job.copy(state = SyncState.FAILED, startTime = nextTryTime, error = error, offline = job.offline || !network.isOnlineMode)) map { _ =>
            result
          }
        }
    }
  }
}

object SyncExecutor {
  val RequestRetryBackoff = new ExponentialBackoff(5.seconds, 1.day)
  val ConvRequestRetryBackoff = new ExponentialBackoff(5.seconds, 1.hour)

  def failureDelay(job: SyncJob) = job.request match {
    case _: SerialExecutionWithinConversation => ConvRequestRetryBackoff.delay(job.attempts).toMillis
    case _ => RequestRetryBackoff.delay(job.attempts).toMillis
  }
}
