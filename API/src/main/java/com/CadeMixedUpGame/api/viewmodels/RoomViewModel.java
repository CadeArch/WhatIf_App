package com.CadeMixedUpGame.api.viewmodels;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.models.Room;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class RoomViewModel extends ViewModel {
    MutableLiveData<Room> room;
    DatabaseReference db;

    public RoomViewModel() {
        db = FirebaseDatabase.getInstance().getReference();
        if (room != null) {
            room.getValue();
        }
    }

    public String getRoomID() {
        return room.getValue().roomID;
    }

    public int getNumInRoom() {
        return room.getValue().numInRoom;
    }

    public void incrementNumInRoom() {
        room.getValue().numInRoom += 1;
    }
}
