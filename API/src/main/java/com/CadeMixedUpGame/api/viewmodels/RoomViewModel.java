package com.CadeMixedUpGame.api.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.repositories.FirebaseGameRepository;
import com.CadeMixedUpGame.api.repositories.GameRepository;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Random;

public class RoomViewModel extends ViewModel {
    ObservableArrayList<Room> rooms;
    Room room;
    public ArrayList<String> roomNames = new ArrayList<>();
    public DatabaseReference db;
    private final GameRepository repository;
    // removed capitol I and lowercase l because they were ambiguous with the font i am using
    String allChars = "a b c d e f g h i j k m n o p q r s t u v w x y z A B C D E F G H J K L M N O P Q R S T U V W X Y Z 0 1 2 3 4 5 6 7 8 9";
    String[] usableCharacter;
    public MutableLiveData<Boolean> inProgress = new MutableLiveData<Boolean>();

    public RoomViewModel() {
        this(new FirebaseGameRepository(), true);
    }

    public RoomViewModel(GameRepository repository) {
        this(repository, false);
    }

    public RoomViewModel(GameRepository repository, boolean loadRoomsOnCreate) {
        if (repository == null) {
            throw new IllegalArgumentException("repository cannot be null");
        }
        this.repository = repository;
        db = repository.root();
        usableCharacter = allChars.split(" ");
        if (rooms == null) {
            rooms = new ObservableArrayList<Room>();
            if (loadRoomsOnCreate) {
                loadRooms();
            }
        }
    }


    public ArrayList<String> loadRooms() {
        db.child("rooms").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                rooms.add(new Room(snapshot.getKey()));
                roomNames.add(snapshot.getKey());
                AppLog.d(AppLog.ROOM, "Room loaded id=" + snapshot.getKey() + ", total=" + roomNames.size());
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
                AppLog.e(AppLog.FIREBASE, "Rooms listener cancelled: " + error.getMessage());
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
        AppLog.i(AppLog.ROOM, "Deleting room=" + roomID);
        db.child("rooms").child(roomID).removeValue()
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed deleting room=" + roomID, e));
    }

    public void pushRoom(String id) {
        room = new Room(id);
        AppLog.i(AppLog.ROOM, "Creating room=" + room.roomID);
        db.child("rooms").child(room.roomID).setValue(room)
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Room created id=" + room.roomID))
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed creating room=" + room.roomID, e));
    }
    public void gameInProgressTrue() {
        if (room != null) {
            repository.setRoomInProgress(room.roomID, true);
        }
    }

    public void gameInProgressTrue(String roomID) {
        if (roomID != null && roomID.length() > 0) {
            repository.setRoomInProgress(roomID, true);
        }
    }

    public void gameInProgressFalse(String room) {
        if (room != null && room.length() > 0) {
            repository.setRoomInProgress(room, false);
        }
    }

    public void checkIfInProgress(String room) {
        db.child("rooms").child(room).child("gameInProgress").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Boolean value = dataSnapshot.getValue(Boolean.class);
                inProgress.setValue(value != null && value);
                AppLog.d(AppLog.ROOM, "Room progress loaded room=" + room + ", inProgress=" + inProgress.getValue());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                AppLog.e(AppLog.FIREBASE, "Failed checking room progress room=" + room + ": " + databaseError.getMessage());
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
