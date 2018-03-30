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
package com.waz.service

import com.waz.api.UsernameValidation
import com.waz.model.Handle
import com.waz.utils.events.{Signal, SourceSignal}

class HandlesService {
  val validatedHandles: SourceSignal[Map[Handle, UsernameValidation]] = Signal(Map.empty[Handle, UsernameValidation])

  def updateValidatedHandles(validations: Seq[UsernameValidation]): Unit = {
    validatedHandles mutate {original =>
      val newHandles = (validations map (validation => Handle(validation.username) -> validation)).toMap
      val result = original.filter(pair => !newHandles.contains(pair._1)) ++ newHandles
      result
    }
  }

  def getValidations(handles: Seq[Handle]) : Signal[Seq[UsernameValidation]] = {
    validatedHandles map (currentHandles => handles.filter(h => currentHandles.contains(h)).flatMap(h => currentHandles.get(h)))
  }
}
