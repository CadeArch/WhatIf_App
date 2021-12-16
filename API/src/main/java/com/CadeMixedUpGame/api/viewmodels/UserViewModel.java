package com.CadeMixedUpGame.api.viewmodels;

import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.CadeMixedUpGame.api.models.Unlockable;
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

public class UserViewModel extends ViewModel {
    public MutableLiveData<Toast> signInToast = new MutableLiveData<>();
    ObservableArrayList<User> users;
    public MutableLiveData<User> host = new MutableLiveData<User>();
    public String localRandIf = "";
    public Boolean onWriteThen = false;
    public Boolean onWriteIf = false;
    public Boolean onWaitingForHost = false;
    public Boolean onEndFrag = false;
    public Boolean onCollectingAnswers = false;
    public Boolean playing = false;
    public String localName = "";
    DatabaseReference db;
    public String myRoom;
    FirebaseAuth auth;
    MutableLiveData<User> user = new MutableLiveData<User>();
    public ObservableArrayList<Unlockable> userUnlocked = new ObservableArrayList<Unlockable>();
    public ChildEventListener listener;

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

    public void reset() {
        signInToast = new MutableLiveData<>();
        // not sure if this is good reseting db member variable
        db = null;
        db = FirebaseDatabase.getInstance().getReference();

        localRandIf = "";
        onWriteThen = false;
        onWriteIf = false;
        onWaitingForHost = false;
        playing = false;
        setUsers(new ObservableArrayList<User>());

        System.out.println("HIT RESET");
        user.getValue().setIfFinished(false);
        user.getValue().setIfSentence("");
        user.getValue().setThenFinished(false);
        user.getValue().setThenSentence("");
        user.getValue().setHostStarted(false);
        user.getValue().setHostPlayedAgain("");

    }



    public void signUp(String email, String password, String userName) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    signInToast.getValue().setText("account created");

