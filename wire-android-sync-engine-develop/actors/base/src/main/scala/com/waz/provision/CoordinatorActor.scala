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

import akka.actor._
import com.waz.utils.events.{EventContext, EventStream}

import scala.collection.mutable
import scala.util.Success

class CoordinatorActor extends Actor with ActorLogging {

  import ActorMessage._
  import EventContext.Implicits.global
  import scala.concurrent.ExecutionContext.Implicits.{global => gec}

  lazy val multicast = new Multicast(context.system)

  val processes = mutable.Map.empty[ActorRef, String]
  val registered = EventStream[(ActorRef, String)]()

  private def process(name: String) = processes.find(_._2 == name)

  override def receive = LoggedReceive {
    case RegisterProcess(processName) =>
      context.watch(sender())
      processes += (sender() -> processName)
      registered ! (sender() -> processName)

    case WaitUntilRegistered(processName) =>
      process(processName) match {
        case Some((ref, _)) =>
          log.info(s"The process $processName is registered and waiting")
          sender() ! ref

        case None =>
          val senderRef = sender()

          val sub = multicast.receiver.filter(_._2 == processName) { case (ref, _) =>
            ref ! ActorIdentity(0, Some(self))
          }

          registered.filter(_._2 == processName).next onComplete {
            case Success((ref, _)) =>
              sub.destroy()
              senderRef ! ref
            case res =>
              sub.destroy()
              senderRef ! Failed(res.toString)
          }
      }

    case SpawnRemoteDevice(processName, deviceName) =>
      process(processName) match {
        case Some((ref, _)) =>
          println(s"Remote process $processName exists, forwarding request to spawn a device called $deviceName")
          ref.forward(SpawnRemoteDevice(processName, deviceName))
        case None =>
          sender() ! Failed(s"There are no remote processes matching the name $processName")
      }

    case Terminated(actorRef) =>
      log.info(s"Process ${processes(actorRef)} has disconnected")
      processes -= actorRef

    case ListRemotes =>
      log.info(processes.toString())
      sender() ! ListRemotes(processes.toMap)

    case Command(target, msg) =>
      process(target) match {
        case Some((ref, _)) => ref forward msg
        case None => sender() ! Failed("No registered processes that match target")
      }

    case Echo(msg, _) =>
      sender() ! Echo(msg, "coordinator")

    case ReleaseRemotes =>
      processes.keys.foreach(_ ! ReleaseProcess)
      processes.clear()
  }
}
