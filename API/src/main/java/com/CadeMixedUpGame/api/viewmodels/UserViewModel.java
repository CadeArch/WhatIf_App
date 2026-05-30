package com.CadeMixedUpGame.api.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.models.Unlockable;
import com.CadeMixedUpGame.api.models.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class UserViewModel extends ViewModel {
    public MutableLiveData<String> signInMessage = new MutableLiveData<String>();
    public MutableLiveData<String> databaseMessage = new MutableLiveData<String>();
    public MutableLiveData<GamePhase> gamePhase = new MutableLiveData<GamePhase>();
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
    private String listenerRoom;
    private ChildEventListener hostListener;
    private String hostListenerPath;

    public UserViewModel() {
        this(FirebaseDatabase.getInstance().getReference(), FirebaseAuth.getInstance(), true);
    }

    public UserViewModel(DatabaseReference db, FirebaseAuth auth) {
        this(db, auth, false);
    }

    public UserViewModel(DatabaseReference db, FirebaseAuth auth, boolean listenForAuthChanges) {
        this.db = db;
        gamePhase.setValue(GamePhase.LOBBY);
        if (users == null) {
            users = new ObservableArrayList<User>();
        }

        this.auth = auth;
        if (this.auth == null || !listenForAuthChanges) {
            return;
        }
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
        if (db == null) {
            db = FirebaseDatabase.getInstance().getReference();
        }

        localRandIf = "";
        gamePhase.setValue(GamePhase.LOBBY);
        onWriteThen = false;
        onWriteIf = false;
        onWaitingForHost = false;
        playing = false;
        setUsers(new ObservableArrayList<User>());

        AppLog.i(AppLog.GAME_FLOW, "Resetting local user game state");
        User currentUser = user.getValue();
        if (currentUser == null) {
            return;
        }
        currentUser.setIfFinished(false);
        currentUser.setIfSentence("");
        currentUser.setThenFinished(false);
        currentUser.setThenSentence("");
        currentUser.setHostStarted(false);
        currentUser.setHostPlayedAgain("");

    }



    public void signUp(String email, String password, String userName) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    signInMessage.setValue("account created");

                    //setting the username of the account to whatever they put in the box when signing up
                    FirebaseUser fBuser = auth.getCurrentUser();
                    if (fBuser == null) {
                        signInMessage.setValue("Error");
                        return;
                    }
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
                    String message = task.getException() == null ? "" : task.getException().getMessage();
                    if (message.equals("The email address is badly formatted.")) {
                        signInMessage.setValue("Email Badly Formatted");
                        AppLog.w(AppLog.AUTH, "Sign up failed: badly formatted email");
                    } else if (message.equals("The given password is invalid. [ Password should be at least 6 characters ]")) {
                        signInMessage.setValue("Weak Password");
                        AppLog.w(AppLog.AUTH, "Sign up failed: weak password");
                    } else if (message.equals("The email address is already in use by another account.")) {
                        signInMessage.setValue("Email in Use");
                        AppLog.w(AppLog.AUTH, "Sign up failed: email already in use");
                    } else {
                        AppLog.w(AppLog.AUTH, "Sign up failed: " + message);
                        signInMessage.setValue("Error");
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
                    AppLog.i(AppLog.AUTH, "Sign in succeeded");
                    FirebaseUser fbUser = auth.getCurrentUser();
                    if (fbUser == null) {
                        signInMessage.setValue("Error");
                        return;
                    }
                    localName = fbUser.getDisplayName();
                    buildUser(fbUser, localName, true);
                    getGamesPlayed(user, false);
//                    System.out.println("UPON SIGN IN - user has played " + user.getValue().gamesPlayed + " matches");
                    signInMessage.setValue("Sign in Complete");

                } else {
                    // If sign in fails, display a message to the user.
//                    System.out.println("EXEPTION----------------------- " + task.getException().getMessage());
                    String message = task.getException() == null ? "" : task.getException().getMessage();
                    if (message.equals("The password is invalid or the user does not have a password.")) {
                        signInMessage.setValue("Invalid Password");
                        AppLog.w(AppLog.AUTH, "Sign in failed: invalid password");
                    }
                    else if (message.equals("There is no user record corresponding to this identifier. The user may have been deleted.")) {
                        signInMessage.setValue("Invalid Email");
                        AppLog.w(AppLog.AUTH, "Sign in failed: invalid email");

                    }
                    else if (message.equals("The email address is badly formatted.")) {
                        signInMessage.setValue("Email Badly Formatted");
                        AppLog.w(AppLog.AUTH, "Sign in failed: badly formatted email");
                    }
                    else {
                        signInMessage.setValue("User Disabled");
                        AppLog.w(AppLog.AUTH, "Sign in failed: user disabled or unknown auth error");
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
        removePlayersListenerOnDB();
        removeHostListener();
    }

    public void removePlayersListenerOnDB() {
        if (listener != null && listenerRoom != null) {
            AppLog.i(AppLog.FIREBASE, "Removing players listener for room=" + listenerRoom);
            db.child("rooms").child(listenerRoom).child("players").removeEventListener(listener);
            listener = null;
            listenerRoom = null;
        }
    }

    // used in create and join game to see players that join room from firebase
    public void loadUsers(String gameRoom) {
        if (gameRoom == null || gameRoom.length() == 0) {
            AppLog.w(AppLog.ROOM, "loadUsers skipped: missing room id");
            return;
        }
        if (listener != null && gameRoom.equals(listenerRoom)) {
            AppLog.d(AppLog.FIREBASE, "Players listener already active for room=" + gameRoom);
            return;
        }
        removeListenerOnDB();
        listenerRoom = gameRoom;
        AppLog.i(AppLog.FIREBASE, "Attaching players listener for room=" + gameRoom);
        listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                addUsersFromSnapshot(snapshot, "added");
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                updateUsersFromSnapshot(snapshot);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                AppLog.e(AppLog.FIREBASE, "Players listener cancelled: " + error.getCode() + " " + error.getMessage());
            }
        };
        db.child("rooms").child(gameRoom).child("players").addChildEventListener(listener);
    }

    private void addUsersFromSnapshot(@NonNull DataSnapshot snapshot, String reason) {
        User user = readPlayerSnapshot(snapshot);
        int added = 0;
        if (user != null) {
            users.remove(user);
            users.add(user);
            added = 1;
        }
        AppLog.d(AppLog.ROOM, "Players snapshot " + reason + ": added=" + added + ", total=" + users.size());
    }

    private void updateUsersFromSnapshot(@NonNull DataSnapshot snapshot) {
        User user = readPlayerSnapshot(snapshot);
        int changed = 0;
        if (shouldReplaceUser(user)) {
            users.remove(user);
            users.add(user);
            changed = 1;
        }
        AppLog.d(AppLog.ROOM, "Players snapshot changed: updated=" + changed + ", total=" + users.size());
    }

    private User readPlayerSnapshot(@NonNull DataSnapshot snapshot) {
        try {
            DataSnapshot valueSnapshot = snapshot.child("value");
            if (valueSnapshot.exists()) {
                return valueSnapshot.getValue(User.class);
            }
            return snapshot.getValue(User.class);
        }
        catch (DatabaseException e) {
            AppLog.e(AppLog.FIREBASE, "Could not parse player snapshot key=" + snapshot.getKey(), e);
            return null;
        }
    }

    private boolean shouldReplaceUser(User user) {
        if (user == null || user.ifSentence == null || user.thenSentence == null) {
            return false;
        }
        return user.ifSentence.length() > 0 || user.thenSentence.length() > 0;
    }

    //key to update values in firebase
    public void hostStarted(User user) {
        if (user == null || user.gameRoom == null || user.gameRoom.length() == 0) {
            AppLog.w(AppLog.ROOM, "hostStarted skipped: missing user or game room");
            return;
        }
        //updating the status that the host has started the game
        db.child("rooms").child(user.gameRoom).child("players").child(user.userName+ "-" + user.userID).child("value").child("hostStarted").setValue(true)
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Host started write succeeded room=" + user.gameRoom))
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not start the game. Check your connection and try again.");
                    AppLog.e(AppLog.FIREBASE, "Host started write failed room=" + user.gameRoom, e);
                });

    }

    public void hostPlayedAgain(User user) {
        if (user == null || user.gameRoom == null || user.gameRoom.length() == 0 || user.hostPlayedAgain == null) {
            AppLog.w(AppLog.ROOM, "hostPlayedAgain skipped: missing user, game room, or play-again state");
            return;
        }
        String value = "";
        if (user.hostPlayedAgain.equals("yes") || user.hostPlayedAgain.equals("no")) {
            value = user.hostPlayedAgain;
        }
        AppLog.i(AppLog.FIREBASE, "Writing hostPlayedAgain=" + value + " room=" + user.gameRoom);
        db.child("rooms").child(user.gameRoom).child("players").child(user.userName+ "-" + user.userID).child("value").child("hostPlayedAgain").setValue(value)
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "hostPlayedAgain write succeeded room=" + user.gameRoom))
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not update play-again state. Check your connection and try again.");
                    AppLog.e(AppLog.FIREBASE, "hostPlayedAgain write failed room=" + user.gameRoom, e);
                });
    }

    public void listenToHost(MutableLiveData<User> host) {
        //todo in one use case this produced a null pointer somewhere and killed for a non host player
        if (host == null || host.getValue() == null) {
            return;
        }
        User hostUser = host.getValue();
        String path = "rooms/" + hostUser.gameRoom + "/players/" + hostUser.userName + "-" + hostUser.userID;
        if (path.equals(hostListenerPath) && hostListener != null) {
            return;
        }
        removeHostListener();
        hostListenerPath = path;
        AppLog.i(AppLog.FIREBASE, "Attaching host listener room=" + hostUser.gameRoom + ", host=" + hostUser.userName);
        hostListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                User theChanged = snapshot.getValue(User.class);
                if (theChanged == null || getUser().getValue() == null) {
                    return;
                }
//                System.out.println("Host Changed Values in DB: " + theChanged.userName + theChanged.host + theChanged.hostStarted);
//                System.out.println("Host played again in DB: " + theChanged.hostPlayedAgain);
                if (theChanged.hostStarted) {
                    getUser().getValue().hostStarted = true;
                    getUser().setValue(getUser().getValue());
                }

                if ("yes".equals(theChanged.hostPlayedAgain)) {
                    getUser().getValue().hostPlayedAgain = "yes";
                    getUser().setValue(getUser().getValue());
                }
                if ("no".equals(theChanged.hostPlayedAgain)) {
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
                AppLog.e(AppLog.FIREBASE, "Host listener cancelled: " + error.getMessage());
            }
        };
        db.child("rooms").child(hostUser.gameRoom).child("players").child(hostUser.userName+ "-" + hostUser.userID).addChildEventListener(hostListener);
    }

    private void removeHostListener() {
        if (hostListener != null && hostListenerPath != null) {
            AppLog.i(AppLog.FIREBASE, "Removing host listener path=" + hostListenerPath);
            db.child(hostListenerPath).removeEventListener(hostListener);
            hostListener = null;
            hostListenerPath = null;
        }
    }

    public void getMadeLeaderBoard(MutableLiveData<User> user) {
        Task<DataSnapshot> madeLeader = db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("value").child("madeLeaderBoard").get();
        madeLeader.addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (!task.isSuccessful()) {
                    AppLog.e(AppLog.FIREBASE, "Failed to load madeLeaderBoard", task.getException());
                    return;
                }
                DataSnapshot snapshot = madeLeader.getResult();
                Boolean inDB = snapshot.getValue(Boolean.class);
                if (inDB == null || user.getValue() == null) {
                    return;
                }
                user.getValue().madeLeaderBoard = inDB;
                AppLog.d(AppLog.AUTH, "Loaded madeLeaderBoard=" + user.getValue().madeLeaderBoard);
            }
        });
    }

    public void getMadePerfectLeaderBoard(MutableLiveData<User> user) {
        Task<DataSnapshot> madePerfectLeader = db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("value").child("perfectLeaderBoard").get();
        madePerfectLeader.addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (!task.isSuccessful()) {
                    AppLog.e(AppLog.FIREBASE, "Failed to load perfectLeaderBoard", task.getException());
                    return;
                }
                DataSnapshot snapshot = madePerfectLeader.getResult();
                Boolean inDB = snapshot.getValue(Boolean.class);
                if (inDB == null || user.getValue() == null) {
                    return;
                }
                user.getValue().perfectLeaderBoard = inDB;
                AppLog.d(AppLog.AUTH, "Loaded perfectLeaderBoard=" + user.getValue().perfectLeaderBoard);
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
            AppLog.d(AppLog.ROOM, "User id already set for " + user.getValue().userName);
        }
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName + "-" + user.getValue().userID).setValue(user).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()){
                    AppLog.i(AppLog.FIREBASE, "Pushed player to room=" + user.getValue().gameRoom);
                }
                else {
                    databaseMessage.setValue("Could not join the room. Check your connection and try again.");
                    AppLog.e(AppLog.FIREBASE, "Failed to push player to room=" + user.getValue().gameRoom, task.getException());
                }
            }
        });
    }

    public void deleteRoom(User userLeft) {
        AppLog.i(AppLog.ROOM, "Deleting room=" + userLeft.gameRoom);
        db.child("rooms").child(userLeft.gameRoom).removeValue()
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Room deleted room=" + userLeft.gameRoom))
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not delete the room. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed deleting room=" + userLeft.gameRoom, e);
                });
    }

    public void nurfAllUsers() {
        AppLog.i(AppLog.ROOM, "Deleting all players in room=" + myRoom);
        db.child("rooms").child(myRoom).child("players").removeValue()
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Players cleared room=" + myRoom))
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not reset the room players. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed deleting players room=" + myRoom, e);
                });
    }
    public void deleteVotesAndVotingItems() {
        AppLog.i(AppLog.VOTE, "Deleting votes and voting items room=" + myRoom);
        db.child("rooms").child(myRoom).child("votes").removeValue()
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not clear votes. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed deleting votes room=" + myRoom, e);
                });
        db.child("rooms").child(myRoom).child("votingItems").removeValue()
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not clear voting items. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed deleting voting items room=" + myRoom, e);
                });

    }

    public void pushAccountPlayer(MutableLiveData<User> user) {
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).setValue(user)
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed pushing account player", e));
    }

    public void pushIf(MutableLiveData<User> user) {

        AppLog.i(AppLog.FIREBASE, "Submitting If room=" + user.getValue().gameRoom);
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName+ "-" + user.getValue().userID).child("value").child("ifSentence").setValue(user.getValue().ifSentence)
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not submit your question. Check your connection and try again.");
                    AppLog.e(AppLog.FIREBASE, "Failed submitting If sentence", e);
                });
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName+ "-" + user.getValue().userID).child("value").child("ifFinished").setValue(user.getValue().ifFinished)
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not mark your question complete. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed submitting If finished", e);
                });
    }

    public void pushThen(MutableLiveData<User> user) {

        AppLog.i(AppLog.FIREBASE, "Submitting Then room=" + user.getValue().gameRoom);
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName+ "-" + user.getValue().userID).child("value").child("thenSentence").setValue(user.getValue().thenSentence)
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not submit your response. Check your connection and try again.");
                    AppLog.e(AppLog.FIREBASE, "Failed submitting Then sentence", e);
                });
        db.child("rooms").child(user.getValue().gameRoom).child("players").child(user.getValue().userName+ "-" + user.getValue().userID).child("value").child("thenFinished").setValue(user.getValue().thenFinished)
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not mark your response complete. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed submitting Then finished", e);
                });
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
                if (!task.isSuccessful()) {
                    AppLog.e(AppLog.FIREBASE, "Failed to load gamesPlayed", task.getException());
                    return;
                }
                DataSnapshot snapshot = gamesPlayed.getResult();
                Integer totalPlayed = snapshot.getValue(Integer.class);
                if (totalPlayed == null || user.getValue() == null) {
                    return;
                }
                user.getValue().gamesPlayed = totalPlayed;
                AppLog.d(AppLog.AUTH, "Loaded gamesPlayed=" + user.getValue().gamesPlayed);
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
            AppLog.i(AppLog.AUTH, "Unlocked backwords voice");
        }
        if (user.getValue().madeLeaderBoard && which.equals("leaderBoards")) {
            db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").child("fuddify").child("unlocked").setValue(true);
            db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("value").child("madeLeaderBoard").setValue(true);
            AppLog.i(AppLog.AUTH, "Unlocked fuddify voice");
        }
        if (user.getValue().perfectLeaderBoard && which.equals("leaderBoards")) {
            db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").child("pig latin").child("unlocked").setValue(true);
            db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("value").child("perfectLeaderBoard").setValue(true);
            AppLog.i(AppLog.AUTH, "Unlocked pig latin voice");

        }

    }

    public void getUnlocked(MutableLiveData<User> user) {
        Task<DataSnapshot> unlocked = db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").get();
        unlocked.addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (!task.isSuccessful()) {
                    AppLog.e(AppLog.FIREBASE, "Failed to load unlockables", task.getException());
                    return;
                }
                DataSnapshot snapshot = unlocked.getResult();
//                System.out.println(snapshot);
                userUnlocked.clear();
                for (DataSnapshot ds:snapshot.getChildren()) {
//                    System.out.println(ds);
                    Unlockable unlockable = ds.getValue(Unlockable.class);
                    if (unlockable != null) {
                        userUnlocked.add(unlockable);
                    }
                }
            }
        });
    }

    @Override
    protected void onCleared() {
        removeListenerOnDB();
        super.onCleared();
    }

}

