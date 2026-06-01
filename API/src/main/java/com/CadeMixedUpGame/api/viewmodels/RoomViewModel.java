package com.CadeMixedUpGame.api.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameLogic;
import com.CadeMixedUpGame.api.RoomCreationPolicy;
import com.CadeMixedUpGame.api.models.RoundAssignment;
import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.repositories.FirebaseGameRepository;
import com.CadeMixedUpGame.api.repositories.GameRepository;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RoomViewModel extends ViewModel {
    ObservableArrayList<Room> rooms;
    Room room;
    public ArrayList<String> roomNames = new ArrayList<>();
    public DatabaseReference db;
    private final GameRepository repository;
    // removed capitol I and lowercase l because they were ambiguous with the font i am using
    String allChars = "a b c d e f g h i j k l m n o p q r s t u v w x y z A B C D E F G H I J K L M N O P Q R S T U V W X Y Z 0 1 2 3 4 5 6 7 8 9";
    String[] usableCharacter;
    public MutableLiveData<Boolean> inProgress = new MutableLiveData<Boolean>();
    public MutableLiveData<RoomJoinState> roomJoinState = new MutableLiveData<RoomJoinState>(RoomJoinState.IDLE);
    public MutableLiveData<String> databaseMessage = new MutableLiveData<String>();
    public MutableLiveData<Integer> activeReaderIndex = new MutableLiveData<Integer>(0);
    public MutableLiveData<Boolean> readingComplete = new MutableLiveData<Boolean>(false);
    public MutableLiveData<String> replayState = new MutableLiveData<String>("");
    public MutableLiveData<RoundAssignment> currentAssignment = new MutableLiveData<RoundAssignment>();
    public MutableLiveData<String> currentRoundId = new MutableLiveData<String>("");
    public MutableLiveData<Boolean> currentRoundLoaded = new MutableLiveData<Boolean>(false);
    public MutableLiveData<Boolean> firebaseConnected = new MutableLiveData<Boolean>(true);
    public MutableLiveData<Boolean> activeReaderLoaded = new MutableLiveData<Boolean>(false);
    public MutableLiveData<String> activeReaderKey = new MutableLiveData<String>("");
    public MutableLiveData<List<String>> readOrder = new MutableLiveData<List<String>>(new ArrayList<String>());
    public MutableLiveData<String> expiredRoomMessage = new MutableLiveData<String>("");
    private ValueEventListener activeReaderListener;
    private String activeReaderListenerRoom;
    private ValueEventListener readingCompleteListener;
    private String readingCompleteListenerRoom;
    private ValueEventListener assignmentListener;
    private String assignmentListenerPath;
    private ValueEventListener replayStateListener;
    private String replayStateListenerRoom;
    private ValueEventListener currentRoundListener;
    private String currentRoundListenerRoom;
    private ValueEventListener connectionListener;
    private ValueEventListener expiredRoomListener;
    private String expiredRoomListenerRoom;

    public enum RoomJoinState {
        IDLE,
        AVAILABLE,
        DOES_NOT_EXIST,
        IN_PROGRESS,
        ERROR
    }

    public interface RoomCreationCallback {
        void onRoomCreated(String roomId);
    }

    public RoomViewModel() {
        this(new FirebaseGameRepository(), false);
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

    public void listenToConnectionState() {
        if (db == null || connectionListener != null) {
            return;
        }
        AppLog.i(AppLog.FIREBASE, "Attaching Firebase connection listener");
        connectionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean connected = snapshot.getValue(Boolean.class);
                firebaseConnected.setValue(connected == null || connected);
                AppLog.i(AppLog.FIREBASE, "Firebase connection state connected=" + firebaseConnected.getValue());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                AppLog.e(AppLog.FIREBASE, "Connection listener cancelled: " + error.getMessage());
            }
        };
        db.getDatabase().getReference(".info/connected").addValueEventListener(connectionListener);
    }

    public void removeConnectionListener() {
        if (db != null && connectionListener != null) {
            AppLog.i(AppLog.FIREBASE, "Removing Firebase connection listener");
            db.getDatabase().getReference(".info/connected").removeEventListener(connectionListener);
            connectionListener = null;
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
        deleteRoom(roomID, null);
    }

    public void deleteRoom(String roomID, Runnable onSuccess) {
        AppLog.i(AppLog.ROOM, "Deleting room=" + roomID);
        db.child("rooms").child(roomID).removeValue()
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.FIREBASE, "Room deleted id=" + roomID);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not delete room. Check your connection and try again.");
                    AppLog.e(AppLog.FIREBASE, "Failed deleting room=" + roomID, e);
                });
    }

    public void markRoomExpired(String roomID, String reason) {
        markRoomExpired(roomID, reason, null);
    }

    public void markRoomExpired(String roomID, String reason, Runnable onSuccess) {
        if (roomID == null || roomID.length() == 0) {
            return;
        }
        Map<String, Object> update = new HashMap<String, Object>();
        update.put("expired", true);
        update.put("reason", reason == null ? "Room expired." : reason);
        update.put("expiredAt", ServerValue.TIMESTAMP);
        AppLog.w(AppLog.ROOM, "Marking room expired room=" + roomID + ", reason=" + update.get("reason"));
        db.child("expiredRooms").child(roomID).updateChildren(update)
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.FIREBASE, "Room expiration marker written room=" + roomID);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not expire room cleanly. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed marking room expired room=" + roomID, e);
                });
    }

    public void listenToExpiredRoom(String roomID) {
        if (roomID == null || roomID.length() == 0) {
            return;
        }
        if (roomID.equals(expiredRoomListenerRoom) && expiredRoomListener != null) {
            return;
        }
        removeExpiredRoomListener();
        expiredRoomListenerRoom = roomID;
        expiredRoomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean expired = snapshot.child("expired").getValue(Boolean.class);
                if (!Boolean.TRUE.equals(expired)) {
                    return;
                }
                String reason = snapshot.child("reason").getValue(String.class);
                String message = reason == null || reason.length() == 0
                        ? "Game room ended. Create a new game!"
                        : reason;
                AppLog.w(AppLog.ROOM, "Expired room marker observed room=" + roomID + ", reason=" + message);
                expiredRoomMessage.setValue(message);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                AppLog.e(AppLog.FIREBASE, "Expired room listener cancelled room=" + roomID + ": " + error.getMessage());
            }
        };
        AppLog.i(AppLog.FIREBASE, "Attaching expired room listener room=" + roomID);
        db.child("expiredRooms").child(roomID).addValueEventListener(expiredRoomListener);
    }

    public void removeExpiredRoomListener() {
        if (expiredRoomListener != null && expiredRoomListenerRoom != null) {
            AppLog.i(AppLog.FIREBASE, "Removing expired room listener room=" + expiredRoomListenerRoom);
            db.child("expiredRooms").child(expiredRoomListenerRoom).removeEventListener(expiredRoomListener);
            expiredRoomListener = null;
            expiredRoomListenerRoom = null;
        }
    }

    public void deleteExpiredRoomMarker(String roomID) {
        if (roomID == null || roomID.length() == 0) {
            return;
        }
        AppLog.i(AppLog.ROOM, "Deleting expired room marker room=" + roomID);
        db.child("expiredRooms").child(roomID).removeValue()
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Expired room marker deleted room=" + roomID))
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed deleting expired room marker room=" + roomID, e));
    }

    public void cleanupOldExpiredRoomMarkers(long cutoffTimeMs) {
        AppLog.i(AppLog.ROOM, "Cleaning old expired room markers cutoff=" + cutoffTimeMs);
        db.child("expiredRooms").get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        AppLog.e(AppLog.FIREBASE, "Failed loading expired room markers for cleanup", task.getException());
                        return;
                    }
                    int deleted = 0;
                    for (DataSnapshot snapshot : task.getResult().getChildren()) {
                        Long expiredAt = snapshot.child("expiredAt").getValue(Long.class);
                        if (expiredAt != null && expiredAt < cutoffTimeMs) {
                            snapshot.getRef().removeValue()
                                    .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed deleting stale expired marker room=" + snapshot.getKey(), e));
                            deleted += 1;
                        }
                    }
                    AppLog.i(AppLog.ROOM, "Expired room marker cleanup queued deleted=" + deleted);
                });
    }

    public void pushRoom(String id) {
        room = new Room(id);
        AppLog.i(AppLog.ROOM, "Creating room=" + room.roomID);
        db.child("rooms").child(room.roomID).setValue(room)
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Room created id=" + room.roomID))
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not create room. Check your connection and try again.");
                    AppLog.e(AppLog.FIREBASE, "Failed creating room=" + room.roomID, e);
                });
    }

    public void createUniqueRoom(RoomCreationCallback callback) {
        reserveUniqueRoom(callback, 0);
    }

    private void reserveUniqueRoom(RoomCreationCallback callback, int attemptIndex) {
        String candidateRoomId = makeRoomID();
        AppLog.i(AppLog.ROOM, "Reserving room id attempt=" + (attemptIndex + 1) + ", room=" + candidateRoomId);
        db.child("rooms").child(candidateRoomId).runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.getValue() != null) {
                    return Transaction.abort();
                }
                currentData.setValue(new Room(candidateRoomId));
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error == null && committed) {
                    room = new Room(candidateRoomId);
                    AppLog.i(AppLog.FIREBASE, "Unique room reserved id=" + candidateRoomId + ", attempts=" + (attemptIndex + 1));
                    if (callback != null) {
                        callback.onRoomCreated(candidateRoomId);
                    }
                    return;
                }

                boolean hadError = error != null;
                if (RoomCreationPolicy.shouldRetry(attemptIndex, committed, hadError)) {
                    AppLog.w(AppLog.ROOM, "Room id unavailable; retrying attempt=" + (attemptIndex + 1)
                            + ", room=" + candidateRoomId
                            + ", error=" + (error == null ? "collision" : error.getMessage()));
                    reserveUniqueRoom(callback, attemptIndex + 1);
                    return;
                }

                databaseMessage.setValue("Could not create a unique game room. Please try again.");
                AppLog.e(AppLog.FIREBASE, "Failed reserving unique room after attempts=" + (attemptIndex + 1)
                        + ", lastRoom=" + candidateRoomId
                        + ", committed=" + committed
                        + ", error=" + (error == null ? "collision" : error.getMessage()));
            }
        });
    }
    public void gameInProgressTrue() {
        if (room != null) {
            repository.setRoomInProgress(room.roomID, true);
        }
    }

    public void gameInProgressTrue(String roomID) {
        gameInProgressTrue(roomID, null);
    }

    public void gameInProgressTrue(String roomID, Runnable onSuccess) {
        if (roomID != null && roomID.length() > 0) {
            Task<Void> task = repository.setRoomInProgress(roomID, true);
            if (task != null) {
                task.addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Room locked for active game room=" + roomID))
                        .addOnSuccessListener(unused -> {
                            if (onSuccess != null) {
                                onSuccess.run();
                            }
                        })
                        .addOnFailureListener(e -> {
                            databaseMessage.setValue("Could not lock the game room. Check your connection before starting.");
                            AppLog.e(AppLog.FIREBASE, "Failed locking room=" + roomID, e);
                        });
            }
            else if (onSuccess != null) {
                onSuccess.run();
            }
        }
    }

    public void gameInProgressFalse(String room) {
        gameInProgressFalse(room, null);
    }

    public void gameInProgressFalse(String room, Runnable onSuccess) {
        if (room != null && room.length() > 0) {
            Task<Void> task = repository.setRoomInProgress(room, false);
            if (task != null) {
                task.addOnSuccessListener(unused -> {
                            AppLog.i(AppLog.FIREBASE, "Room unlocked room=" + room);
                            if (onSuccess != null) {
                                onSuccess.run();
                            }
                        })
                        .addOnFailureListener(e -> {
                            databaseMessage.setValue("Could not update room status. Check your connection.");
                            AppLog.e(AppLog.FIREBASE, "Failed unlocking room=" + room, e);
                        });
            }
            else if (onSuccess != null) {
                onSuccess.run();
            }
        }
    }

    public void checkRoomCanJoin(String roomId) {
        if (roomId == null || roomId.length() == 0) {
            roomJoinState.setValue(RoomJoinState.DOES_NOT_EXIST);
            return;
        }
        AppLog.i(AppLog.ROOM, "Checking room before join room=" + roomId);
        db.child("rooms").child(roomId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    AppLog.w(AppLog.ROOM, "Room lookup failed: room does not exist room=" + roomId);
                    roomJoinState.setValue(RoomJoinState.DOES_NOT_EXIST);
                    return;
                }
                Boolean value = snapshot.child("gameInProgress").getValue(Boolean.class);
                if (value != null && value) {
                    AppLog.w(AppLog.ROOM, "Room lookup blocked: game in progress room=" + roomId);
                    roomJoinState.setValue(RoomJoinState.IN_PROGRESS);
                    return;
                }
                AppLog.i(AppLog.ROOM, "Room available to join room=" + roomId);
                roomJoinState.setValue(RoomJoinState.AVAILABLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                databaseMessage.setValue("Could not check room. Check your connection and try again.");
                roomJoinState.setValue(RoomJoinState.ERROR);
                AppLog.e(AppLog.FIREBASE, "Room lookup cancelled room=" + roomId + ": " + databaseError.getMessage());
            }
        });
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

    public void createRoundAssignments(String roomId, ObservableArrayList<User> users) {
        createRoundAssignments(roomId, users, null);
    }

    public void createRoundAssignments(String roomId, ObservableArrayList<User> users, Runnable onSuccess) {
        if (roomId == null || roomId.length() == 0 || users == null || users.size() == 0) {
            AppLog.w(AppLog.GAME_FLOW, "Assignment map skipped: missing room or players");
            return;
        }

        Map<String, User> usersByKey = buildUsersByKey(users);
        List<String> playerKeys = new ArrayList<String>(usersByKey.keySet());
        Collections.sort(playerKeys);
        if (playerKeys.size() < 2) {
            AppLog.w(AppLog.GAME_FLOW, "Assignment map skipped: fewer than two players room=" + roomId);
            return;
        }

        long seed = System.currentTimeMillis();
        String roundId = GameLogic.newRoundId();
        List<String> ifOwners = GameLogic.randomizedAssignment(playerKeys, seed);
        List<String> thenOwners = GameLogic.randomizedAssignment(playerKeys, seed + 9973L);
        List<String> readerOrder = buildHostFirstReadOrder(usersByKey, playerKeys, seed + 19997L);
        Map<String, RoundAssignment> assignments = new LinkedHashMap<String, RoundAssignment>();
        for (int index = 0; index < playerKeys.size(); index++) {
            String playerKey = playerKeys.get(index);
            String ifOwnerKey = ifOwners.get(index);
            String thenOwnerKey = thenOwners.get(index);
            User ifOwner = usersByKey.get(ifOwnerKey);
            User thenOwner = usersByKey.get(thenOwnerKey);
            assignments.put(playerKey, new RoundAssignment(
                    playerKey,
                    ifOwnerKey,
                    thenOwnerKey,
                    displayName(ifOwner),
                    displayName(thenOwner),
                    contributorId(ifOwner),
                    contributorId(thenOwner),
                    seed,
                    roundId,
                    readerOrder.indexOf(playerKey)));
        }

        AppLog.i(AppLog.GAME_FLOW, "Creating round assignment map room=" + roomId + ", roundId=" + roundId + ", players=" + assignments.size());
        Map<String, Object> roundUpdate = new HashMap<String, Object>();
        roundUpdate.put("currentRoundId", roundId);
        roundUpdate.put("roundAssignments", assignments);
        roundUpdate.put("readOrder", readerOrder);
        roundUpdate.put("activeReaderIndex", 0);
        roundUpdate.put("activeReaderKey", readerOrder.get(0));
        roundUpdate.put("activeReaderRoundId", roundId);
        roundUpdate.put("readingComplete", false);
        roundUpdate.put("readingCompleteRoundId", roundId);
        db.child("rooms").child(roomId).updateChildren(roundUpdate)
                .addOnSuccessListener(unused -> {
                    currentRoundId.setValue(roundId);
                    currentRoundLoaded.setValue(true);
                    readOrder.setValue(readerOrder);
                    activeReaderIndex.setValue(0);
                    activeReaderKey.setValue(readerOrder.get(0));
                    activeReaderLoaded.setValue(true);
                    AppLog.i(AppLog.FIREBASE, "Round assignments written room=" + roomId + ", roundId=" + roundId);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not prepare round pairings. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed writing round assignments room=" + roomId, e);
                });
    }

    private Map<String, User> buildUsersByKey(ObservableArrayList<User> users) {
        Map<String, User> usersByKey = new HashMap<String, User>();
        for (User user : users) {
            String key = GameLogic.playerKey(user);
            if (key.length() > 1) {
                usersByKey.put(key, user);
            }
        }
        return usersByKey;
    }

    private List<String> buildHostFirstReadOrder(Map<String, User> usersByKey, List<String> playerKeys, long seed) {
        List<String> hostKeys = new ArrayList<String>();
        List<String> guestKeys = new ArrayList<String>();
        for (String playerKey : playerKeys) {
            User user = usersByKey.get(playerKey);
            if (user != null && user.host) {
                hostKeys.add(playerKey);
            }
            else {
                guestKeys.add(playerKey);
            }
        }
        Collections.shuffle(guestKeys, new Random(seed));
        List<String> order = new ArrayList<String>();
        if (hostKeys.size() > 0) {
            Collections.sort(hostKeys);
            order.add(hostKeys.get(0));
        }
        for (String playerKey : guestKeys) {
            if (!order.contains(playerKey)) {
                order.add(playerKey);
            }
        }
        for (String playerKey : playerKeys) {
            if (!order.contains(playerKey)) {
                order.add(playerKey);
            }
        }
        AppLog.i(AppLog.GAME_FLOW, "Read order built hostFirst=" + (hostKeys.size() > 0) + ", players=" + order.size());
        return order;
    }

    private String displayName(User user) {
        return user == null || user.userName == null ? "" : user.userName;
    }

    private String contributorId(User user) {
        return user == null || user.uid == null ? "" : user.uid;
    }

    public void listenToAssignment(String roomId, String playerKey) {
        if (roomId == null || roomId.length() == 0 || playerKey == null || playerKey.length() == 0) {
            AppLog.w(AppLog.GAME_FLOW, "Assignment listener skipped: missing room or player key");
            return;
        }
        String path = "rooms/" + roomId + "/roundAssignments/" + playerKey;
        if (path.equals(assignmentListenerPath) && assignmentListener != null) {
            return;
        }

        removeAssignmentListener();
        currentAssignment.setValue(null);
        assignmentListenerPath = path;
        assignmentListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                RoundAssignment assignment = snapshot.getValue(RoundAssignment.class);
                if (assignment == null) {
                    AppLog.d(AppLog.GAME_FLOW, "Waiting for assignment map path=" + path);
                    return;
                }
                if (!GameLogic.isCurrentRound(currentRoundId.getValue(), assignment.roundId)) {
                    AppLog.w(AppLog.GAME_FLOW, "Ignoring stale assignment playerKey=" + playerKey
                            + ", eventRoundId=" + assignment.roundId
                            + ", currentRoundId=" + currentRoundId.getValue());
                    return;
                }
                currentAssignment.setValue(assignment);
                AppLog.i(AppLog.GAME_FLOW, "Assignment loaded playerKey=" + playerKey
                        + ", ifOwner=" + assignment.ifOwnerKey
                        + ", thenOwner=" + assignment.thenOwnerKey);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                databaseMessage.setValue("Could not load your round pairing. Check your connection.");
                AppLog.e(AppLog.FIREBASE, "Assignment listener cancelled path=" + path + ": " + error.getMessage());
            }
        };
        AppLog.i(AppLog.FIREBASE, "Attaching assignment listener path=" + path);
        db.child(path).addValueEventListener(assignmentListener);
    }

    public void removeAssignmentListener() {
        if (assignmentListener != null && assignmentListenerPath != null) {
            AppLog.i(AppLog.FIREBASE, "Removing assignment listener path=" + assignmentListenerPath);
            db.child(assignmentListenerPath).removeEventListener(assignmentListener);
            assignmentListener = null;
            assignmentListenerPath = null;
        }
    }

    public void deleteRoundAssignments(String roomId) {
        deleteRoundAssignments(roomId, null);
    }

    public void deleteRoundAssignments(String roomId, Runnable onSuccess) {
        if (roomId == null || roomId.length() == 0) {
            return;
        }
        AppLog.i(AppLog.GAME_FLOW, "Deleting round assignments room=" + roomId);
        db.child("rooms").child(roomId).child("roundAssignments").removeValue()
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.FIREBASE, "Round assignments deleted room=" + roomId);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not clear round pairings. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed deleting round assignments room=" + roomId, e);
                });
    }

    public void clearRoomRoundStateForReplay(String roomId, Runnable onSuccess) {
        if (roomId == null || roomId.length() == 0) {
            AppLog.w(AppLog.ROOM, "clearRoomRoundStateForReplay skipped: missing room id");
            return;
        }
        AppLog.i(AppLog.GAME_FLOW, "Clearing room round state for replay room=" + roomId);
        Map<String, Object> update = new HashMap<String, Object>();
        update.put("roundAssignments", null);
        update.put("currentRoundId", "");
        update.put("readOrder", null);
        update.put("activeReaderIndex", 0);
        update.put("activeReaderKey", "");
        update.put("activeReaderRoundId", "");
        update.put("readingComplete", false);
        update.put("readingCompleteRoundId", "");
        db.child("rooms").child(roomId).updateChildren(update)
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.FIREBASE, "Room round state cleared for replay room=" + roomId);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not clear old round state. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed clearing room round state room=" + roomId, e);
                });
    }

    public void clearLocalRoundState() {
        activeReaderIndex.setValue(0);
        activeReaderLoaded.setValue(false);
        activeReaderKey.setValue("");
        readOrder.setValue(new ArrayList<String>());
        readingComplete.setValue(false);
        replayState.setValue("");
        currentRoundId.setValue("");
        currentRoundLoaded.setValue(false);
        currentAssignment.setValue(null);
        removeAssignmentListener();
        removeReadingCompleteListener();
        removeActiveReaderListener();
        removeCurrentRoundListener();
        removeReplayStateListener();
        removeExpiredRoomListener();
        AppLog.i(AppLog.GAME_FLOW, "Cleared local room round state");
    }

    public void setActiveReaderIndex(String roomId, int index) {
        setActiveReaderIndex(roomId, index, null);
    }

    public void setActiveReaderIndex(String roomId, int index, Runnable onSuccess) {
        if (roomId == null || roomId.length() == 0) {
            AppLog.w(AppLog.ROOM, "setActiveReaderIndex skipped: missing room id");
            return;
        }
        AppLog.i(AppLog.ROOM, "Setting active reader room=" + roomId + ", index=" + index);
        Map<String, Object> update = new HashMap<String, Object>();
        update.put("activeReaderIndex", index);
        update.put("activeReaderKey", activeReaderKeyForIndex(index));
        update.put("activeReaderRoundId", currentRoundId.getValue() == null ? "" : currentRoundId.getValue());
        db.child("rooms").child(roomId).updateChildren(update)
                .addOnSuccessListener(unused -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not pass reading turn. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed setting active reader room=" + roomId, e);
                });
    }

    public void setReadingComplete(String roomId, boolean complete) {
        setReadingComplete(roomId, complete, null);
    }

    public void setReadingComplete(String roomId, boolean complete, Runnable onSuccess) {
        if (roomId == null || roomId.length() == 0) {
            AppLog.w(AppLog.ROOM, "setReadingComplete skipped: missing room id");
            return;
        }
        AppLog.i(AppLog.ROOM, "Setting reading complete room=" + roomId + ", complete=" + complete);
        Map<String, Object> update = new HashMap<String, Object>();
        update.put("readingComplete", complete);
        update.put("readingCompleteRoundId", currentRoundId.getValue() == null ? "" : currentRoundId.getValue());
        db.child("rooms").child(roomId).updateChildren(update)
                .addOnSuccessListener(unused -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not finish the reading round. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed setting reading complete room=" + roomId, e);
                });
    }

    public void completeReadingAfterFinalPass(String roomId, int completedIndex, Runnable onSuccess) {
        if (roomId == null || roomId.length() == 0) {
            AppLog.w(AppLog.ROOM, "completeReadingAfterFinalPass skipped: missing room id");
            return;
        }
        AppLog.i(AppLog.ROOM, "Completing reading after final pass room=" + roomId + ", completedIndex=" + completedIndex);
        Map<String, Object> update = new HashMap<String, Object>();
        update.put("activeReaderIndex", completedIndex);
        update.put("activeReaderKey", "");
        update.put("activeReaderRoundId", currentRoundId.getValue() == null ? "" : currentRoundId.getValue());
        update.put("readingComplete", true);
        update.put("readingCompleteRoundId", currentRoundId.getValue() == null ? "" : currentRoundId.getValue());
        db.child("rooms").child(roomId).updateChildren(update)
                .addOnSuccessListener(unused -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not finish the reading round. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed completing reading after final pass room=" + roomId, e);
                });
    }

    public void setReplayState(String roomId, String state) {
        setReplayState(roomId, state, null);
    }

    public void setReplayState(String roomId, String state, Runnable onSuccess) {
        if (roomId == null || roomId.length() == 0) {
            AppLog.w(AppLog.ROOM, "setReplayState skipped: missing room id");
            return;
        }
        String safeState = state == null ? "" : state;
        AppLog.i(AppLog.ROOM, "Setting replay state room=" + roomId + ", state=" + safeState);
        db.child("rooms").child(roomId).child("replayState").setValue(safeState)
                .addOnSuccessListener(unused -> {
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not update play-again state. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed setting replay state room=" + roomId, e);
                });
    }

    public void listenToActiveReader(String roomId) {
        if (roomId == null || roomId.length() == 0) {
            AppLog.w(AppLog.ROOM, "listenToActiveReader skipped: missing room id");
            return;
        }
        if (roomId.equals(activeReaderListenerRoom) && activeReaderListener != null) {
            return;
        }
        removeActiveReaderListener();
        activeReaderLoaded.setValue(false);
        activeReaderListenerRoom = roomId;
        activeReaderListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String eventRoundId = snapshot.child("activeReaderRoundId").getValue(String.class);
                if (!GameLogic.isCurrentRound(currentRoundId.getValue(), eventRoundId)) {
                    AppLog.w(AppLog.ROOM, "Ignoring stale active reader room=" + roomId
                            + ", eventRoundId=" + eventRoundId
                            + ", currentRoundId=" + currentRoundId.getValue());
                    return;
                }
                Integer index = snapshot.child("activeReaderIndex").getValue(Integer.class);
                String readerKey = snapshot.child("activeReaderKey").getValue(String.class);
                List<String> order = readStringList(snapshot.child("readOrder"));
                activeReaderIndex.setValue(index == null ? 0 : index);
                activeReaderKey.setValue(readerKey == null ? "" : readerKey);
                readOrder.setValue(order);
                activeReaderLoaded.setValue(true);
                AppLog.d(AppLog.ROOM, "Active reader updated room=" + roomId + ", index=" + activeReaderIndex.getValue() + ", key=" + activeReaderKey.getValue());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                databaseMessage.setValue("Could not listen for reading turns. Check your connection.");
                AppLog.e(AppLog.FIREBASE, "Active reader listener cancelled room=" + roomId + ": " + error.getMessage());
            }
        };
        db.child("rooms").child(roomId).addValueEventListener(activeReaderListener);
    }

    private List<String> readStringList(DataSnapshot snapshot) {
        List<String> values = new ArrayList<String>();
        if (snapshot == null || !snapshot.exists()) {
            return values;
        }
        for (DataSnapshot child : snapshot.getChildren()) {
            String value = child.getValue(String.class);
            if (value != null && value.length() > 0) {
                values.add(value);
            }
        }
        return values;
    }

    private String activeReaderKeyForIndex(int index) {
        List<String> order = readOrder.getValue();
        if (order == null || index < 0 || index >= order.size()) {
            return "";
        }
        return order.get(index);
    }

    public void listenToReadingComplete(String roomId) {
        if (roomId == null || roomId.length() == 0) {
            AppLog.w(AppLog.ROOM, "listenToReadingComplete skipped: missing room id");
            return;
        }
        if (roomId.equals(readingCompleteListenerRoom) && readingCompleteListener != null) {
            return;
        }
        removeReadingCompleteListener();
        readingComplete.setValue(false);
        readingCompleteListenerRoom = roomId;
        readingCompleteListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String eventRoundId = snapshot.child("readingCompleteRoundId").getValue(String.class);
                if (!GameLogic.isCurrentRound(currentRoundId.getValue(), eventRoundId)) {
                    AppLog.w(AppLog.ROOM, "Ignoring stale reading complete room=" + roomId
                            + ", eventRoundId=" + eventRoundId
                            + ", currentRoundId=" + currentRoundId.getValue());
                    return;
                }
                Boolean complete = snapshot.child("readingComplete").getValue(Boolean.class);
                readingComplete.setValue(complete != null && complete);
                AppLog.d(AppLog.ROOM, "Reading complete updated room=" + roomId + ", complete=" + readingComplete.getValue());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                databaseMessage.setValue("Could not listen for reading completion. Check your connection.");
                AppLog.e(AppLog.FIREBASE, "Reading completion listener cancelled room=" + roomId + ": " + error.getMessage());
            }
        };
        db.child("rooms").child(roomId).addValueEventListener(readingCompleteListener);
    }

    public void listenToCurrentRoundId(String roomId) {
        if (roomId == null || roomId.length() == 0) {
            AppLog.w(AppLog.ROOM, "listenToCurrentRoundId skipped: missing room id");
            return;
        }
        if (roomId.equals(currentRoundListenerRoom) && currentRoundListener != null) {
            return;
        }
        removeCurrentRoundListener();
        currentRoundLoaded.setValue(false);
        currentRoundId.setValue("");
        currentRoundListenerRoom = roomId;
        currentRoundListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String roundId = snapshot.getValue(String.class);
                currentRoundLoaded.setValue(true);
                currentRoundId.setValue(roundId == null ? "" : roundId);
                AppLog.i(AppLog.GAME_FLOW, "Current round updated room=" + roomId + ", roundId=" + currentRoundId.getValue());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                databaseMessage.setValue("Could not load the current round. Check your connection.");
                AppLog.e(AppLog.FIREBASE, "Current round listener cancelled room=" + roomId + ": " + error.getMessage());
            }
        };
        db.child("rooms").child(roomId).child("currentRoundId").addValueEventListener(currentRoundListener);
    }

    public void listenToReplayState(String roomId) {
        if (roomId == null || roomId.length() == 0) {
            AppLog.w(AppLog.ROOM, "listenToReplayState skipped: missing room id");
            return;
        }
        if (roomId.equals(replayStateListenerRoom) && replayStateListener != null) {
            return;
        }
        removeReplayStateListener();
        replayState.setValue("");
        replayStateListenerRoom = roomId;
        replayStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String value = snapshot.getValue(String.class);
                replayState.setValue(value == null ? "" : value);
                AppLog.d(AppLog.ROOM, "Replay state updated room=" + roomId + ", state=" + replayState.getValue());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                databaseMessage.setValue("Could not listen for play-again state. Check your connection.");
                AppLog.e(AppLog.FIREBASE, "Replay state listener cancelled room=" + roomId + ": " + error.getMessage());
            }
        };
        db.child("rooms").child(roomId).child("replayState").addValueEventListener(replayStateListener);
    }

    public void removeActiveReaderListener() {
        if (activeReaderListener != null && activeReaderListenerRoom != null) {
            AppLog.i(AppLog.FIREBASE, "Removing active reader listener room=" + activeReaderListenerRoom);
            db.child("rooms").child(activeReaderListenerRoom).removeEventListener(activeReaderListener);
            activeReaderListener = null;
            activeReaderListenerRoom = null;
        }
    }

    public void removeReadingCompleteListener() {
        if (readingCompleteListener != null && readingCompleteListenerRoom != null) {
            AppLog.i(AppLog.FIREBASE, "Removing reading complete listener room=" + readingCompleteListenerRoom);
            db.child("rooms").child(readingCompleteListenerRoom).removeEventListener(readingCompleteListener);
            readingCompleteListener = null;
            readingCompleteListenerRoom = null;
        }
    }

    public void removeReplayStateListener() {
        if (replayStateListener != null && replayStateListenerRoom != null) {
            AppLog.i(AppLog.FIREBASE, "Removing replay state listener room=" + replayStateListenerRoom);
            db.child("rooms").child(replayStateListenerRoom).child("replayState").removeEventListener(replayStateListener);
            replayStateListener = null;
            replayStateListenerRoom = null;
        }
    }

    public void removeCurrentRoundListener() {
        if (currentRoundListener != null && currentRoundListenerRoom != null) {
            AppLog.i(AppLog.FIREBASE, "Removing current round listener room=" + currentRoundListenerRoom);
            db.child("rooms").child(currentRoundListenerRoom).child("currentRoundId").removeEventListener(currentRoundListener);
            currentRoundListener = null;
            currentRoundListenerRoom = null;
        }
    }

    @Override
    protected void onCleared() {
        removeAssignmentListener();
        removeReadingCompleteListener();
        removeActiveReaderListener();
        removeReplayStateListener();
        removeCurrentRoundListener();
        removeExpiredRoomListener();
        removeConnectionListener();
        super.onCleared();
    }

    //    public void updateNumInRoom(User user) {
//
//    }
}
