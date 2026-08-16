package com.CadeMixedUpGame.api.repositories;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.models.User;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Who is currently in a room and whether the host is still alive: the {@code onDisconnect}
 * registrations, the host heartbeat, and the listener that tells guests their host has gone quiet.
 *
 * <p>Split out of {@code UserViewModel}. This is the disconnect/replay behavior the steering doc
 * warns against refactoring casually, so it was moved only after
 * {@code RoomPresenceEmulatorTest} pinned it with tests that drive a **real socket drop** (a second
 * FirebaseApp observing while this one calls {@code goOffline()}) — and those tests were themselves
 * verified to fail when the {@code onDisconnect} registration is removed, so they are not passing
 * vacuously. They pass unchanged against this class.
 *
 * <p>Owns no policy: the grace/expiry timing lives in {@code GameFlowPolicy} and
 * {@code HostDisconnectScheduler}, and the {@code hostDisconnectedAt}/{@code hostLastSeenAt}
 * LiveData handed in here is what the Activity observes to drive that.
 */
public class RoomPresenceRepository {
    private final DatabaseReference db;
    private final MutableLiveData<User> user;
    private final MutableLiveData<Long> hostDisconnectedAt;
    private final MutableLiveData<Long> hostLastSeenAt;
    private final MutableLiveData<String> databaseMessage;
    private final MutableLiveData<String> hostDisconnectedMessage;

    private DatabaseReference onDisconnectPlayerRef;
    private String onDisconnectPlayerPath;
    private DatabaseReference onDisconnectHostConnectionRef;
    private String onDisconnectHostConnectionRoom;
    private ValueEventListener hostConnectionListener;
    private String hostConnectionListenerRoom;

