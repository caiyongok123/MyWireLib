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
package com.waz.api.impl.conversation

import com.waz.model.ConversationData.ConversationType
import com.waz.ui.{SignalLoading, UiModule}

/**
 * Special conversation implementation tracking self conversation.
 * Allows us to always return actual object whenever UI asks for conversation.
 * Will reload with proper data on user change.
 *
 * XXX: Contrary to regular conversations this object's id can change, but UI shouldn't need to use, so we should be safe.
 */
class SelfConversation(implicit ui: UiModule) extends BaseConversation with SignalLoading {

  data = data.copy(convType = ConversationType.Self)

  addLoader(_.convsNotifier.selfConversationSignal, None) { _.foreach(set) }

}