                    //setting the username of the account to whatever they put in the box when signing up
                    FirebaseUser fBuser = FirebaseAuth.getInstance().getCurrentUser();
                    buildUser(fBuser, userName, false);

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
                            getUser().getValue().perfectLeaderBoard = false;
                            getUser().getValue().madeLeaderBoard = false;
                            pushAccountPlayer(user);
                            fillUnlockables(user);
                            fillGamesPlayed(user);
                        }
                    });
                }
                else {
                    // If sign in fails, display a message to the user.
//                    System.out.println("EXEPTION----------------------- " + task.getException().getMessage());
                    if (task.getException().getMessage().equals("The email address is badly formatted.")) {
                        signInToast.getValue().setText("Email Badly Formatted");
                        System.out.println("bad email");
                    } else if (task.getException().getMessage().equals("The given password is invalid. [ Password should be at least 6 characters ]")) {
                        signInToast.getValue().setText("Weak Password");
                        System.out.println("weak password");
                    } else if (task.getException().getMessage().equals("The email address is already in use by another account.")) {
                        signInToast.getValue().setText("Email in Use");
                        System.out.println("email in use");
                    } else {
                        System.out.println("EXEPTION----------------------- " + task.getException().getMessage());
                        signInToast.getValue().setText("Error");
                        System.out.println("error");
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
                    buildUser(fbUser, localName, true);
                    getGamesPlayed(user, false);
//                    System.out.println("UPON SIGN IN - user has played " + user.getValue().gamesPlayed + " matches");
                    signInToast.getValue().setText("Sign in Complete");

                } else {
                    // If sign in fails, display a message to the user.
//                    System.out.println("EXEPTION----------------------- " + task.getException().getMessage());
                    if (task.getException().getMessage().equals("The password is invalid or the user does not have a password.")) {
                        signInToast.getValue().setText("Invalid Password");
                        System.out.println("bad password");
                    }
                    else if (task.getException().getMessage().equals("There is no user record corresponding to this identifier. The user may have been deleted.")) {
                        signInToast.getValue().setText("Invalid Email");
                        System.out.println("invalid Email");

                    }
                    else if (task.getException().getMessage().equals("The email address is badly formatted.")) {
                        signInToast.getValue().setText("Email Badly Formatted");
                        System.out.println("bad email");
                    }
                    else {
                        signInToast.getValue().setText("User Disabled");
                        System.out.println("userDisabled");
                    }
                }
            }
        });
    }

    //used when a user signs up for the first time and when the user logs in
    public MutableLiveData<User> buildUser(FirebaseUser fbUser, String username, boolean signIn) {
        user.setValue(new User(fbUser, username));
        user.getValue().accountPlay = true;
        if (signIn) {
            // todo this is finishing after the start fragment loads in, causing the toast to show
            getMadeLeaderBoard(user);
            getMadePerfectLeaderBoard(user);
        }

        return user;
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

    public void setUsers(ObservableArrayList<User> users) {
        this.users = users;
    }

    public void removeListenerOnDB() {
        db.child("rooms").child(myRoom).child("players").removeEventListener(listener);
    }

    // used in create and join game to see players that join room from firebase
    public void loadUsers(String gameRoom) {
        System.out.println("LOAD USERS CALLED: adding a listener to gameroom: " + gameRoom);
        listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                System.out.println("On Child Added Called");
                String child = snapshot.getKey();
//                System.out.println(child);

                for(DataSnapshot ds : snapshot.getChildren()) {
                    User user = ds.getValue(User.class);
//                    Log.d("result", "User name: " + user.getUserName() + ", email " + user.getEmail());
//                    System.out.println("Not Null user FROM-DB? ------------ " + user.userName);
//                    System.out.println("DB-NEW PLAYER ADDED---------- " + user.userName);
                    if (user != null) {
                        users.add(user);
                    }
                }
//                System.out.println("Users in user array after child added");
                for(User user: users) {
                    System.out.println(user.userName);
                }
//                System.out.println("users array size after added: " + users.size());
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                System.out.println("DATABASE NOTICED CHANGE IN GAMEROOM---------------------------------");
//                System.out.println(snapshot);
                System.out.println("On child changed called");
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
        };
        db.child("rooms").child(gameRoom).child("players").addChildEventListener(listener);
    }

    //key to update values in firebase
    public void hostStarted(User user) {
        //updating the status that the host has started the game
        db.child("rooms").child(user.gameRoom).child("players").child(user.userName+ "-" + user.userID).child("value").child("hostStarted").setValue(true);

    }

    public void hostPlayedAgain(User user) {
        System.out.println("Host played again: " + user.hostPlayedAgain + " Updating DB");
        if (user.hostPlayedAgain.equals("yes")) {
            db.child("rooms").child(user.gameRoom).child("players").child(user.userName+ "-" + user.userID).child("value").child("hostPlayedAgain").setValue("yes");
            System.out.println("Host Played again set to yes");
        }
        else if (user.hostPlayedAgain.equals("no")){
            db.child("rooms").child(user.gameRoom).child("players").child(user.userName+ "-" + user.userID).child("value").child("hostPlayedAgain").setValue("no");
            System.out.println("Host Played again set to no");
        }
        else {
            db.child("rooms").child(user.gameRoom).child("players").child(user.userName+ "-" + user.userID).child("value").child("hostPlayedAgain").setValue("");
            System.out.println("Host Played again set to NOTHING");

        }
    }

    public void listenToHost(MutableLiveData<User> host) {
        //todo in one use case this produced a null pointer somewhere and killed for a non host player
        System.out.println("putting listen to host listener on db: HOST IS " + host.getValue().userName);
        db.child("rooms").child(host.getValue().gameRoom).child("players").child(host.getValue().userName+ "-" + host.getValue().userID).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                User theChanged = snapshot.getValue(User.class);
//                System.out.println("Host Changed Values in DB: " + theChanged.userName + theChanged.host + theChanged.hostStarted);
//                System.out.println("Host played again in DB: " + theChanged.hostPlayedAgain);
                if (theChanged.hostStarted) {
                    getUser().getValue().hostStarted = true;
                    getUser().setValue(getUser().getValue());
                }

                if (theChanged.hostPlayedAgain.equals("yes")) {
                    getUser().getValue().hostPlayedAgain = "yes";
                    getUser().setValue(getUser().getValue());
                }
                if (theChanged.hostPlayedAgain.equals("no")) {
                    getUser().getValue().hostPlayedAgain = "no";
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

    public void getMadeLeaderBoard(MutableLiveData<User> user) {
        Task<DataSnapshot> madeLeader = db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("value").child("madeLeaderBoard").get();
        madeLeader.addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                DataSnapshot snapshot = madeLeader.getResult();
                boolean inDB = snapshot.getValue(Boolean.class);
                user.getValue().madeLeaderBoard = inDB;
                System.out.println("Made LeaderBoard ----- " + user.getValue().madeLeaderBoard);
            }
        });
    }

    public void getMadePerfectLeaderBoard(MutableLiveData<User> user) {
        Task<DataSnapshot> madePerfectLeader = db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("value").child("perfectLeaderBoard").get();
        madePerfectLeader.addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                DataSnapshot snapshot = madePerfectLeader.getResult();
                boolean inDB = snapshot.getValue(Boolean.class);
                user.getValue().perfectLeaderBoard = inDB;
                System.out.println("Made Perfect LeaderBoard ----- " + user.getValue().perfectLeaderBoard);
            }
        });
    }

    //in freeplay to build the user
    public MutableLiveData<User> buildUserFree(String username) {
        user.setValue(new User(username));
        user.getValue().accountPlay = false;

        return user;
    }

