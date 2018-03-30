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

import android.os.Parcel;
import android.os.Parcelable;
import com.waz.service.ZMessaging$;

public interface User extends UiObservable, Parcelable {

    String getName();
    String getDisplayName();
    @Deprecated // use getDisplayName instead
    String getNameBasedOnConnectionState(); // display name if connected, name otherwise
    String getInitials();
    String getId();
    String getEmail();
    String getPhone();
    ImageAsset getPicture();
    AccentColor getAccent();
    boolean isConnected();
    boolean isRelated();
    boolean isMe();
    boolean isDeleted();
    boolean isAutoConnection();
    boolean isContact();
    ContactDetails getFirstContact();

    CoreList<OtrClient> getOtrClients();
    Verification getVerified();

    String getUsername();

    Parcelable.Creator<User> CREATOR = new Parcelable.Creator<User>() {
        @Override
        public User createFromParcel(Parcel source) {
            return ZMessaging$.MODULE$.currentUi().users().getUser(source);
        }

        @Override
        public User[] newArray(int size) {
            return new User[size];
        }
    };

    enum ConnectionStatus {
        UNCONNECTED("unconnected"), PENDING_FROM_USER("sent"), PENDING_FROM_OTHER("pending"), ACCEPTED("accepted"), BLOCKED("blocked"), IGNORED("ignored"), SELF("self"), CANCELLED("cancelled");

        public String code;

        ConnectionStatus(String code) {
            this.code = code;
        }
    }

    ConnectionStatus getConnectionStatus();

    /**
     * Sends connection request to this user and creates one-to-one conversation.
     */
    IConversation connect(String message);

    /**
     * Returns a one-to-one conversation with this user (creates new conversation if needed), should only be used with already connected user.
     */
    IConversation getConversation();

    IConversation acceptConnection();

    void ignoreConnection();

    void cancelConnection();

    void block();

    IConversation unblock();
}
