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

import android.net.Uri;
import com.waz.model.PersonalInvitationToken;
import com.waz.service.invitations.WebLink;

public class InvitationTokenFactory {

    public static Invitations.PersonalToken personalTokenFromCode(String code) {
        return new PersonalInvitationToken(code);
    }

    public static Invitations.GenericToken genericTokenFromCode(String code) {
        return new WebLink.Token(code);
    }

    public static Invitations.GenericToken genericTokenFromUri(Uri uri) {
        return WebLink.unapply(uri).get();
    }
}