//    public void removeUserFromGameRoom(MutableLiveData<User> user) {
//        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName).removeValue();
//        System.out.println("removed User from GameRoom");
//        }

    public void pushPerson(MutableLiveData<User> user) {
        int userID = (int)(Math.random() * 100000);
        if (user.getValue().userID == 0) {
            user.getValue().userID = userID;
//            System.out.println("UserID set -------------------");
        }
        else {
            System.out.println("UserID already set -----------------");
        }
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName + "-" + user.getValue().userID).setValue(user).addOnCompleteListener(new OnCompleteListener<Void>() {
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

    public void deleteRoom(User userLeft) {
        System.out.println("Deleted room");
        db.child("rooms").child(userLeft.gameRoom).removeValue();
    }

    public void nurfAllUsers() {
        db.child("rooms").child(myRoom).child("players").removeValue();
        System.out.println("DELETING ALL IN ROOM");
    }
    public void deleteVotesAndVotingItems() {
        db.child("rooms").child(myRoom).child("votes").removeValue();
        db.child("rooms").child(myRoom).child("votingItems").removeValue();
        System.out.println("Deleting votes and voting items in gameroom in database");

    }

    public void pushAccountPlayer(MutableLiveData<User> user) {
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).setValue(user);
    }

    public void pushIf(MutableLiveData<User> user) {

        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName+ "-" + user.getValue().userID).child("value").child("ifSentence").setValue(user.getValue().ifSentence);
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName+ "-" + user.getValue().userID).child("value").child("ifFinished").setValue(user.getValue().ifFinished);
    }

    public void pushThen(MutableLiveData<User> user) {

        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName+ "-" + user.getValue().userID).child("value").child("thenSentence").setValue(user.getValue().thenSentence);
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName+ "-" + user.getValue().userID).child("value").child("thenFinished").setValue(user.getValue().thenFinished);
    }

    public void fillUnlockables(MutableLiveData<User> user) {
        Unlockable v1 = new Unlockable("fuddify", "1", false);
        Unlockable v2 = new Unlockable("pig latin", "2", false);
        Unlockable v3 = new Unlockable("backwords", "3", false);
        Unlockable v4 = new Unlockable("jokester", "4", false);
        Unlockable v5 = new Unlockable("forgetful", "5", false);
        Unlockable v6 = new Unlockable("shaggy", "6", false);
        Unlockable v7 = new Unlockable("disobedient", "7", false);

        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").child(v1.getVoiceType()).setValue(v1);
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").child(v2.getVoiceType()).setValue(v2);
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").child(v3.getVoiceType()).setValue(v3);
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").child(v4.getVoiceType()).setValue(v4);
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").child(v5.getVoiceType()).setValue(v5);
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").child(v6.getVoiceType()).setValue(v6);
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").child(v7.getVoiceType()).setValue(v7);

    }

    public void fillGamesPlayed(MutableLiveData<User> user) {
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("gamesPlayed").setValue(0);
    }

    public void getGamesPlayed(MutableLiveData<User> user, boolean increment) {
        Task<DataSnapshot> gamesPlayed = db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("gamesPlayed").get();
        gamesPlayed.addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                DataSnapshot snapshot = gamesPlayed.getResult();
                int totalPlayed = snapshot.getValue(Integer.class);
                user.getValue().gamesPlayed = totalPlayed;
                System.out.println("GAMES PLAYED ----- " + user.getValue().gamesPlayed);
                if (increment) {
                    incrementGamesPlayed(user);
                }
            }
        });
    }

    public void incrementGamesPlayed(MutableLiveData<User> user) {
        user.getValue().gamesPlayed += 1;
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("gamesPlayed").setValue(user.getValue().gamesPlayed);
    }

    public void unlockVoice(MutableLiveData<User> user, String which) {
        if (user.getValue().gamesPlayed >= 5 && which.equals("numGames")) {
            db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").child("backwords").child("unlocked").setValue(true);
            System.out.println("more than 5 games played ----- backwords unlocked");
        }
        if (user.getValue().madeLeaderBoard && which.equals("leaderBoards")) {
            db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").child("fuddify").child("unlocked").setValue(true);
            db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("value").child("madeLeaderBoard").setValue(true);
            System.out.println("made leaderboard ------ fuddify unlocked");
        }
        if (user.getValue().perfectLeaderBoard && which.equals("leaderBoards")) {
            db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").child("pig latin").child("unlocked").setValue(true);
            db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("value").child("perfectLeaderBoard").setValue(true);
            System.out.println("made perfect leaderboard ------ pig latin unlocked");

        }

    }

    public void getUnlocked(MutableLiveData<User> user) {
        Task<DataSnapshot> unlocked = db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").get();
        unlocked.addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                DataSnapshot snapshot = unlocked.getResult();
//                System.out.println(snapshot);
                for (DataSnapshot ds:snapshot.getChildren()) {
//                    System.out.println(ds);
                    userUnlocked.add(ds.getValue(Unlockable.class));
                }
            }
        });
    }


}