    public RoomPresenceRepository(DatabaseReference db,
                                  MutableLiveData<User> user,
                                  MutableLiveData<Long> hostDisconnectedAt,
                                  MutableLiveData<Long> hostLastSeenAt,
                                  MutableLiveData<String> databaseMessage,
                                  MutableLiveData<String> hostDisconnectedMessage) {
        this.db = db;
        this.user = user;
        this.hostDisconnectedAt = hostDisconnectedAt;
        this.hostLastSeenAt = hostLastSeenAt;
        this.databaseMessage = databaseMessage;
        this.hostDisconnectedMessage = hostDisconnectedMessage;
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

    public void registerOnDisconnectCleanup(User user, DatabaseReference ref) {
        String path = playerPath(user);
        if (path.length() == 0 || ref == null) {
            AppLog.w(AppLog.FIREBASE, "onDisconnect registration skipped: missing player path");
            return;
        }
        if (path.equals(onDisconnectPlayerPath)) {
            AppLog.d(AppLog.FIREBASE, "onDisconnect already registered path=" + path);
            registerHostConnectionOnDisconnect(user);
            return;
        }
        cancelOnDisconnectCleanup();
        onDisconnectPlayerRef = ref;
        onDisconnectPlayerPath = path;
        ref.onDisconnect().updateChildren(disconnectedUpdate())
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Registered onDisconnect player cleanup path=" + path))
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not set disconnect cleanup. If someone drops, the host may need to recreate the room.");
                    AppLog.e(AppLog.FIREBASE, "Failed registering onDisconnect cleanup path=" + path, e);
                });
        registerHostConnectionOnDisconnect(user);
    }

    public void cancelOnDisconnectCleanup() {
        if (onDisconnectPlayerRef != null && onDisconnectPlayerPath != null) {
            String path = onDisconnectPlayerPath;
            onDisconnectPlayerRef.onDisconnect().cancel()
                    .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Cancelled onDisconnect player cleanup path=" + path))
                    .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed cancelling onDisconnect cleanup path=" + path, e));
            onDisconnectPlayerRef = null;
            onDisconnectPlayerPath = null;
        }
        cancelHostConnectionOnDisconnect();
    }

    private void registerHostConnectionOnDisconnect(User user) {
        if (user == null || !user.host || user.gameRoom == null || user.gameRoom.length() == 0) {
            return;
        }
        if (user.gameRoom.equals(onDisconnectHostConnectionRoom) && onDisconnectHostConnectionRef != null) {
            AppLog.d(AppLog.FIREBASE, "Host connection onDisconnect already registered room=" + user.gameRoom);
            return;
        }
        cancelHostConnectionOnDisconnect();
        onDisconnectHostConnectionRoom = user.gameRoom;
        onDisconnectHostConnectionRef = hostConnectionRef(user.gameRoom);
        onDisconnectHostConnectionRef.onDisconnect().updateChildren(disconnectedUpdate())
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Registered host connection onDisconnect room=" + user.gameRoom))
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed registering host connection onDisconnect room=" + user.gameRoom, e));
    }

    private void cancelHostConnectionOnDisconnect() {
        if (onDisconnectHostConnectionRef == null || onDisconnectHostConnectionRoom == null) {
            return;
        }
        String room = onDisconnectHostConnectionRoom;
        onDisconnectHostConnectionRef.onDisconnect().cancel()
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Cancelled host connection onDisconnect room=" + room))
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed cancelling host connection onDisconnect room=" + room, e));
        onDisconnectHostConnectionRef = null;
        onDisconnectHostConnectionRoom = null;
    }

    /**
     * Re-arms presence after the app returns to the foreground.
     *
     * <p><b>This does not fix the connection wedge, and is not claimed to.</b> Measured repeatedly
     * on an emulator: backgrounding drops the Realtime Database socket after ~30-60s while the
     * process is still alive, and afterwards the SDK reconnects only on its own exponential backoff
     * - observed at 82s, 4m17s, and still down at 3m in another run. None of {@code goOnline()},
     * {@code goOffline()+goOnline()}, or the two separated by 750ms changed that, and a
     * {@code goOffline()} on resume was removed because a *brief* app switch does not drop the
     * socket, so forcing offline there would fire the onDisconnect handlers and mark a perfectly
     * connected player as gone. Cade confirms this wedge happens in real games on production, so it
     * is a genuine bug, not a local-rig artefact. See README's roadmap item.
     *
     * <p>What this method does still earn: re-arming the onDisconnect handlers. They are
     * <b>consumed when they fire</b>, so after a real drop the server holds none for this client -
     * yet {@code onDisconnectPlayerPath}/{@code onDisconnectHostConnectionRoom} still say
     * "registered", and both register methods early-return on that. Left alone, a resumed player
     * would have no cleanup armed at all, so their *next* disconnect would go unnoticed forever.
     * Clearing the bookkeeping first is what forces a genuine re-arm.
     */
    public void reconnectAfterResume() {
        // goOnline() is a cheap no-op when already online; it is not what fixes the wedge (see javadoc).
        db.getDatabase().goOnline();
        User currentUser = user.getValue();
        if (currentUser == null || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0
                || currentUser.userName == null) {
            AppLog.d(AppLog.FIREBASE, "Resume reconnect: online again, not in a room");
            return;
        }
        AppLog.i(AppLog.FIREBASE, "Resume reconnect: re-arming presence room=" + currentUser.gameRoom);
        onDisconnectPlayerRef = null;
        onDisconnectPlayerPath = null;
        onDisconnectHostConnectionRef = null;
        onDisconnectHostConnectionRoom = null;
        registerOnDisconnectCleanup(currentUser, playerRef(currentUser));
        markCurrentPlayerConnected();
    }

    public void markCurrentPlayerConnected() {
        User currentUser = user.getValue();
        if (currentUser == null || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0 || currentUser.userName == null) {
            return;
        }
        if (currentUser.host) {
            markHostConnectedIfRoomActive(currentUser);
            return;
        }
        markUserConnectedLocally(currentUser);
        listenToHostConnection(currentUser.gameRoom);
        playerRef(currentUser).updateChildren(connectedUpdate())
                .addOnSuccessListener(unused -> AppLog.d(AppLog.FIREBASE, "Marked player connected room=" + currentUser.gameRoom))
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed marking player connected room=" + currentUser.gameRoom, e));
        markHostConnectionConnectedIfNeeded(currentUser);
    }

    public void markHostConnectionConnectedIfNeeded(User currentUser) {
        if (currentUser == null || !currentUser.host || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0) {
            return;
        }
        hostConnectionRef(currentUser.gameRoom).updateChildren(hostConnectedUpdate())
                .addOnSuccessListener(unused -> AppLog.d(AppLog.FIREBASE, "Marked host connection online room=" + currentUser.gameRoom))
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed marking host connection online room=" + currentUser.gameRoom, e));
    }

    public void writeHostHeartbeat() {
        User currentUser = user.getValue();
        if (currentUser == null || !currentUser.host || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0) {
            return;
        }
        runIfHostRoomActive(currentUser, () ->
                hostConnectionRef(currentUser.gameRoom).updateChildren(hostConnectedUpdate())
                        .addOnSuccessListener(unused -> AppLog.d(AppLog.FIREBASE, "Host heartbeat written room=" + currentUser.gameRoom))
                        .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed writing host heartbeat room=" + currentUser.gameRoom, e)));
    }

    private void markHostConnectedIfRoomActive(User currentUser) {
        runIfHostRoomActive(currentUser, () -> {
            markUserConnectedLocally(currentUser);
            listenToHostConnection(currentUser.gameRoom);
            playerRef(currentUser).updateChildren(connectedUpdate())
                    .addOnSuccessListener(unused -> AppLog.d(AppLog.FIREBASE, "Marked host player connected room=" + currentUser.gameRoom))
                    .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed marking host player connected room=" + currentUser.gameRoom, e));
            markHostConnectionConnectedIfNeeded(currentUser);
        });
    }

    private void runIfHostRoomActive(User currentUser, Runnable activeRoomAction) {
        db.child("expiredRooms").child(currentUser.gameRoom).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        AppLog.w(AppLog.ROOM, "Host room already expired; blocking room writes room=" + currentUser.gameRoom);
                        hostDisconnectedMessage.setValue("Game room ended while connection was lost. Create a new game!");
                        return;
                    }
                    db.child("rooms").child(currentUser.gameRoom).get()
                            .addOnCompleteListener(roomTask -> {
                                if (!roomTask.isSuccessful() || roomTask.getResult() == null || !roomTask.getResult().exists()) {
                                    AppLog.w(AppLog.ROOM, "Host room missing; blocking room writes room=" + currentUser.gameRoom);
                                    hostDisconnectedMessage.setValue("Game room ended while connection was lost. Create a new game!");
                                    return;
                                }
                                activeRoomAction.run();
                            });
                });
    }

    public void listenToHostConnection(String room) {
        if (room == null || room.length() == 0) {
            return;
        }
        if (room.equals(hostConnectionListenerRoom) && hostConnectionListener != null) {
            return;
        }
        removeHostConnectionListener();
        hostConnectionListenerRoom = room;
        hostConnectionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                handleHostConnectionSnapshot(room, snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                AppLog.e(AppLog.FIREBASE, "Host connection listener cancelled room=" + room + ": " + error.getMessage());
            }
        };
        AppLog.i(AppLog.FIREBASE, "Attaching host connection listener room=" + room);
        hostConnectionRef(room).addValueEventListener(hostConnectionListener);
    }

    public void removeHostConnectionListener() {
        if (hostConnectionListener != null && hostConnectionListenerRoom != null) {
            AppLog.i(AppLog.FIREBASE, "Removing host connection listener room=" + hostConnectionListenerRoom);
            hostConnectionRef(hostConnectionListenerRoom).removeEventListener(hostConnectionListener);
            hostConnectionListener = null;
            hostConnectionListenerRoom = null;
        }
    }

    private void handleHostConnectionSnapshot(String room, DataSnapshot snapshot) {
        User currentUser = user.getValue();
        if (currentUser == null || currentUser.host) {
            return;
        }
        if (snapshot == null || !snapshot.exists()) {
            hostDisconnectedAt.setValue(0L);
            hostLastSeenAt.setValue(0L);
            return;
        }
        Long lastSeenAtValue = snapshot.child("lastSeenAt").getValue(Long.class);
        if (lastSeenAtValue != null && lastSeenAtValue > 0L) {
            hostLastSeenAt.setValue(lastSeenAtValue);
        }
        Boolean connected = snapshot.child("connected").getValue(Boolean.class);
        if (Boolean.FALSE.equals(connected)) {
            Long disconnectedAtValue = snapshot.child("disconnectedAt").getValue(Long.class);
            long safeDisconnectedAt = disconnectedAtValue == null ? System.currentTimeMillis() : disconnectedAtValue;
            AppLog.w(AppLog.ROOM, "Room host connection marked offline room=" + room + ", disconnectedAt=" + safeDisconnectedAt);
            hostDisconnectedAt.setValue(safeDisconnectedAt);
            return;
        }
        if (hostDisconnectedAt.getValue() != null && hostDisconnectedAt.getValue() > 0L) {
            AppLog.i(AppLog.ROOM, "Room host connection recovered room=" + room);
        }
        hostDisconnectedAt.setValue(0L);
    }

    public void markUserConnectedLocally(User user) {
        if (user == null) {
            return;
        }
        user.connected = true;
        user.disconnectedAt = 0L;
    }

    private Map<String, Object> connectedUpdate() {
        Map<String, Object> update = new HashMap<String, Object>();
        update.put("connected", true);
        update.put("disconnectedAt", 0L);
        return update;
    }

    private Map<String, Object> hostConnectedUpdate() {
        Map<String, Object> update = connectedUpdate();
        update.put("lastSeenAt", ServerValue.TIMESTAMP);
        return update;
    }

    private Map<String, Object> disconnectedUpdate() {
        Map<String, Object> update = new HashMap<String, Object>();
        update.put("connected", false);
        update.put("disconnectedAt", ServerValue.TIMESTAMP);
        return update;
    }

    private DatabaseReference hostConnectionRef(String room) {
        return db.child("rooms").child(room).child("hostConnection");
    }
}
