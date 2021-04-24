package com.CadeMixedUpGame.api.models;

import androidx.annotation.Nullable;

public class Room {
    public String roomID;
    public int numInRoom;

    public Room(String roomID) {
        this.roomID = roomID;
        this.numInRoom = 1;
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
