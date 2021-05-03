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

    public void loadUsers(String gameRoom) {
        db.child("rooms").child(gameRoom).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                usersInRoom.add(snapshot.getKey());

                User newUser = new User(usersInRoom() + 1, snapshot.getKey());
                users.add(newUser);
                System.out.println("added");
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

    public int usersInRoom() {
        return users.size();
    }

    public void pushPerson(User user) {
        db.child("rooms").child(user.gameRoom).child(user.userName).setValue(user);
    }

}

