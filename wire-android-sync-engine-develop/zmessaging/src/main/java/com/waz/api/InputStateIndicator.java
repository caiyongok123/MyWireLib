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
package com.waz.api;

public interface InputStateIndicator extends UiObservable {

    /**
     * @deprecated - hot knocks are no longer supported, so this is useless.
     */
    enum KnockState {
        NONE,       // user can knock
        KNOCKED,    // user just knocked, next knock() will cause hot knock
        DISABLED    // user hot knocked, knocking is now disabled
    }

    /**
     * @deprecated - hot knocks are no longer supported, so this is useless.
     */
    KnockState getKnockState();

    UsersList getTypingUsers();

    /**
     * Call this whenever the user types text, or if the text gets changed otherwise
     * (for example, returning to a conversation with a saved draft message).
     */
    void textChanged();

    /**
     * Call this when the text gets cleared. This is not necessary when pausing the app
     * because the sync engine handles that, but otherwise, when the user clears the text manually,
     * switches conversations or sends the message, you should call this.
     */
    void textCleared();
}
