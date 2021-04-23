package com.CadeMixedUpGame.api.viewmodels;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.models.Room;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.Random;

public class RoomViewModel extends ViewModel {
    MutableLiveData<Room> room;
    DatabaseReference db;
    String allChars = "a b c d e f g h i j k l m n o p q r s t u v w x y z A B C D E F G H I J K L M N O P Q R S T U V W X Y Z 0 1 2 3 4 5 6 7 8 9";
    String[] usableCharacter;

    public RoomViewModel() {
        db = FirebaseDatabase.getInstance().getReference();
        usableCharacter = allChars.split(" ");
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

    public String makeRoomID() {
        Random rand = new Random();
        String sequence = "";
        for (int x = 0; x < 4; x++ ) {
            int randNum = rand.nextInt(usableCharacter.length);
            sequence += usableCharacter[randNum];

        }
        return sequence;
    }
}
