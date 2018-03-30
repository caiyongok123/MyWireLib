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

import java.net.{MulticastSocket, DatagramPacket, DatagramSocket, InetAddress}

import akka.actor.{ActorRef, ActorSystem}
import akka.serialization.Serialization
import com.waz.ZLog._
import com.waz.threading.CancellableFuture
import com.waz.utils.LoggedTry
import com.waz.utils.events.{EventContext, SourceStream}

import scala.util.Try

class Multicast(system: ActorSystem, port: Int = 9987, address: InetAddress = InetAddress.getByName("239.255.42.98")) {
  import Multicast._

  lazy val sendSocket = new DatagramSocket()

  val receiver = new SourceStream[(ActorRef, String)] {

    private var receiverThread: ReceiverThread = _

    override protected def onWire(): Unit = {
      super.onWire()

      receiverThread = new ReceiverThread(address, port, this)
      receiverThread.start()
    }

    override protected def onUnwire(): Unit = {
      receiverThread.kill()
      receiverThread.join(1000)
      super.onUnwire()
    }
  }

  def send(ref: ActorRef, name: String): Unit = {
    val msg = Message(ref, name)
    println(s"Sending multicast: $ref, $name, msg: ${new String(msg)}")

    val packet = new DatagramPacket(msg, msg.length, address, port)
    sendSocket.send(packet)
  }

  def receive(implicit ev: EventContext): CancellableFuture[(ActorRef, String)] = receiver.next

  object Message {
    def apply(ref: ActorRef, name: String) = s"ActorRef:${Serialization.serializedActorPath(ref)}#$name#".getBytes

    def unapply(packet: DatagramPacket): Option[(ActorRef, String)] = {
      new String(packet.getData, "utf-8") match {
        case MessagePattern(ref, name) =>
          LoggedTry(DeserializeActorRefExtension(system).deserialize(ref)).toOption.map((_, name))
        case _ => None
      }
    }
  }

  class ReceiverThread(address: InetAddress, port: Int, stream: SourceStream[(ActorRef, String)]) extends Thread {

    private lazy val socket = new MulticastSocket(port)

    @volatile private var running = true
    setDaemon(true)

    def kill() = {
      running = false
      interrupt()
    }

    override def run(): Unit = if (running) {
      val buf = new Array[Byte](1024)

      try {
        socket.joinGroup(address)
        while (running) {
          val packet = new DatagramPacket(buf, buf.length)
          socket.receive(packet)
          println(s"got packet from: ${packet.getAddress}")
          packet match {
            case Message(ref, name) => stream ! (ref, name)
            case p => println(s"received unknown packet: ${new String(packet.getData)}")
          }
        }
      } catch {
        case e: InterruptedException =>
        case e: Throwable => e.printStackTrace()
      } finally {
        Try(socket.leaveGroup(address))
        Try(socket.close())
      }
    }
  }
}

object Multicast {
  private implicit val tag: LogTag = logTagFor[Multicast]
  private val MessagePattern = """ActorRef:(.*)#(.*)#.*""".r
}
