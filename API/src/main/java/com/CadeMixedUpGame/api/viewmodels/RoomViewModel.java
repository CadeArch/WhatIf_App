package com.CadeMixedUpGame.api.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.ChildEventListenerAdapter;
import com.CadeMixedUpGame.api.GameFlowPolicy;
import com.CadeMixedUpGame.api.GameLogic;
import com.CadeMixedUpGame.api.RoomCreationPolicy;
import com.CadeMixedUpGame.api.models.RoundAssignment;
import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.repositories.FirebaseGameRepository;
import com.CadeMixedUpGame.api.repositories.GameRepository;
import com.CadeMixedUpGame.api.repositories.RoomMaintenance;
import com.CadeMixedUpGame.api.repositories.RoundStateRepository;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class RoomViewModel extends ViewModel {
    ObservableArrayList<Room> rooms;
    Room room;
    public ArrayList<String> roomNames = new ArrayList<>();
    public DatabaseReference db;
    private final GameRepository repository;
    private final RoomMaintenance maintenance;
    private final RoundStateRepository round;
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
        maintenance = new RoomMaintenance(db);
        round = new RoundStateRepository(db, databaseMessage, currentAssignment, currentRoundId,
                currentRoundLoaded, activeReaderIndex, activeReaderLoaded, activeReaderKey,
                readOrder, readingComplete, replayState);
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
        db.child("rooms").addChildEventListener(new ChildEventListenerAdapter(AppLog.FIREBASE, "Rooms listener cancelled") {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                rooms.add(new Room(snapshot.getKey()));
                roomNames.add(snapshot.getKey());
                AppLog.d(AppLog.ROOM, "Room loaded id=" + snapshot.getKey() + ", total=" + roomNames.size());
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                Room room = new Room(snapshot.getKey());
                rooms.remove(room);
            }
        });
        return roomNames;
    }

    public String makeRoomID() {
        return GameLogic.randomRoomCode(new Random());
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

    // Housekeeping lives in RoomMaintenance; these stay so the Activity keeps one entry point.
    public void runDailyMaintenanceIfDue(long nowMs) {
        maintenance.runDailyMaintenanceIfDue(nowMs);
    }

    public void cleanupAbandonedRooms(long nowMs) {
        maintenance.cleanupAbandonedRooms(nowMs);
    }

    public void cleanupOldExpiredRoomMarkers(long cutoffTimeMs) {
        maintenance.cleanupOldExpiredRoomMarkers(cutoffTimeMs);
    }

    public void pushRoom(String id) {
        pushRoom(id, null);
    }

    /** Written as a separate server-timestamped field rather than a Room POJO member so the value
     * comes from the server clock - cleanupAbandonedRooms compares it against other clients' clocks,
     * and a device with a skewed clock could otherwise create a room that instantly looks stale. */
    private void stampCreatedAt(String roomID) {
        db.child("rooms").child(roomID).child("createdAt").setValue(ServerValue.TIMESTAMP)
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed stamping createdAt room=" + roomID, e));
    }

    public void pushRoom(String id, Runnable onSuccess) {
        room = new Room(id);
        AppLog.i(AppLog.ROOM, "Creating room=" + room.roomID);
        db.child("rooms").child(room.roomID).setValue(room)
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.FIREBASE, "Room created id=" + room.roomID);
                    stampCreatedAt(room.roomID);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
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
                    stampCreatedAt(candidateRoomId);
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

    // The round itself - assignments, reading turn, completion, replay signal - lives in
    // RoundStateRepository. These forward so every existing call site and the reading/replay
    // characterization tests are untouched.
    public void createRoundAssignments(String roomId, ObservableArrayList<User> users) {
        round.createRoundAssignments(roomId, users);
    }

    public void createRoundAssignments(String roomId, ObservableArrayList<User> users, Runnable onSuccess) {
        round.createRoundAssignments(roomId, users, onSuccess);
    }

    public void listenToAssignment(String roomId, String playerKey) {
        round.listenToAssignment(roomId, playerKey);
    }

    public void removeAssignmentListener() {
        round.removeAssignmentListener();
    }

    public void deleteRoundAssignments(String roomId) {
        round.deleteRoundAssignments(roomId);
    }

    public void deleteRoundAssignments(String roomId, Runnable onSuccess) {
        round.deleteRoundAssignments(roomId, onSuccess);
    }

    public void clearRoomRoundStateForReplay(String roomId, Runnable onSuccess) {
        round.clearRoomRoundStateForReplay(roomId, onSuccess);
    }

    public void clearLocalRoundState() {
        round.clearLocalRoundState();
        // The expired-room listener is room lifecycle rather than round state, so it stayed here -
        // but clearing local round state has always torn it down too, and that is preserved.
        removeExpiredRoomListener();
    }

    public void setActiveReaderIndex(String roomId, int index) {
        round.setActiveReaderIndex(roomId, index);
    }

    public void setActiveReaderIndex(String roomId, int index, Runnable onSuccess) {
        round.setActiveReaderIndex(roomId, index, onSuccess);
    }

    public void setReadingComplete(String roomId, boolean complete) {
        round.setReadingComplete(roomId, complete);
    }

    public void setReadingComplete(String roomId, boolean complete, Runnable onSuccess) {
        round.setReadingComplete(roomId, complete, onSuccess);
    }

    public void completeReadingAfterFinalPass(String roomId, int completedIndex, Runnable onSuccess) {
        round.completeReadingAfterFinalPass(roomId, completedIndex, onSuccess);
    }

    public void setReplayState(String roomId, String state) {
        round.setReplayState(roomId, state);
    }

    public void setReplayState(String roomId, String state, Runnable onSuccess) {
        round.setReplayState(roomId, state, onSuccess);
    }

    public void listenToActiveReader(String roomId) {
        round.listenToActiveReader(roomId);
    }

    public void listenToReadingComplete(String roomId) {
        round.listenToReadingComplete(roomId);
    }

    public void listenToCurrentRoundId(String roomId) {
        round.listenToCurrentRoundId(roomId);
    }

    public void listenToReplayState(String roomId) {
        round.listenToReplayState(roomId);
    }

    public void removeActiveReaderListener() {
        round.removeActiveReaderListener();
    }

    public void removeReadingCompleteListener() {
        round.removeReadingCompleteListener();
    }

    public void removeReplayStateListener() {
        round.removeReplayStateListener();
    }

    public void removeCurrentRoundListener() {
        round.removeCurrentRoundListener();
    }

}
