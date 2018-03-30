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
package com.waz.api.impl

import com.waz.api.NetworkMode
import com.waz.ui.{SignalLoading, UiModule}
import com.waz.utils.events.Signal

class ConnectionIndicator(implicit ui: UiModule) extends com.waz.api.ConnectionIndicator with UiObservable with SignalLoading {

  private var webSocketConnected = false
  private var connectionError = false
  private var networkMode = NetworkMode.UNKNOWN

  signalLoader(ui.global.network.networkMode) { mode =>
    if (networkMode != mode) {
      networkMode = mode
      notifyChanged()
    }
  }

  addLoader { zms => Signal(zms.websocket.connected, zms.websocket.connectionError) } { case (connected, error) =>
    webSocketConnected = connected
    connectionError = error
    notifyChanged()
  }

  override def isConnectionError = connectionError

  override def isWebSocketConnected = webSocketConnected

  override def getNetworkMode = networkMode
}
