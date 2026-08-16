package com.CadeMixedUpGame.api.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.AuthErrorPolicy;
import com.CadeMixedUpGame.api.ChildEventListenerAdapter;
import com.CadeMixedUpGame.api.UnlockPolicy;
import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.models.Unlockable;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.repositories.AccountProgressRepository;
import com.CadeMixedUpGame.api.repositories.RoomPresenceRepository;
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
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class UserViewModel extends ViewModel {
    public MutableLiveData<String> signInMessage = new MutableLiveData<String>();
    public MutableLiveData<String> databaseMessage = new MutableLiveData<String>();
    public MutableLiveData<String> hostDisconnectedMessage = new MutableLiveData<String>();
    public MutableLiveData<Long> hostDisconnectedAt = new MutableLiveData<Long>(0L);
    public MutableLiveData<Long> hostLastSeenAt = new MutableLiveData<Long>(0L);
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
    public String pendingHomeSnackbar = "";
    DatabaseReference db;
    public String myRoom;
    FirebaseAuth auth;
    MutableLiveData<User> user = new MutableLiveData<User>();
    public ObservableArrayList<Unlockable> userUnlocked = new ObservableArrayList<Unlockable>();
    private final AccountProgressRepository accountProgress;
    public ChildEventListener listener;
    private String listenerRoom;
    private final RoomPresenceRepository presence;
    private DatabaseReference onDisconnectPlayerRef;
    private String onDisconnectPlayerPath;
    private DatabaseReference onDisconnectHostConnectionRef;
    private String onDisconnectHostConnectionRoom;
    private ValueEventListener hostConnectionListener;
    private String hostConnectionListenerRoom;

    public UserViewModel() {
        this(FirebaseDatabase.getInstance().getReference(), FirebaseAuth.getInstance(), true);
    }

    public UserViewModel(DatabaseReference db, FirebaseAuth auth) {
        this(db, auth, false);
    }

    public UserViewModel(DatabaseReference db, FirebaseAuth auth, boolean listenForAuthChanges) {
        this.db = db;
        this.accountProgress = new AccountProgressRepository(db, userUnlocked);
        this.presence = new RoomPresenceRepository(db, user, hostDisconnectedAt, hostLastSeenAt,
                databaseMessage, hostDisconnectedMessage);
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

                    // setting username in Firebase account
                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                            .setDisplayName(userName).build();

                    fBuser.updateProfile(profileUpdates).addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
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
                    String shown = AuthErrorPolicy.signUpMessageFor(task.getException());
                    signInMessage.setValue(shown);
                    AppLog.w(AppLog.AUTH, "Sign up failed: " + shown
                            + " (" + (task.getException() == null ? "no exception" : task.getException().getMessage()) + ")");
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
                    signInMessage.setValue("Sign in Complete");

                } else {
                    String shown = AuthErrorPolicy.signInMessageFor(task.getException());
                    signInMessage.setValue(shown);
                    AppLog.w(AppLog.AUTH, "Sign in failed: " + shown
                            + " (" + (task.getException() == null ? "no exception" : task.getException().getMessage()) + ")");
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

    public void deleteAccount() {
        FirebaseUser firebaseUser = auth == null ? null : auth.getCurrentUser();
        User currentUser = user.getValue();
        if (firebaseUser == null || currentUser == null || currentUser.uid == null || currentUser.uid.length() == 0) {
            signInMessage.setValue("Sign in again before deleting your account.");
            AppLog.w(AppLog.AUTH, "Delete account skipped: missing current Firebase user");
            return;
        }

        removeCurrentPlayerFromRoom(() -> deleteAccountData(currentUser, firebaseUser));
    }

    private void deleteAccountData(User accountUser, FirebaseUser firebaseUser) {
        db.child("AccountPlayers").child(accountUser.uid).removeValue()
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.FIREBASE, "Account profile data deleted uid=" + accountUser.uid);
                    deleteFirebaseAccount(firebaseUser);
                })
                .addOnFailureListener(e -> {
                    signInMessage.setValue("Could not delete account data. Check your connection and try again.");
                    AppLog.e(AppLog.FIREBASE, "Failed deleting account profile data uid=" + accountUser.uid, e);
                });
    }

    private void deleteFirebaseAccount(FirebaseUser firebaseUser) {
        firebaseUser.delete()
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.AUTH, "Firebase account deleted");
                    signInMessage.setValue("Account deleted.");
                    user.setValue(null);
                })
                .addOnFailureListener(e -> {
                    signInMessage.setValue("Sign in again before deleting your account.");
                    AppLog.e(AppLog.AUTH, "Failed deleting Firebase account", e);
                });
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
        removeHostConnectionListener();
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
        listener = new ChildEventListenerAdapter(AppLog.FIREBASE, "Players listener cancelled") {
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
                User removedUser = readPlayerSnapshot(snapshot);
                if (removedUser != null) {
                    users.remove(removedUser);
                    AppLog.d(AppLog.ROOM, "Player removed: total=" + users.size());
                    handleRemovedHost(removedUser);
                }
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
            handleHostConnectionState(user);
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
            handleHostConnectionState(user);
            changed = 1;
        }
        AppLog.d(AppLog.ROOM, "Players snapshot changed: updated=" + changed + ", total=" + users.size());
    }

    private User readPlayerSnapshot(@NonNull DataSnapshot snapshot) {
        try {
            DataSnapshot valueSnapshot = snapshot.child("value");
            if (isUserSnapshot(valueSnapshot)) {
                return valueSnapshot.getValue(User.class);
            }
            if (!isUserSnapshot(snapshot)) {
                AppLog.w(AppLog.FIREBASE, "Skipping non-user player child key=" + snapshot.getKey());
                return null;
            }
            return snapshot.getValue(User.class);
        }
        catch (DatabaseException e) {
            AppLog.e(AppLog.FIREBASE, "Could not parse player snapshot key=" + snapshot.getKey(), e);
            return null;
        }
    }

    private boolean isUserSnapshot(DataSnapshot snapshot) {
        return snapshot != null
                && snapshot.exists()
                && snapshot.hasChildren()
                && (snapshot.hasChild("userName") || snapshot.hasChild("userID") || snapshot.hasChild("uid"));
    }

    private boolean shouldReplaceUser(User user) {
        return user != null;
    }

    private void handleRemovedHost(User removedUser) {
        if (removedUser == null || !removedUser.host) {
            return;
        }
        host.setValue(null);
        AppLog.w(AppLog.ROOM, "Host removed from room=" + removedUser.gameRoom + ", host=" + removedUser.userName);
        // Deliberately does NOT set hostDisconnectedMessage here. This listener fires on any
        // removal of the host's player node, including the legitimate nurfAllUsers() wipe done as
        // part of resetting a room for "Play Again" - a guest whose players listener is still
        // attached from a previous screen (EndFrag.onViewCreated is what detaches it, and a guest
        // who hasn't reached EndFrag yet still has it live) would otherwise be falsely sent home
        // mid-replay. Real host departures are already covered by two purpose-built, correctly
        // timed paths: the explicit-leave replayState="no" flow, and the hostConnection/heartbeat
        // flow driving HostDisconnectScheduler - both grace-period-protected, unlike this instant
        // player-list-removal signal.
    }

    public void clearHostDisconnectedMessage() {
        hostDisconnectedMessage.setValue("");
    }

    public void clearHostDisconnectedAt() {
        hostDisconnectedAt.setValue(0L);
    }

    public void clearHostLastSeenAt() {
        hostLastSeenAt.setValue(0L);
    }

    public void clearLocalRoomIdentity() {
        removeListenerOnDB();
        cancelOnDisconnectCleanup();
        User currentUser = user.getValue();
        if (currentUser != null) {
            currentUser.gameRoom = "";
            currentUser.host = false;
            currentUser.playAgain = false;
            currentUser.hostPlayedAgain = "";
            currentUser.connected = true;
            currentUser.disconnectedAt = 0L;
        }
        myRoom = "";
        host.setValue(null);
        clearHostDisconnectedMessage();
        clearHostDisconnectedAt();
        clearHostLastSeenAt();
        AppLog.i(AppLog.ROOM, "Cleared local room identity");
    }

    private void handleHostConnectionState(User changedUser) {
        if (changedUser == null || !changedUser.host) {
            return;
        }
        User currentUser = user.getValue();
        if (currentUser == null || currentUser.host) {
            return;
        }
        if (Boolean.FALSE.equals(changedUser.connected)) {
            long disconnectedAtValue = changedUser.disconnectedAt == null ? System.currentTimeMillis() : changedUser.disconnectedAt;
            AppLog.w(AppLog.ROOM, "Host marked disconnected room=" + changedUser.gameRoom + ", disconnectedAt=" + disconnectedAtValue);
            hostDisconnectedAt.setValue(disconnectedAtValue);
            return;
        }
        if (hostDisconnectedAt.getValue() != null && hostDisconnectedAt.getValue() > 0L) {
            AppLog.i(AppLog.ROOM, "Host reconnected before grace timer expired room=" + changedUser.gameRoom);
        }
        hostDisconnectedAt.setValue(0L);
    }

    private DatabaseReference playerRef(User user) {
        return db.child("rooms").child(user.gameRoom).child("players").child(user.userName + "-" + user.userID);
    }

    private String playerPath(User user) {
        if (user == null || user.gameRoom == null || user.userName == null) {
            return "";
        }
        return "rooms/" + user.gameRoom + "/players/" + user.userName + "-" + user.userID;
    }

    private DatabaseReference accountPlayerRef(User user) {
        return db.child("AccountPlayers").child(user.uid).child(user.userName);
    }

    public void removeCurrentPlayerFromRoom() {
        removeCurrentPlayerFromRoom(null);
    }

    public void removeCurrentPlayerFromRoom(Runnable onComplete) {
        User currentUser = user.getValue();
        if (currentUser == null || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0 || currentUser.userName == null) {
            AppLog.w(AppLog.ROOM, "removeCurrentPlayerFromRoom skipped: missing current user or room");
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        String room = currentUser.gameRoom;
        String playerKey = currentUser.userName + "-" + currentUser.userID;
        AppLog.i(AppLog.ROOM, "Removing current player from room=" + room + ", playerKey=" + playerKey);
        playerRef(currentUser).removeValue()
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.FIREBASE, "Current player removed room=" + room + ", playerKey=" + playerKey);
                    cancelOnDisconnectCleanup();
                    deleteRoomIfPlayersEmpty(room, onComplete);
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not leave the room cleanly. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed removing current player room=" + room + ", playerKey=" + playerKey, e);
                });
    }

    /** Called only after a non-host player removes themself (the host leaving already goes
     * through its own explicit deleteRoom() path in EndFrag/CreateGameFrag/MainActivity) - if
     * that was the last remaining player, the room would otherwise sit around forever with no
     * one left to clean it up. */
    private void deleteRoomIfPlayersEmpty(String room, Runnable onComplete) {
        db.child("rooms").child(room).child("players").get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().exists()) {
                        AppLog.i(AppLog.ROOM, "Room empty after player left, deleting room=" + room);
                        db.child("rooms").child(room).removeValue()
                                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed deleting emptied room=" + room, e));
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
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
        playerRef(user).child("hostPlayedAgain").setValue(value)
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "hostPlayedAgain write succeeded room=" + user.gameRoom))
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not update play-again state. Check your connection and try again.");
                    AppLog.e(AppLog.FIREBASE, "hostPlayedAgain write failed room=" + user.gameRoom, e);
                });
    }

    public void getMadeLeaderBoard(MutableLiveData<User> user) {
        Task<DataSnapshot> madeLeader = accountPlayerRef(user.getValue()).get();
        madeLeader.addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (!task.isSuccessful()) {
                    AppLog.e(AppLog.FIREBASE, "Failed to load madeLeaderBoard", task.getException());
                    return;
                }
                DataSnapshot snapshot = madeLeader.getResult();
                Boolean inDB = readAccountBoolean(snapshot, "madeLeaderBoard");
                if (inDB == null || user.getValue() == null) {
                    return;
                }
                user.getValue().madeLeaderBoard = inDB;
                AppLog.d(AppLog.AUTH, "Loaded madeLeaderBoard=" + user.getValue().madeLeaderBoard);
            }
        });
    }

    public void getMadePerfectLeaderBoard(MutableLiveData<User> user) {
        Task<DataSnapshot> madePerfectLeader = accountPlayerRef(user.getValue()).get();
        madePerfectLeader.addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (!task.isSuccessful()) {
                    AppLog.e(AppLog.FIREBASE, "Failed to load perfectLeaderBoard", task.getException());
                    return;
                }
                DataSnapshot snapshot = madePerfectLeader.getResult();
                Boolean inDB = readAccountBoolean(snapshot, "perfectLeaderBoard");
                if (inDB == null || user.getValue() == null) {
                    return;
                }
                user.getValue().perfectLeaderBoard = inDB;
                AppLog.d(AppLog.AUTH, "Loaded perfectLeaderBoard=" + user.getValue().perfectLeaderBoard);
            }
        });
    }

    private Boolean readAccountBoolean(DataSnapshot snapshot, String key) {
        Boolean direct = snapshot.child(key).getValue(Boolean.class);
        if (direct != null) {
            return direct;
        }
        return snapshot.child("value").child(key).getValue(Boolean.class);
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
        pushPerson(user, null);
    }

    public void pushPerson(MutableLiveData<User> user, Runnable onSuccess) {
        if (user == null || user.getValue() == null || user.getValue().gameRoom == null || user.getValue().gameRoom.length() == 0 || user.getValue().userName == null) {
            databaseMessage.setValue("Could not join the room. Missing player or room information.");
            AppLog.w(AppLog.ROOM, "pushPerson skipped: missing player or room data");
            return;
        }
        int userID = (int)(Math.random() * 100000);
        if (user.getValue().userID == 0) {
            user.getValue().userID = userID;
//            System.out.println("UserID set -------------------");
        }
        else {
            AppLog.d(AppLog.ROOM, "User id already set for " + user.getValue().userName);
        }
        User currentUser = user.getValue();
        markUserConnectedLocally(currentUser);
        DatabaseReference ref = playerRef(currentUser);
        ref.setValue(currentUser).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()){
                    AppLog.i(AppLog.FIREBASE, "Pushed player to room=" + currentUser.gameRoom);
                    registerOnDisconnectCleanup(currentUser, ref);
                    listenToHostConnection(currentUser.gameRoom);
                    markHostConnectionConnectedIfNeeded(currentUser);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                }
                else {
                    databaseMessage.setValue("Could not join the room. Check your connection and try again.");
                    AppLog.e(AppLog.FIREBASE, "Failed to push player to room=" + currentUser.gameRoom, task.getException());
                }
            }
        });
    }

    // Presence, onDisconnect registration and the host heartbeat live in RoomPresenceRepository;
    // these forward so call sites and the characterization tests are untouched.
    private void registerOnDisconnectCleanup(User user, DatabaseReference ref) {
        presence.registerOnDisconnectCleanup(user, ref);
    }

    private void cancelOnDisconnectCleanup() {
        presence.cancelOnDisconnectCleanup();
    }

    private void markUserConnectedLocally(User user) {
        presence.markUserConnectedLocally(user);
    }

    private void markHostConnectionConnectedIfNeeded(User currentUser) {
        presence.markHostConnectionConnectedIfNeeded(currentUser);
    }

    public void markCurrentPlayerConnected() {
        presence.markCurrentPlayerConnected();
    }

    public void writeHostHeartbeat() {
        presence.writeHostHeartbeat();
    }

    public void listenToHostConnection(String room) {
        presence.listenToHostConnection(room);
    }

    public void removeHostConnectionListener() {
        presence.removeHostConnectionListener();
    }


    public void deleteRoom(User userLeft) {
        deleteRoom(userLeft, null);
    }

    public void deleteRoom(User userLeft, Runnable onSuccess) {
        AppLog.i(AppLog.ROOM, "Deleting room=" + userLeft.gameRoom);
        db.child("rooms").child(userLeft.gameRoom).removeValue()
                .addOnSuccessListener(unused -> {
                    cancelOnDisconnectCleanup();
                    AppLog.i(AppLog.FIREBASE, "Room deleted room=" + userLeft.gameRoom);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not delete the room. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed deleting room=" + userLeft.gameRoom, e);
                });
    }

    public void nurfAllUsers() {
        nurfAllUsers(null);
    }

    public void nurfAllUsers(Runnable onSuccess) {
        AppLog.i(AppLog.ROOM, "Deleting all players in room=" + myRoom);
        db.child("rooms").child(myRoom).child("players").removeValue()
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.FIREBASE, "Players cleared room=" + myRoom);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
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
        accountPlayerRef(user.getValue()).setValue(user.getValue())
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed pushing account player", e));
    }

    public void pushIf(MutableLiveData<User> user) {
        pushIf(user, null);
    }

    public void pushIf(MutableLiveData<User> user, Runnable onSuccess) {
        if (!canSubmitSentence(user)) {
            databaseMessage.setValue("Could not submit. Missing player or room information.");
            AppLog.w(AppLog.ROOM, "pushIf skipped: missing player or room data");
            return;
        }
        User currentUser = user.getValue();
        AppLog.i(AppLog.FIREBASE, "Submitting If room=" + currentUser.gameRoom);
        playerRef(currentUser).updateChildren(sentenceUpdate("ifSentence", currentUser.ifSentence, "ifFinished", currentUser.ifFinished))
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.FIREBASE, "If submitted room=" + currentUser.gameRoom);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not submit your question. Check your connection and tap submit again.");
                    AppLog.e(AppLog.FIREBASE, "Failed submitting If", e);
                });
    }

    public void pushThen(MutableLiveData<User> user) {
        pushThen(user, null);
    }

    public void pushThen(MutableLiveData<User> user, Runnable onSuccess) {
        if (!canSubmitSentence(user)) {
            databaseMessage.setValue("Could not submit. Missing player or room information.");
            AppLog.w(AppLog.ROOM, "pushThen skipped: missing player or room data");
            return;
        }
        User currentUser = user.getValue();
        AppLog.i(AppLog.FIREBASE, "Submitting Then room=" + currentUser.gameRoom);
        playerRef(currentUser).updateChildren(sentenceUpdate("thenSentence", currentUser.thenSentence, "thenFinished", currentUser.thenFinished))
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.FIREBASE, "Then submitted room=" + currentUser.gameRoom);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not submit your response. Check your connection and tap submit again.");
                    AppLog.e(AppLog.FIREBASE, "Failed submitting Then", e);
                });
    }

    private boolean canSubmitSentence(MutableLiveData<User> user) {
        return user != null
                && user.getValue() != null
                && user.getValue().gameRoom != null
                && user.getValue().gameRoom.length() > 0
                && user.getValue().userName != null;
    }

    private Map<String, Object> sentenceUpdate(String sentenceKey, String sentence, String finishedKey, Boolean finished) {
        Map<String, Object> update = new HashMap<String, Object>();
        update.put(sentenceKey, sentence == null ? "" : sentence);
        update.put(finishedKey, Boolean.TRUE.equals(finished));
        return update;
    }

    // Account progression (games played, unlockables, leaderboard flags) lives in
    // AccountProgressRepository; these forward so call sites and tests are untouched.
    public void fillUnlockables(MutableLiveData<User> user) {
        accountProgress.fillUnlockables(user);
    }

    public void fillGamesPlayed(MutableLiveData<User> user) {
        accountProgress.fillGamesPlayed(user);
    }

    public void getGamesPlayed(MutableLiveData<User> user, boolean increment) {
        accountProgress.getGamesPlayed(user, increment);
    }

    public void incrementGamesPlayed(MutableLiveData<User> user) {
        accountProgress.incrementGamesPlayed(user);
    }

    public void unlockEarnedVoices(MutableLiveData<User> user) {
        accountProgress.unlockEarnedVoices(user);
    }

    public void getUnlocked(MutableLiveData<User> user) {
        accountProgress.getUnlocked(user);
    }


    @Override
    protected void onCleared() {
        removeListenerOnDB();
        cancelOnDisconnectCleanup();
        removeHostConnectionListener();
        super.onCleared();
    }

}

