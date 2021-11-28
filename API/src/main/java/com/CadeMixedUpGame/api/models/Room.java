package com.CadeMixedUpGame.api.models;

import androidx.annotation.Nullable;

public class Room {
    public String roomID;
    public boolean gameInProgress;

    public Room(String roomID) {
        this.roomID = roomID;
        this.gameInProgress = false;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof Room) {
            Room room = (Room) obj;
            return room.roomID.equals(roomID);
        }
        return false;
    }

}
