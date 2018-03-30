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
package com.waz.api

import android.os.{Parcel, Parcelable}
import com.waz.api.OtrClient.{DeleteCallback, ResetCallback}
import com.waz.service.ZMessaging
import org.threeten.bp.Instant

trait Location {
  def getLongitude: Double
  def getLatitude: Double
  def getDisplayName: String
}

trait OtrClient extends UiObservable with Parcelable {

  def getId: String

  def getDisplayId: String

  def getLabel: String

  def getModel: String

  def getRegTime: Instant

  def getRegLocation: Location

  def getRegIpAddress: String

  def getType: OtrClientType

  def getFingerprint: UiSignal[Fingerprint]

  // was this client fingerprint verified (manually or by sync form trusted device)
  def getVerified: Verification

  def setVerified(verified: Boolean): Unit

  def delete(password: String, cb: DeleteCallback): Unit

  // updates device label, can be only called for own client
  def setLabel(label: String): Unit

  // resets otr session for this device
  // if resetting fails, callback will be called, but session refresh will still be retried later
  // WARNING: don't call that on current device (Self.getOtrClient)
  def resetSession(callback: ResetCallback): Unit
}

object OtrClient {
  trait DeleteCallback {
    def onClientDeleted(client: OtrClient): Unit
    def onDeleteFailed(error: String): Unit
  }

  trait ResetCallback {
    def onSessionReset(client: OtrClient): Unit
    def onSessionResetFailed(code: Int, message: String, label: String): Unit
  }

  val CREATOR: Parcelable.Creator[OtrClient] = new Parcelable.Creator[OtrClient] {
    override def createFromParcel(source: Parcel): OtrClient = impl.otr.OtrClient.fromParcel(source)(ZMessaging.currentUi)
    override def newArray(size: Int): Array[OtrClient] = Array.ofDim(size)
  }
}

trait Fingerprint {
  def getRawBytes: Array[Byte]
}
