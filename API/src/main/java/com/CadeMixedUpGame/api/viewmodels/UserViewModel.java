package com.CadeMixedUpGame.api.viewmodels;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.User;

import com.google.android.gms.common.internal.ConnectionErrorMessages;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
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
    public MutableLiveData<Toast> signInToast = new MutableLiveData<>();
    ObservableArrayList<User> users;
    public MutableLiveData<User> host = new MutableLiveData<User>();
    public String localRandIf = "";
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
                if (task.isSuccessful()) {
                    signInToast.getValue().setText("account created");

                    //setting the username of the account to whatever they put in the box when signing up
                    FirebaseUser fBuser = FirebaseAuth.getInstance().getCurrentUser();
                    buildUser(fBuser, userName);

//                    System.out.println(" -----------------------------\n " + "email: " + email + "\npassword: " + password + "\nusername: " + userName);
                    // setting username in Firebase account
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
                else {
                    // If sign in fails, display a message to the user.
//                    System.out.println("EXEPTION----------------------- " + task.getException().getMessage());
                    if (task.getException().getMessage().equals("The email address is badly formatted.")) {
                        signInToast.getValue().setText("Email Badly Formatted");
                    } else if (task.getException().getMessage().equals("The given password is invalid. [ Password should be at least 6 characters ]")) {
                        signInToast.getValue().setText("Weak Password");
                    } else if (task.getException().getMessage().equals("The email address is already in use by another account.")) {
                        signInToast.getValue().setText("Email in Use");
                    } else {
                        System.out.println("EXEPTION----------------------- " + task.getException().getMessage());
                        signInToast.getValue().setText("Error");
                    }
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
                    signInToast.getValue().setText("Sign in Complete");

                } else {
                    // If sign in fails, display a message to the user.
//                    System.out.println("EXEPTION----------------------- " + task.getException().getMessage());
                    if (task.getException().getMessage().equals("The password is invalid or the user does not have a password.")) {
                        signInToast.getValue().setText("Invalid Password");
                    }
                    else if (task.getException().getMessage().equals("There is no user record corresponding to this identifier. The user may have been deleted.")) {
                        signInToast.getValue().setText("Invalid Email");
                    }
                    else if (task.getException().getMessage().equals("The email address is badly formatted.")) {
                        signInToast.getValue().setText("Email Badly Formatted");
                    }
                    else {
                        signInToast.getValue().setText("User Disabled");
                    }
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

    // used in create and join game to see players that join room from firebase
    public void loadUsers(String gameRoom, String userName) {
        db.child("rooms").child(gameRoom).child("players").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                String child = snapshot.getKey();
//                System.out.println(child);

                for(DataSnapshot ds : snapshot.getChildren()) {
                    User user = ds.getValue(User.class);
//                    Log.d("result", "User name: " + user.getUserName() + ", email " + user.getEmail());
//                    System.out.println("Not Null user FROM-DB? ------------ " + user.userName);
//                    System.out.println("DB-NEW PLAYER ADDED---------- " + user.userName);
                    users.add(user);
                }
                for(User user: users) {
//                    System.out.println(user.userName);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                System.out.println("DATABASE NOTICED CHANGE IN GAMEROOM---------------------------------");
//                System.out.println(snapshot);
                for(DataSnapshot ds : snapshot.getChildren()) {
                    User user = ds.getValue(User.class);
                    // this is so it will only update in device once players are playing game
                    if (user.ifSentence.length() > 0 && user.thenSentence.length() == 0) {
//                        Log.d("IF CHANGED", "User name: " + user.getUserName() + ", ------------------- " + user.getEmail());
//                        for (User us : users) {
//                            System.out.println("before removed --------------- " + us.userName + " " + us.userID + " " + us.ifSentence);
//                        }
                        users.remove(user);
                        users.add(user);
//                        for (User us : users) {
//                            System.out.println("after re added ---------------- " + us.userName + " " + us.userID + " " + us.ifSentence);
//                        }
                    }
                    // this is for when a then answer is changed
                    if (user.thenSentence.length() > 0) {
//                        Log.d("THEN CHANGED", "User name: " + user.getUserName() + ", ---------------- " + user.getEmail());

                        users.remove(user);
                        users.add(user);
                    }
                }

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                System.out.println(error.getCode() + ": " + error.getMessage() + ": " + error.getDetails());
            }
        });
    }

    //key to update values in firebase
    public void hostStarted(User user) {
        //updating the status that the host has started the game
        db.child("rooms").child(user.gameRoom).child("players").child(user.userName).child("value").child("hostStarted").setValue(true);

    }

    public void listenToHost(MutableLiveData<User> host) {
        db.child("rooms").child(host.getValue().gameRoom).child("players").child(host.getValue().userName).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                System.out.println(snapshot.getValue());
//                System.out.println(snapshot.getKey());
                User theChanged = snapshot.getValue(User.class);
                System.out.println(theChanged.userName + theChanged.host + theChanged.hostStarted);

                if (theChanged.hostStarted) {
                    getUser().getValue().hostStarted = true;
                    getUser().setValue(getUser().getValue());
                }

//                System.out.println("DATABASE noticed Change On Host Player ");
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                System.out.println("ERROR==========" + error.getMessage());
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

    public void pushIf(MutableLiveData<User> user) {
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName).child("value").child("ifSentence").setValue(user.getValue().ifSentence);
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName).child("value").child("ifFinished").setValue(user.getValue().ifFinished);
    }

    public void pushThen(MutableLiveData<User> user) {
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName).child("value").child("thenSentence").setValue(user.getValue().thenSentence);
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName).child("value").child("thenFinished").setValue(user.getValue().thenFinished);
    }

}

