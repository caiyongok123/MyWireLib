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

import java.text.Normalizer

import android.content.Context
import com.waz.ZLog._
import com.waz.api
import com.waz.api.ValidatedUsernames
import com.waz.model.Handle
import com.waz.ui.UiModule
import com.waz.utils.crypto.ZSecureRandom
import com.waz.zms.R

object Usernames {
  val MAX_LENGTH = 21
  val MIN_LENGTH = 2
  val checkMultipleAvailabilityPath = "/users"
  val checkSingleAvailabilityPath = "/users/handles/"
  val handlesQuery = "handles"
}

class Usernames()(implicit ui: UiModule) extends api.Usernames{
  private implicit val tag: LogTag = s"Usernames"

  override def generateUsernameFromName(name: String, context: Context): String = {
    var cleanName: String = Handle.transliterated(name).toLowerCase
    cleanName = Normalizer.normalize(cleanName, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
    cleanName = Normalizer.normalize(cleanName, Normalizer.Form.NFD).replaceAll("\\W+", "")
    if (cleanName.isEmpty) {
      cleanName = generateFromDictionary(context)
    }
    cleanName
  }

  override def validateUsernames(usernames: Array[String]): Unit = {
    ui.zms(_.sync.postValidateHandles(usernames.map(Handle(_))))
  }

  override def getValidatedUsernames: ValidatedUsernames = new ValidatedHandles()

  private def generateFromDictionary(context: Context): String = Option(context) match {
    case None => ""
    case Some(c) =>
      val names = c.getResources.getStringArray(R.array.random_names)
      val adjectives = c.getResources.getStringArray(R.array.random_adjectives)
      val namesIndex = ZSecureRandom.nextInt(names.length)
      val adjectivesIndex = ZSecureRandom.nextInt(adjectives.length)
      (adjectives(adjectivesIndex) + names(namesIndex)).toLowerCase
  }
}
