package com.CadeMixedUpGame.api.repositories;

import androidx.annotation.NonNull;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameFlowPolicy;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;

import java.util.HashSet;
import java.util.Set;

/**
 * Deletes rooms nobody is coming back to, and prunes the expired-room tombstones.
 *
 * <p>Split out of {@code RoomViewModel}, which had grown past 1100 lines covering room lifecycle,
 * round/assignment state, replay reset, reader turn order, connection listeners *and* this. Nothing
 * here is about a room the player is currently in - it is whole-table housekeeping that happens once
 * at app start - so it shares no state with the rest of that class and reads far better on its own.
 *
 * <p>{@code RoomViewModel} still exposes the same three methods and forwards to this, so the
 * Activity keeps a single entry point and the behavior stayed provably identical across the move:
 * see {@code RoomMaintenanceEmulatorTest}, written against the old implementation and passing
 * unchanged against this one.
 */
public class RoomMaintenance {
    private final DatabaseReference db;

    public RoomMaintenance(DatabaseReference db) {
        this.db = db;
    }

    /** Runs the app's housekeeping at most once per MAINTENANCE_SWEEP_INTERVAL_MS across all
     * clients - whichever device happens to launch first after it falls due does the work, and
     * everyone else that day does nothing.
     *
     * <p>The claim is a transaction on a single shared value, so simultaneous launches can't all
     * decide they are the one: exactly one commit wins and the rest abort. Without it every client
     * swept on every launch, reading the entire rooms node each time - O(users x rooms) and a pile
     * of clients racing to delete the same rooms, which is fine at small scale and untenable at
     * real throughput. The proper long-term answer is a scheduled server-side sweep instead of
     * doing this on clients at all; see README's Reliability roadmap. */
    public void runDailyMaintenanceIfDue(long nowMs) {
        db.child("maintenance").child("lastSweepAt").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Long lastSweepAt = currentData.getValue(Long.class);
                if (!GameFlowPolicy.isMaintenanceSweepDue(lastSweepAt, nowMs)) {
                    return Transaction.abort();
                }
                currentData.setValue(nowMs);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) {
                    AppLog.e(AppLog.FIREBASE, "Maintenance claim failed: " + error.getMessage());
                    return;
                }
                if (!committed) {
                    AppLog.d(AppLog.ROOM, "Maintenance sweep not due; another client already ran it");
                    return;
                }
                AppLog.i(AppLog.ROOM, "Maintenance claim won; sweeping nowMs=" + nowMs);
                cleanupAbandonedRooms(nowMs);
                cleanupOldExpiredRoomMarkers(nowMs - GameFlowPolicy.EXPIRED_ROOM_TOMBSTONE_TTL_MS);
            }
        });
    }

    /** Deletes rooms nobody is coming back to.
     *
     * <p>This is the only thing that ever removes an abandoned room. Every other deletion path
     * ({@code EndFrag} home, {@code CreateGameFrag} back, the host-disconnect handler,
     * {@code deleteRoomIfPlayersEmpty}) needs a live client to reach it, so a room whose host's
     * process simply dies - crash, force-stop, swiped away, battery, a killed instrumented test -
     * used to sit in the database forever. The {@code expiredRooms} tombstones are a different
     * mechanism and were never a room cleaner: they flag "this room is dead" so a reconnecting
     * host stops writing to it and other clients know to go home, and their own 24h prune only
     * ever removed those flags, never the rooms themselves.
     *
     * <p>Safe to run from any client on startup: {@link GameFlowPolicy#isRoomAbandoned} keys off
     * the host heartbeat, so a room with anyone actually playing in it is never a candidate. */
    public void cleanupAbandonedRooms(long nowMs) {
        AppLog.i(AppLog.ROOM, "Cleaning abandoned rooms nowMs=" + nowMs);
        // Tombstones first: a room the app already marked expired is deletable immediately, no
        // matter how recently its host was seen. Read once here rather than per room.
        db.child("expiredRooms").get().addOnCompleteListener(markersTask -> {
            Set<String> expiredRoomIds = new HashSet<String>();
            if (markersTask.isSuccessful() && markersTask.getResult() != null) {
                for (DataSnapshot marker : markersTask.getResult().getChildren()) {
                    if (marker.getKey() != null) {
                        expiredRoomIds.add(marker.getKey());
                    }
                }
            }
            deleteAbandonedRooms(nowMs, expiredRoomIds);
        });
    }

    private void deleteAbandonedRooms(long nowMs, Set<String> expiredRoomIds) {
        db.child("rooms").get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        AppLog.e(AppLog.FIREBASE, "Failed loading rooms for cleanup", task.getException());
                        return;
                    }
                    int deleted = 0;
                    for (DataSnapshot snapshot : task.getResult().getChildren()) {
                        Long hostLastSeenAt = snapshot.child("hostConnection").child("lastSeenAt").getValue(Long.class);
                        Long createdAt = snapshot.child("createdAt").getValue(Long.class);
                        boolean hasExpiredMarker = expiredRoomIds.contains(snapshot.getKey());
                        if (!GameFlowPolicy.isRoomAbandoned(hostLastSeenAt, createdAt, hasExpiredMarker, nowMs)) {
                            continue;
                        }
                        String abandonedRoomId = snapshot.getKey();
                        AppLog.i(AppLog.ROOM, "Deleting abandoned room=" + abandonedRoomId
                                + ", hostLastSeenAt=" + hostLastSeenAt + ", createdAt=" + createdAt);
                        snapshot.getRef().removeValue()
                                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed deleting abandoned room=" + abandonedRoomId, e));
                        // The room is gone, so its tombstone has nothing left to guard.
                        db.child("expiredRooms").child(abandonedRoomId).removeValue();
                        deleted += 1;
                    }
                    AppLog.i(AppLog.ROOM, "Abandoned room cleanup queued deleted=" + deleted);
                });
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
}
