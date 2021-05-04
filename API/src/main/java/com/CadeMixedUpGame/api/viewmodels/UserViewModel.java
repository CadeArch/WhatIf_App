package com.CadeMixedUpGame.api.viewmodels;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.User;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class UserViewModel extends ViewModel {
    ObservableArrayList<User> users;
    ArrayList<String> usersInRoom = new ArrayList<>();
    public String localName;
    DatabaseReference db;
    public String myRoom;

    public UserViewModel() {
        db = FirebaseDatabase.getInstance().getReference();
        if (users == null) {
            users = new ObservableArrayList<User>();
        }
    }


    public ObservableArrayList<User> getUsers() {
        return users;
    }

    public void loadUsers(String gameRoom, String userName) {
        db.child("rooms").child(gameRoom).child("players").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                usersInRoom.add(userName);
                User newUser = snapshot.getValue(User.class);
                users.add(newUser);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    public void listenToHost(User host) {
        db.child("rooms").child(host.gameRoom).child("players").child(host.userName).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                if (snapshot.getKey().equals("hostStarted")) {
                    for (User user : users) {
                        user.hostStarted = true;
                    }
                    System.out.println("Host Started: TRUE");
                }
                System.out.println("Child Changed Called: TRUE ");
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

        public int usersInRoom() {
        return users.size();
    }

    public void pushPerson(User user) {
        int userID = (int)(Math.random() * 100000);
        user.userID = userID;
        db.child("rooms").child(user.gameRoom).child("players").child(user.userName).setValue(user);
    }

    //key to update values in firebase
    public void hostStarted(User user) {
        //updating the status that the host has started the game
        db.child("rooms").child(user.gameRoom).child("players").child(user.userName).child("hostStarted").setValue(true);

    }

}

