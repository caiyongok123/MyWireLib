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

import com.waz.api.UsernameValidation
import com.waz.model.Handle
import com.waz.ui.UiModule
import com.waz.utils.events.{EventContext, Signal}
import com.waz.ZLog.ImplicitTag._

class ValidatedHandles()(implicit ui: UiModule) extends com.waz.api.ValidatedUsernames with UiObservable {
  implicit val ec = EventContext.Global

  ui.currentZms.flatMap{
    case None => Signal.empty[Map[Handle, UsernameValidation]]
    case Some(zms) => zms.handlesService.validatedHandles
  }.onChanged.onUi{
    _ => notifyChanged()
  }

  override def getValidations(usernames: Array[String]): Array[UsernameValidation] = {
    ui.currentZms.flatMap{
      case Some(zms) => zms.handlesService.getValidations(usernames.map(Handle(_)))
      case _ => Signal(Seq[UsernameValidation]())
    }.currentValue.getOrElse(Seq()).toArray
  }
}
