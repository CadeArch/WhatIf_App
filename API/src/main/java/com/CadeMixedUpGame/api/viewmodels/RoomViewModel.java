package com.CadeMixedUpGame.api.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RoomViewModel extends ViewModel {
    ObservableArrayList<Room> rooms;
    Room room;
    public ArrayList<String> roomNames = new ArrayList<>();
    public DatabaseReference db;
    String allChars = "a b c d e f g h i j k l m n o p q r s t u v w x y z A B C D E F G H I J K L M N O P Q R S T U V W X Y Z 0 1 2 3 4 5 6 7 8 9";
    String[] usableCharacter;
    public MutableLiveData<Boolean> inProgress = new MutableLiveData<Boolean>();

    public RoomViewModel() {
        db = FirebaseDatabase.getInstance().getReference();
        usableCharacter = allChars.split(" ");
        if (rooms == null) {
            rooms = new ObservableArrayList<Room>();
            loadRooms();
        }
    }


    public ArrayList<String> loadRooms() {
        db.child("rooms").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                rooms.add(new Room(snapshot.getKey()));
                roomNames.add(snapshot.getKey());
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                Room room = new Room(snapshot.getKey());
                rooms.remove(room);
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
        return roomNames;
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

    public ObservableArrayList<Room> getRooms() {
        return rooms;
    }

    public void deleteRoom(String roomID) {
        db.child("rooms").child(roomID).removeValue();
    }

    public void pushRoom(String id) {
        room = new Room(id);
        db.child("rooms").child(room.roomID).setValue(room);
    }
    public void gameInProgressTrue() {
        db.child("rooms").child(room.roomID).child("gameInProgress").setValue(true);
    }

    public void gameInProgressFalse() {
        db.child("rooms").child(room.roomID).child("gameInProgress").setValue(false);
    }

    public void checkIfInProgress(String room) {
        db.child("rooms").child(room).child("gameInProgress").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                inProgress.setValue(dataSnapshot.getValue(boolean.class));
                System.out.println("DB says game is ----------------- roomVM " + inProgress.getValue());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }
    public Room getRoom() {
        return room;
    }

    //    public void updateNumInRoom(User user) {
//
//    }
}
