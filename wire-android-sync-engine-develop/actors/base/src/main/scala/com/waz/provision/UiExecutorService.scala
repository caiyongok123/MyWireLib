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

import java.util
import java.util.Collections
import java.util.concurrent.{ThreadFactory, ExecutorService, TimeUnit, AbstractExecutorService}

import akka.dispatch.{ExecutorServiceFactory, ExecutorServiceConfigurator, DispatcherPrerequisites}
import com.typesafe.config.Config
import com.waz.threading.Threading

object UiExecutorService extends AbstractExecutorService {
  override def shutdown(): Unit = ()
  override def shutdownNow(): util.List[Runnable] = Collections.emptyList[Runnable]
  override def isShutdown: Boolean = false
  override def isTerminated: Boolean = false
  override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true

  override def execute(command: Runnable): Unit = Threading.Ui execute command
}

class UiExecutorServiceConfigurator(config: Config, prerequisites: DispatcherPrerequisites) extends ExecutorServiceConfigurator(config, prerequisites) {
  private val f = new ExecutorServiceFactory {
    def createExecutorService: ExecutorService = UiExecutorService
  }

  def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = f
}
