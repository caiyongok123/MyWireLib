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

public interface IConversation extends UiObservable, Parcelable {

    enum Type {
        UNKNOWN(-1), GROUP(0), SELF(1), ONE_TO_ONE(2), WAIT_FOR_CONNECTION(3), INCOMING_CONNECTION(4);

        // conversation type backend id - this value is received in json
        public int id;

        Type(int id) {
            this.id = id;
        }

        public static Type withId(int id) {
            switch(id) {
                case -1: return UNKNOWN;
                case 0: return GROUP;
                case 1: return SELF;
                case 2: return ONE_TO_ONE;
                case 3: return WAIT_FOR_CONNECTION;
                case 4: return INCOMING_CONNECTION;
                default: return UNKNOWN;
            }
        }
    }

    /**
     * https://github.com/wireapp/architecture/blob/master/topics/conversations/access%20modes.md
     *
     * Access:
     * Specifies the means by which a user (with the correct access role) may join a conversation
     */
    enum Access {
        INVITE,  //possible to join only via invite from another conv member
        CODE,    //possible to join conversation via a "link" (confusingly enough)
        LINK,    //possible to join if the convId is known
        PRIVATE; //for 1:1 conversations
    }

    /**
     * Specifies what types of users may join a conversation
     */
    enum AccessRole {
        TEAM,          //only team members may join the conversation
        ACTIVATED,     //only users with activated account may join the conversation
        NON_ACTIVATED, //"wireless" users may join the conversation
        PRIVATE        //for 1:1 conversations
    }

    Parcelable.Creator<IConversation> CREATOR = new Parcelable.Creator<IConversation>() {
        @Override
        public IConversation createFromParcel(Parcel source) {
            return ZMessaging$.MODULE$.currentUi().convs().getConversation(source);
        }

        @Override
        public IConversation[] newArray(int size) {
            return new IConversation[size];
        }
    };

    Type getType();

    MembersList getUsers();

    /*
     id of the conversation
    */
    String getId();

    /*
        For now me is retrieved by another core lib function and
        if this is empty there is another function constructing
        a friendly name via the id
    */
    String getName();

    /*
        Is this user also a member in this conversation, so far I know
        that the user doesn't have an option to mute the conversation
    */
    boolean isMemberOfConversation();

    /**
     *
     * @return for {@link com.waz.api.IConversation.Type#ONE_TO_ONE} returns the other user. Throws a RuntimeException
     * for other conversation types.
     */
    User getOtherParticipant();

    // used by DeviceActor
    void setArchived(boolean archived);
    void setMuted(boolean muted);
    void clear();
    /*
        Returns a background image for this conversation
     */
    ImageAsset getBackground();

    /**
     * Conversation verification state.
     * VERIFIED if all members of conversation are verified (have otr clients and oll their clients are verified)
     * UNVERIFIED if conversation was verified previously and new unverified client was added.
     */
    Verification getVerified();

    InputStateIndicator getInputStateIndicator();

}
