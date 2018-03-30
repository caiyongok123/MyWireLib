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
package com.waz.test

import com.koushikdutta.async.callback.{CompletedCallback, DataCallback}
import com.koushikdutta.async.http.AsyncHttpClient.WebSocketConnectCallback
import com.koushikdutta.async.http.{AsyncHttpClient, AsyncHttpGet, WebSocket}
import com.koushikdutta.async.{AsyncServer, ByteBufferList, DataEmitter}
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream
import com.waz.RobolectricUtils
import org.scalatest.{BeforeAndAfter, FeatureSpecLike, Matchers, RobolectricTests}
import scala.concurrent.duration._

class EchoServerTest extends FeatureSpecLike with Matchers with BeforeAndAfter with RobolectricTests with RobolectricUtils {

  feature("echo server") {

    scenario("connect with async client, send message and wait for echo") {
      val client = new AsyncHttpClient(new AsyncServer)
      var webSocket = None : Option[WebSocket]

      val req = new AsyncHttpGet("http://localhost:8083")
      req.setHeader("Accept-Encoding", "identity")

      client.websocket(req, null, new WebSocketConnectCallback {
        override def onCompleted(ex: Exception, socket: WebSocket): Unit = {
          println(s"WebSocket request finished, ex: $ex, socket: $socket")
          webSocket = Option(socket)
        }
      })

      withDelay {
        webSocket should be('defined)
      }

      val socket = webSocket.get
      var stringResponse = None: Option[String]
      val bytesResponse = new ByteOutputStream()
      socket.setStringCallback(new WebSocket.StringCallback {
        override def onStringAvailable(s: String): Unit = {
          stringResponse = Some(s)
        }
      })
      socket.setDataCallback(new DataCallback {
        override def onDataAvailable(emitter: DataEmitter, bb: ByteBufferList): Unit = bytesResponse.write(bb.getAllByteArray)
      })

      socket.send("test")

      withDelay(stringResponse shouldEqual Some("test"))

      socket.send("test bytes".getBytes)

      withDelay(new String(bytesResponse.getBytes).trim shouldEqual "test bytes")

      webSocket.foreach(_.close())
    }

    scenario("disable/enable commands") {
      val client = new AsyncHttpClient(new AsyncServer)
      var webSocket = None : Option[WebSocket]

      val req = new AsyncHttpGet("http://localhost:8083")
      req.setHeader("Accept-Encoding", "identity")

      client.websocket(req, null, new WebSocketConnectCallback {
        override def onCompleted(ex: Exception, socket: WebSocket): Unit = {
          println(s"WebSocket request finished, ex: $ex, socket: $socket")
          webSocket = Option(socket)
        }
      })

      withDelay {
        webSocket should be('defined)
      }

      val socket = webSocket.get
      var stringResponse = None: Option[String]
      socket.setStringCallback(new WebSocket.StringCallback {
        override def onStringAvailable(s: String): Unit = {
          stringResponse = Some(s)
        }
      })

      socket.send("disable")
      socket.send("test")

      withDelay(stringResponse shouldEqual None)

      socket.send("enable")
      withDelay(stringResponse shouldEqual Some("enable"))
      socket.send("test")
      withDelay(stringResponse shouldEqual Some("test"))

      webSocket.foreach(_.close())
    }

    scenario("restart command") {
      val client = new AsyncHttpClient(new AsyncServer)
      var webSocket = None : Option[WebSocket]

      val req = new AsyncHttpGet("http://localhost:8083")
      req.setHeader("Accept-Encoding", "identity")

      client.websocket(req, null, new WebSocketConnectCallback {
        override def onCompleted(ex: Exception, socket: WebSocket): Unit = {
          println(s"WebSocket request finished, ex: $ex, socket: $socket")
          webSocket = Option(socket)
        }
      })

      withDelay {
        webSocket should be('defined)
      }

      val socket = webSocket.get
      var closed = false
      socket.setClosedCallback(new CompletedCallback {
        override def onCompleted(ex: Exception): Unit = closed = true
      })

      socket.send("restart")
      withDelay(closed shouldEqual true)

      webSocket.foreach(_.close())
    }

    scenario("closeLater command without delay") {
      val client = new AsyncHttpClient(new AsyncServer)
      @volatile var webSocket = None: Option[WebSocket]

      val req = new AsyncHttpGet("http://localhost:8083")
      req.setHeader("Accept-Encoding", "identity")

      client.websocket(req, null, new WebSocketConnectCallback {
        override def onCompleted(ex: Exception, socket: WebSocket): Unit = {
          println(s"WebSocket request finished, ex: $ex, socket: $socket")
          webSocket = Option(socket)
        }
      })

      withDelay {
        webSocket should be('defined)
      }

      val socket = webSocket.get
      @volatile var closed = false
      socket.setClosedCallback(new CompletedCallback {
        override def onCompleted(ex: Exception): Unit = closed = true
      })

      socket.send("closeAfter 0")
      withDelay {
        closed shouldEqual true
      }(timeout = 100.milliseconds)

      webSocket.foreach(_.close())
    }

    scenario("closeLater command with delay") {
      val client = new AsyncHttpClient(new AsyncServer)
      @volatile var webSocket = None: Option[WebSocket]

      val req = new AsyncHttpGet("http://localhost:8083")
      req.setHeader("Accept-Encoding", "identity")

      client.websocket(req, null, new WebSocketConnectCallback {
        override def onCompleted(ex: Exception, socket: WebSocket): Unit = {
          println(s"WebSocket request finished, ex: $ex, socket: $socket")
          webSocket = Option(socket)
        }
      })

      withDelay {
        webSocket should be('defined)
      }

      val socket = webSocket.get
      @volatile var closed = false
      socket.setClosedCallback(new CompletedCallback {
        override def onCompleted(ex: Exception): Unit = closed = true
      })

      socket.send("closeAfter 500")
      withDelay {
        closed shouldEqual false
      }(timeout = 100.milliseconds)

      withDelay {
        closed shouldEqual true
      }

      webSocket.foreach(_.close())
    }
  }
}
