package com.CadeMixedUpGame.api.repositories;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.MutableLiveData;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameFlowPolicy;
import com.CadeMixedUpGame.api.GameLogic;
import com.CadeMixedUpGame.api.models.RoundAssignment;
import com.CadeMixedUpGame.api.models.User;
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

/**
 * Everything about the round currently being played: the hidden If/Then assignment map, whose turn
 * it is to read, whether reading has finished, and the replay signal that starts the next round.
 *
 * <p>The last and most entangled cluster split out of {@code RoomViewModel}, which was left holding
 * room lifecycle (create/join/delete/expire) and little else. The two concerns only ever met
 * through the database: nothing here touches room creation, and nothing there touches a round.
 *
 * <p>Every listener is round-scoped and ignores updates from a previous round — that guard is the
 * reason replay works at all, and it is covered by {@code ReadingTurnEmulatorTest},
 * {@code RoundAssignmentEmulatorTest} and {@code ReplayLoopEmulatorTest}, which already existed and
 * pass unchanged against this class. Those tests were what made this move safe to attempt: they
 * pin the false-disconnect-during-replay race that took real iteration to get right.
 *
 * <p>The {@code MutableLiveData} handed in are the very same instances {@code RoomViewModel} still
 * exposes as public fields, so every {@code roomViewModel.readOrder} / {@code .activeReaderIndex}
 * read across the fragments keeps working untouched.
 */
public class RoundStateRepository {
    private final DatabaseReference db;
    private final MutableLiveData<String> databaseMessage;
    private final MutableLiveData<RoundAssignment> currentAssignment;
    private final MutableLiveData<String> currentRoundId;
    private final MutableLiveData<Boolean> currentRoundLoaded;
    private final MutableLiveData<Integer> activeReaderIndex;
    private final MutableLiveData<Boolean> activeReaderLoaded;
    private final MutableLiveData<String> activeReaderKey;
    private final MutableLiveData<List<String>> readOrder;
    private final MutableLiveData<Boolean> readingComplete;
    private final MutableLiveData<String> replayState;

    private ValueEventListener assignmentListener;
    private String assignmentListenerPath;
    private ValueEventListener activeReaderListener;
    private String activeReaderListenerRoom;
    private ValueEventListener readingCompleteListener;
    private String readingCompleteListenerRoom;
    private ValueEventListener replayStateListener;
    private String replayStateListenerRoom;
    private ValueEventListener currentRoundListener;
    private String currentRoundListenerRoom;

    public RoundStateRepository(DatabaseReference db,
                                MutableLiveData<String> databaseMessage,
                                MutableLiveData<RoundAssignment> currentAssignment,
                                MutableLiveData<String> currentRoundId,
                                MutableLiveData<Boolean> currentRoundLoaded,
                                MutableLiveData<Integer> activeReaderIndex,
                                MutableLiveData<Boolean> activeReaderLoaded,
                                MutableLiveData<String> activeReaderKey,
                                MutableLiveData<List<String>> readOrder,
                                MutableLiveData<Boolean> readingComplete,
                                MutableLiveData<String> replayState) {
        this.db = db;
        this.databaseMessage = databaseMessage;
        this.currentAssignment = currentAssignment;
        this.currentRoundId = currentRoundId;
        this.currentRoundLoaded = currentRoundLoaded;
        this.activeReaderIndex = activeReaderIndex;
        this.activeReaderLoaded = activeReaderLoaded;
        this.activeReaderKey = activeReaderKey;
        this.readOrder = readOrder;
        this.readingComplete = readingComplete;
        this.replayState = replayState;
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

    /**
     * Flags a player as removed instead of deleting their record.
     *
     * <p>Deleting would take their If and Then with them, and during reading those are already
     * woven into sentences <em>other</em> players are about to read - so the people who stayed
     * would lose content. The flag stops them counting for progression, votes and the roster while
     * leaving what they wrote intact.
     */
    public void markPlayerRemoved(String roomId, String playerKey, Runnable onSuccess) {
        if (roomId == null || roomId.length() == 0 || playerKey == null || playerKey.length() == 0) {
            AppLog.w(AppLog.ROOM, "Remove player skipped: missing room or player key");
            return;
        }
        Map<String, Object> update = new HashMap<String, Object>();
        update.put("removed", true);
        db.child("rooms").child(roomId).child("players").child(playerKey).updateChildren(update)
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.ROOM, "Player removed from round room=" + roomId + ", player=" + playerKey);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE,
                        "Failed removing player room=" + roomId + ", player=" + playerKey, e));
    }

    /**
     * Removed players are skipped, so a rebuilt round never references someone who has left - no
     * stranded If waiting on a Then that will never be written, and no reader slot for an absent
     * player. This is the single place that guarantees it, rather than every caller remembering to
     * filter first.
     */
    private Map<String, User> buildUsersByKey(ObservableArrayList<User> users) {
        Map<String, User> usersByKey = new HashMap<String, User>();
        for (User user : users) {
            if (!GameFlowPolicy.isActivePlayer(user)) {
                continue;
            }
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
}
