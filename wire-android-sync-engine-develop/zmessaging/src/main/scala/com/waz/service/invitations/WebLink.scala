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
package com.waz.service.invitations

import java.net.URLDecoder
import java.nio.{ByteBuffer, ByteOrder}
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}

import android.net.Uri
import android.os.Parcel
import android.util.Base64
import com.waz.api.Invitations.GenericToken
import com.waz.model.UserId

import scala.util.{Random, Try}

object WebLink {
  val BaseUri = Uri.parse("https://www.wire.com/c/")
  val TokenExpiryHours = 14 * 24 // 2 weeks in hours

  val TimeZero = 1388534400000L // 2014.1.1 00:00:00 UTC
  val Secret = Array(0x64, 0x68, 0xca, 0xee, 0x5c, 0x0, 0x25, 0xf5, 0x68, 0xe4, 0xd0, 0x85, 0xf8, 0x38, 0x28, 0x6a, 0x8a, 0x98, 0x6d, 0x2d, 0xfa, 0x67, 0x5e, 0x48, 0xa3, 0xed, 0x2a, 0xef, 0xdd, 0xaf, 0xe8, 0xc1).map(_.toByte)
  val IV = Array.fill[Byte](16)(0)

  def cipher(mode: Int) = {
    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
    cipher.init(mode, new SecretKeySpec(Secret, "AES"), new IvParameterSpec(IV))
    cipher
  }

  val Encipher = new ThreadLocal[Cipher] {
    override def initialValue(): Cipher = cipher(Cipher.ENCRYPT_MODE)
  }
  val Decipher = new ThreadLocal[Cipher] {
    override def initialValue(): Cipher = cipher(Cipher.DECRYPT_MODE)
  }

  def apply(token: Token) = BaseUri.buildUpon().appendPath(token.code).build()

  def unapply(uri: Uri): Option[Token] = Some(Token(uri.getLastPathSegment))

  def decodeToken(code: String) = Try {
    val buffer = ByteBuffer.wrap(Decipher.get().doFinal(Base64.decode(code, Base64.URL_SAFE)))
    buffer.order(ByteOrder.BIG_ENDIAN)
    buffer.position(14)
    val time = buffer.getShort & 0xffff
    val msb = buffer.getLong
    val lsb = buffer.getLong
    (time, new UUID(msb, lsb))
  }

  def encodeToken(uid: UserId, currentTime: Long = System.currentTimeMillis()) = {
    val bytes = new Array[Byte](32)
    Random.nextBytes(bytes)
    val buffer = ByteBuffer.wrap(bytes)
    buffer.order(ByteOrder.BIG_ENDIAN)
    buffer.position(14)
    buffer.putShort(timestamp(currentTime))
    val uuid = UUID.fromString(uid.str)
    buffer.putLong(uuid.getMostSignificantBits)
    buffer.putLong(uuid.getLeastSignificantBits)

    new Token(Base64.encodeToString(Encipher.get.doFinal(bytes), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING))
  }

  def timestamp(currentTime: Long = System.currentTimeMillis()) = math.max(0, (currentTime - TimeZero) / 1000 / 3600).toShort

  case class Token(code: String) extends GenericToken {

    def userId: Option[UserId] =
      decodeToken(code).filter { case (time, _) => time + TokenExpiryHours >= timestamp() }
        .map { case (_, uuid) => UserId(uuid.toString) }
        .toOption

    def describeContents: Int = 0
    def writeToParcel(out: Parcel, flags: Int): Unit = out.writeString(code)
  }

  object Token extends (String => Token) {
    def apply(uri: Uri): Token = new Token(URLDecoder.decode(uri.getLastPathSegment, "utf8"))
    def apply(uid: UserId, currentTime: Long = System.currentTimeMillis()): Token = encodeToken(uid, currentTime)
    def fromParcel(source: Parcel): Token = apply(source.readString())
  }
}
