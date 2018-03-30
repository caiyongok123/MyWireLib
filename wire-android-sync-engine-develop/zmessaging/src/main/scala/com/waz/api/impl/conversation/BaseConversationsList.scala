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

import com.waz.ZLog._
import com.waz.api
import com.waz.api.impl.{ConversationsList, EventualReadiness}
import com.waz.content.ConversationsSet
import com.waz.model.{ConvId, ConversationData}
import com.waz.threading.Threading
import com.waz.ui.{Conversations, SignalLoading, UiEventListener, UiModule}
import com.waz.utils.events.Signal

import scala.collection.immutable.SortedSet
import scala.concurrent.Future

trait BaseConversationsList extends api.impl.CoreList[api.IConversation] with SignalLoading with EventualReadiness {
  import BaseConversationsList._

  private var convs = Option.empty[Convs]

  protected val conversations: Conversations
  protected def filter: (ConversationData => Boolean) = ConversationsList.RegularListFilter

  implicit def ui: UiModule

  addLoader(_.convsContent.conversationsSignal.throttle(UiEventListener.UpdateThrottling).flatMap(buildConversationsList), None) {
    case Some(cs) =>
      val orderChanged = convs.forall(_.ordered != cs) // important: SortedSet uses its Ordering to determine equality
      convs = Some(Convs(cs))
      ready()
      if (orderChanged) notifyChanged()

    case None =>
      if (convs.isDefined) {
        convs = None
        notifyChanged()
      }
  }

  private def buildConversationsList(convs: ConversationsSet): Signal[Option[SortedSet[ConversationData]]] =
    Signal.future(Future(Some(convs.conversations.filter(filter)))(Threading.Background))

  override def size: Int = convs.fold(0)(_.seq.length)

  override def get(position: Int): api.IConversation = conversations.getConversation(convs.getOrElse(throw new NoSuchElementException(s"position: $position")).seq(position))

  def getConversationIndex(id: String): Int = convs.flatMap(_.indices.get(ConvId(id))).getOrElse(-1)
}

object BaseConversationsList {
  private implicit val logTag: LogTag = logTagFor[BaseConversationsList]

  private case class Convs(ordered: SortedSet[ConversationData]) {
    val seq = ordered.toVector
    val indices = ordered.iterator.map(_.id).zipWithIndex.toMap
  }
}
