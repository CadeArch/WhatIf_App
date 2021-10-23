package com.CadeMixedUpGame.api.viewmodels;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
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
    FirebaseAuth auth;
    MutableLiveData<User> user = new MutableLiveData<User>();

    public UserViewModel() {
        db = FirebaseDatabase.getInstance().getReference();
        if (users == null) {
            users = new ObservableArrayList<User>();
        }

        this.auth = FirebaseAuth.getInstance();
        this.auth.addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser fbUser = auth.getCurrentUser();
//                loginError.setValue(null);
                if (fbUser == null) {
                    user.setValue(null);
                } else {
                    // display name should be set when they sign up
                    user.setValue(new User(fbUser, fbUser.getDisplayName()));
                    User myUser = user.getValue();
                    myUser.accountPlay = true;

                }
            }
        });
    }

    public void signUp(String email, String password, String userName) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                AuthResult result = task.getResult();
                // if user wasn't created
                if (result.getUser() == null) {
                    Log.d("Error: ", "Failure to create", task.getException());
                }
                // when user is created set up their display name
                else {

                    //setting the username of the account to whatever they put in the box when signing up
                    FirebaseUser fBuser = FirebaseAuth.getInstance().getCurrentUser();
                    buildUser(fBuser, userName);

//                    System.out.println(" -----------------------------\n " + "email: " + email + "\npassword: " + password + "\nusername: " + userName);
//                    System.out.println(user);
                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                            .setDisplayName(userName).build();

                    fBuser.updateProfile(profileUpdates).addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
//                            System.out.println("DISPLAY NAME ----------------" + fBuser.getDisplayName());
                            // assuring their username is set when they create an account
                            getUser().getValue().userName = fBuser.getDisplayName();
                            pushAccountPlayer(user);

                        }
                    });
                }

            }
        });

    }

    public void signIn(String email, String password) {

        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d("Success: ", "signInWithEmail:success--------------");
                    FirebaseUser fbUser = auth.getCurrentUser();
                    localName = fbUser.getDisplayName();
                    buildUser(fbUser, localName);


                } else {
                    // If sign in fails, display a message to the user.
                    System.out.println("failed to sign in ------------------");

                }
            }
        });
    }

    public void signOut() {
        auth.signOut();
    }

    public MutableLiveData<User> getUser() {
        return user;
    }

    public ObservableArrayList<User> getUsers() {
        return users;
    }


    public void loadUsers(String gameRoom, String userName) {
        //// CHECK THIS
        db.child("rooms").child(gameRoom).child("players").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                // this has data in it?
                System.out.println(snapshot);

                //this is the persons name but snapshot.child(child).getvalue(User.class) produces null
                String child = snapshot.getKey();
                System.out.println(child);

                for(DataSnapshot ds : snapshot.getChildren()) {
                    User user = ds.getValue(User.class);
                    Log.d("result", "User name: " + user.getUserName() + ", email " + user.getEmail());
                    System.out.println("Not Null? ------------ " + user);
                    System.out.println("DB-NEW PLAYER ADDED---------- " + user.userName);
                    users.add(user);
                }
                User newUser = snapshot.getValue(User.class);
                // producing null User?

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

    //used when a user signs up for the first time and when the user logs in
    public MutableLiveData<User> buildUser(FirebaseUser fbUser, String username) {
        user.setValue(new User(fbUser, username));
        user.getValue().accountPlay = true;

        return user;
    }
    //in freeplay to build the user
    public MutableLiveData<User> buildUserFree(String username) {
        user.setValue(new User(username));
        user.getValue().accountPlay = false;

        return user;
    }


    public void pushPerson(MutableLiveData<User> user) {
        int userID = (int)(Math.random() * 100000);
        user.getValue().userID = userID;
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName).setValue(user).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()){
                    System.out.println("SUCCESS - PUSHED TO DATABASE");
                }
                else {
                    System.out.println(task.getResult());
                }
            }
        });
    }

    public void pushAccountPlayer(MutableLiveData<User> user) {
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).setValue(user);
    }

    //key to update values in firebase
    public void hostStarted(User user) {
        //updating the status that the host has started the game
        db.child("rooms").child(user.gameRoom).child("players").child(user.userName).child("hostStarted").setValue(true);

    }

}

