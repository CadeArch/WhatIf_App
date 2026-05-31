package com.CadeMixedUpGame.api.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameLogic;
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
    String allChars = "a b c d e f g h i j k m n o p q r s t u v w x y z A B C D E F G H J K L M N O P Q R S T U V W X Y Z 0 1 2 3 4 5 6 7 8 9";
    String[] usableCharacter;
    public MutableLiveData<Boolean> inProgress = new MutableLiveData<Boolean>();
    public MutableLiveData<RoomJoinState> roomJoinState = new MutableLiveData<RoomJoinState>(RoomJoinState.IDLE);
    public MutableLiveData<String> databaseMessage = new MutableLiveData<String>();
    public MutableLiveData<Integer> activeReaderIndex = new MutableLiveData<Integer>(0);
    public MutableLiveData<Boolean> readingComplete = new MutableLiveData<Boolean>(false);
    public MutableLiveData<String> replayState = new MutableLiveData<String>("");
    public MutableLiveData<RoundAssignment> currentAssignment = new MutableLiveData<RoundAssignment>();
    private ValueEventListener activeReaderListener;
    private String activeReaderListenerRoom;
    private ValueEventListener readingCompleteListener;
    private String readingCompleteListenerRoom;
    private ValueEventListener assignmentListener;
    private String assignmentListenerPath;
    private ValueEventListener replayStateListener;
    private String replayStateListenerRoom;

    public enum RoomJoinState {
        IDLE,
        AVAILABLE,
        DOES_NOT_EXIST,
        IN_PROGRESS,
        ERROR
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
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Room deleted id=" + roomID))
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not delete room. Check your connection and try again.");
                    AppLog.e(AppLog.FIREBASE, "Failed deleting room=" + roomID, e);
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
        if (room != null && room.length() > 0) {
            Task<Void> task = repository.setRoomInProgress(room, false);
            if (task != null) {
                task.addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Room unlocked room=" + room))
                        .addOnFailureListener(e -> {
                            databaseMessage.setValue("Could not update room status. Check your connection.");
                            AppLog.e(AppLog.FIREBASE, "Failed unlocking room=" + room, e);
                        });
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
        List<String> ifOwners = GameLogic.randomizedAssignment(playerKeys, seed);
        List<String> thenOwners = GameLogic.randomizedAssignment(playerKeys, seed + 9973L);
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
                    seed));
        }

        AppLog.i(AppLog.GAME_FLOW, "Creating round assignment map room=" + roomId + ", players=" + assignments.size());
        db.child("rooms").child(roomId).child("roundAssignments").setValue(assignments)
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.FIREBASE, "Round assignments written room=" + roomId);
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
        deleteRoundAssignments(roomId, () ->
                setActiveReaderIndex(roomId, 0, () ->
                        setReadingComplete(roomId, false, onSuccess)));
    }

    public void clearLocalRoundState() {
        activeReaderIndex.setValue(0);
        readingComplete.setValue(false);
        replayState.setValue("");
        currentAssignment.setValue(null);
        removeAssignmentListener();
        removeReadingCompleteListener();
        removeActiveReaderListener();
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
        db.child("rooms").child(roomId).child("activeReaderIndex").setValue(index)
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
        db.child("rooms").child(roomId).child("readingComplete").setValue(complete)
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
        activeReaderIndex.setValue(0);
        activeReaderListenerRoom = roomId;
        activeReaderListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer index = snapshot.getValue(Integer.class);
                activeReaderIndex.setValue(index == null ? 0 : index);
                AppLog.d(AppLog.ROOM, "Active reader updated room=" + roomId + ", index=" + activeReaderIndex.getValue());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                databaseMessage.setValue("Could not listen for reading turns. Check your connection.");
                AppLog.e(AppLog.FIREBASE, "Active reader listener cancelled room=" + roomId + ": " + error.getMessage());
            }
        };
        db.child("rooms").child(roomId).child("activeReaderIndex").addValueEventListener(activeReaderListener);
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
                Boolean complete = snapshot.getValue(Boolean.class);
                readingComplete.setValue(complete != null && complete);
                AppLog.d(AppLog.ROOM, "Reading complete updated room=" + roomId + ", complete=" + readingComplete.getValue());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                databaseMessage.setValue("Could not listen for reading completion. Check your connection.");
                AppLog.e(AppLog.FIREBASE, "Reading completion listener cancelled room=" + roomId + ": " + error.getMessage());
            }
        };
        db.child("rooms").child(roomId).child("readingComplete").addValueEventListener(readingCompleteListener);
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
            db.child("rooms").child(activeReaderListenerRoom).child("activeReaderIndex").removeEventListener(activeReaderListener);
            activeReaderListener = null;
            activeReaderListenerRoom = null;
        }
    }

    public void removeReadingCompleteListener() {
        if (readingCompleteListener != null && readingCompleteListenerRoom != null) {
            AppLog.i(AppLog.FIREBASE, "Removing reading complete listener room=" + readingCompleteListenerRoom);
            db.child("rooms").child(readingCompleteListenerRoom).child("readingComplete").removeEventListener(readingCompleteListener);
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

    @Override
    protected void onCleared() {
        removeAssignmentListener();
        removeReadingCompleteListener();
        removeActiveReaderListener();
        removeReplayStateListener();
        super.onCleared();
    }

    //    public void updateNumInRoom(User user) {
//
//    }
}
