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

import android.os.Parcel
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api
import com.waz.api.Message.{Status, Type}
import com.waz.api.{EphemeralExpiration, UpdateListener}
import com.waz.model._
import com.waz.service.media.GoogleMapsMediaService
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.ui._
import com.waz.utils._
import com.waz.utils.events.Signal

import scala.collection.breakOut

class Message(val id: MessageId, var data: MessageData, var likes: IndexedSeq[UserId], var likedBySelf: Boolean)(implicit context: UiModule) extends api.Message with SignalLoading with UiObservable {

  private val convId = if (data == MessageData.Empty) Signal[ConvId]() else Signal(data.convId)
  private var lastMessageFromSelf = false
  private var lastMessageFromOther = false

  def this(msg: MessageAndLikes)(context: UiModule) = this(msg.message.id, msg.message, msg.likes, msg.likedBySelf)(context)
  def this(id: MessageId)(context: UiModule) = this(id, MessageData.Empty, Vector.empty, false)(context)

  reload() // always reload because data from constructor might always be outdated already
  addLoader(zms => convId.flatMap(c => zms.messagesStorage.lastMessageFromSelfAndFromOther(c)), (Option.empty[MessageData], Option.empty[MessageData])) { case (fromSelf, fromOther) =>
    val isMostRecentFromSelf = fromSelf.exists(_.id == id)
    val isMostRecentFromOther = fromOther.exists(_.id == id)
    if (isMostRecentFromSelf != lastMessageFromSelf || isMostRecentFromOther != lastMessageFromOther) {
      lastMessageFromSelf = isMostRecentFromSelf
      lastMessageFromOther = isMostRecentFromOther
      notifyChanged()
    }
  }

  def reload() = context.zms.flatMapFuture(_.msgAndLikes.getMessageAndLikes(id))(Threading.Background).map { m => set(m.getOrElse(MessageAndLikes.Deleted)) } (Threading.Ui)

  def set(msg: MessageAndLikes): Unit = if (msg.message != data || msg.likes != likes || msg.likedBySelf != likedBySelf) {
    data = msg.message
    likes = msg.likes
    likedBySelf = msg.likedBySelf
    notifyChanged()
    convId ! data.convId
    notifyEphemeralRead()
  }


  override def addUpdateListener(listener: UpdateListener): Unit = {
    super.addUpdateListener(listener)
    notifyEphemeralRead()
  }

  private def notifyEphemeralRead() =
    if (data.ephemeral != EphemeralExpiration.NONE && data.expiryTime.isEmpty && getListenersCount > 0)
      context.zms { _.ephemeral.onMessageRead(id) }

  private def content = if (data.content.isEmpty) MessageContent.Empty else data.content.head

  override def isEphemeral = data.ephemeral != EphemeralExpiration.NONE

  override def isExpired: Boolean = data.expired

  override def getImage = getImage(0, 0)

  private def getImage(w: Int, h: Int) = data.msgType match {
    case Type.LOCATION =>
      data.location.fold2(ImageAsset.Empty, { loc =>
        val id = AssetId(s"${data.assetId.str}_${w}_$h") // use dimensions in id, to avoid caching images with different sizes
        context.images.getLocalImageAsset(GoogleMapsMediaService.mapImageAsset(id, loc, if (w <= 0) GoogleMapsMediaService.ImageDimensions else Dim2(w, h)))
      })
    case _ => context.images.getImageAsset(data.assetId)
  }

  override def getId: String = data.id.str

  override def retry(): Unit =
    if (data.state == Status.FAILED || data.state == Status.FAILED_READ) context.messages.retry(data.convId, data.id)
    else error(s"Retrying a message that has not yet failed (${data.state}): $id")

  override def equals(other: Any): Boolean = other match {
    case other: Message => id == other.id
    case _ => false
  }

  override def hashCode: Int = id.hashCode

  override def toString: String = s"Message($id, $content, $likes, $data)"

  override def writeToParcel(dest: Parcel, flags: Int): Unit = {
    dest.writeString(JsonEncoder.encodeString(data))
    dest.writeStringArray(likes.map(_.str)(breakOut))
    dest.writeInt(if (likedBySelf) 1 else 0)
  }

  override def describeContents(): Int = 0

  override def getLikes: Array[api.User] = likes.map(context.users.getUser)(breakOut)
}

object EmptyMessage extends com.waz.api.Message {
  override def getId: String = Uid(0, 0).str
  override def retry(): Unit = ()
  override def getImage: api.ImageAsset = ImageAsset.Empty
  override def removeUpdateListener(listener: UpdateListener): Unit = {}
  override def addUpdateListener(listener: UpdateListener): Unit = {}
  override def writeToParcel(dest: Parcel, flags: Int): Unit = {
    dest.writeString(JsonEncoder.encodeString(MessageData.Empty))
    dest.writeStringArray(Array.empty[String])
    dest.writeInt(0)
  }
  override def describeContents(): Int = 0
  override val getLikes: Array[api.User] = Array.empty
  override def isEphemeral: Boolean = false
  override def isExpired: Boolean = false

}
