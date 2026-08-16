package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import com.CadeMixedUpGame.api.AppLog;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Writes room state directly, so a Tier B test can put the app in a situation that would otherwise
 * need a second device behaving in a very specific way at a very specific moment.
 *
 * <h3>Why fabricate the other player instead of driving two devices</h3>
 * The flows this supports - the host removing someone who has gone quiet, a guest escaping a room
 * whose host has vanished - all hinge on <em>another</em> player being absent for a while. Driving
 * that for real means launching a second device, joining, then killing it at the right instant and
 * hoping the timing holds; that is a lot of moving parts for a test whose subject is the UI on
 * <em>this</em> device. Writing the other player straight into the database is deterministic, and
 * the app under test cannot tell the difference: it reads the same room, runs the same listeners
 * and the same policy.
 *
 * <p>Timing is expressed as data for the same reason (see {@code whatif-testing} §0d): nothing
 * here shortens a threshold or flips a debug flag, it just backdates {@code disconnectedAt} so the
 * real ninety-second rule is genuinely satisfied.
 */
final class E2ERoomFixture {
    private static final long CALL_TIMEOUT_SECONDS = 20L;

    private E2ERoomFixture() {
    }

    /** Adds a player who has been disconnected for {@code goneForMs}, i.e. removable if long enough. */
    static void addAbsentPlayer(String roomId, String userName, int userID, long goneForMs) {
        Map<String, Object> player = basePlayer(roomId, userName, userID, false);
        player.put("connected", false);
        player.put("disconnectedAt", System.currentTimeMillis() - goneForMs);
        write(playerRef(roomId, userName, userID), player, "add absent player");
    }

    /**
     * Adds an absent player who <b>already wrote an If</b> before vanishing.
     *
     * <p>This is the harder case and the one worth testing: their sentence is part of the round, so
     * removing them is not just "one fewer body". Every player writes one If and one Then, and the
     * assignments pair them up - so losing a player between the two phases leaves an If with no Then
     * to go with it unless the round is rebuilt. A player who never wrote anything cannot expose
     * that.
     */
    static void addAbsentPlayerWithIf(String roomId, String userName, int userID, long goneForMs,
                                      String ifSentence) {
        Map<String, Object> player = basePlayer(roomId, userName, userID, false);
        player.put("connected", false);
        player.put("disconnectedAt", System.currentTimeMillis() - goneForMs);
        player.put("ifFinished", true);
        player.put("ifSentence", ifSentence);
        write(playerRef(roomId, userName, userID), player, "add absent player with if");
    }

    /**
     * Adds an absent player who finished <b>both</b> their If and Then before vanishing.
     *
     * <p>For the reading-phase case: with all their writing in, the round advances to reading with
     * them still in the read order. Reading moves by matching the active reader's key, so their slot
     * is a dead stop for everyone unless the host can take it - which is the thing under test.
     */
    static void addAbsentPlayerWhoFinishedWriting(String roomId, String userName, int userID,
                                                  long goneForMs, String ifSentence, String thenSentence) {
        Map<String, Object> player = basePlayer(roomId, userName, userID, false);
        player.put("connected", false);
        player.put("disconnectedAt", System.currentTimeMillis() - goneForMs);
        player.put("ifFinished", true);
        player.put("ifSentence", ifSentence);
        player.put("thenFinished", true);
        player.put("thenSentence", thenSentence);
        write(playerRef(roomId, userName, userID), player, "add absent player who finished writing");
    }

    /**
     * As above, but an <em>account</em> player - voting only happens when everyone has an account,
     * so a free-play stand-in would make the round skip the vote entirely and prove nothing.
     */
    static void addAbsentAccountPlayerWhoFinishedWriting(String roomId, String userName, int userID,
                                                        long goneForMs, String ifSentence, String thenSentence) {
        Map<String, Object> player = basePlayer(roomId, userName, userID, false);
        player.put("accountPlay", true);
        player.put("uid", "ghost-uid-" + userID);
        player.put("email", userName.toLowerCase() + "@example.com");
        player.put("connected", false);
        player.put("disconnectedAt", System.currentTimeMillis() - goneForMs);
        player.put("ifFinished", true);
        player.put("ifSentence", ifSentence);
        player.put("thenFinished", true);
        player.put("thenSentence", thenSentence);
        write(playerRef(roomId, userName, userID), player, "add absent account player");
    }

    /** Adds a normal, present player. */
    static void addPresentPlayer(String roomId, String userName, int userID, boolean host) {
        Map<String, Object> player = basePlayer(roomId, userName, userID, host);
        player.put("connected", true);
        player.put("disconnectedAt", 0L);
        write(playerRef(roomId, userName, userID), player, "add present player");
    }

    /** Creates a room owned by a host who has not been seen for {@code staleForMs}. */
    static void createRoomWithAwayHost(String roomId, String hostName, int hostID, long staleForMs) {
        Map<String, Object> room = new HashMap<String, Object>();
        room.put("roomID", roomId);
        room.put("gameInProgress", false);
        room.put("createdAt", System.currentTimeMillis() - staleForMs);
        write(E2ERoomCodeSignal.testRoot().child("rooms").child(roomId), room, "create room");

        addPresentPlayer(roomId, hostName, hostID, true);
        setHostConnectionStale(roomId, staleForMs);
    }

    /**
     * Makes the host look away: still flagged connected, but with a heartbeat that stopped a while
     * ago. That is the shape a locked phone actually produces - measured on a real device, the
     * socket stays up and {@code onDisconnect} never fires while the heartbeat simply stops - so a
     * test that only flipped {@code connected} to false would be rehearsing the wrong failure.
     */
    static void setHostConnectionStale(String roomId, long staleForMs) {
        long staleAt = System.currentTimeMillis() - staleForMs;
        Map<String, Object> hostConnection = new HashMap<String, Object>();
        hostConnection.put("connected", true);
        hostConnection.put("lastSeenAt", staleAt);
        hostConnection.put("disconnectedAt", staleAt);
        write(E2ERoomCodeSignal.testRoot().child("rooms").child(roomId).child("hostConnection"),
                hostConnection, "stale host connection");
    }

    static String playerKey(String userName, int userID) {
        return userName + "-" + userID;
    }

    /** Waits for a player to be gone from the room - either flagged removed, or deleted outright. */
    static void awaitPlayerGone(String roomId, String playerKey, long timeoutMs) {
        DatabaseReference ref = E2ERoomCodeSignal.testRoot()
                .child("rooms").child(roomId).child("players").child(playerKey);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> state = new AtomicReference<String>("still present");
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    state.set("deleted");
                    latch.countDown();
                    return;
                }
                if (Boolean.TRUE.equals(snapshot.child("removed").getValue(Boolean.class))) {
                    state.set("flagged removed");
                    latch.countDown();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                state.set("read cancelled: " + error.getMessage());
                latch.countDown();
            }
        };
        ref.addValueEventListener(listener);
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                fail("player " + playerKey + " was still in room " + roomId + " after " + timeoutMs
                        + "ms (" + state.get() + ")");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            ref.removeEventListener(listener);
        }
        AppLog.i(AppLog.ROOM, "E2E fixture: player " + playerKey + " -> " + state.get());
    }

    /** A room whose host is present and healthy - for tests where this device is the guest. */
    static void createRoomWithPresentHost(String roomId, String hostName, int hostID) {
        Map<String, Object> room = new HashMap<String, Object>();
        room.put("roomID", roomId);
        room.put("gameInProgress", false);
        room.put("createdAt", System.currentTimeMillis());
        write(E2ERoomCodeSignal.testRoot().child("rooms").child(roomId), room, "create room");
        addPresentPlayer(roomId, hostName, hostID, true);

        Map<String, Object> hostConnection = new HashMap<String, Object>();
        hostConnection.put("connected", true);
        hostConnection.put("lastSeenAt", System.currentTimeMillis());
        hostConnection.put("disconnectedAt", 0L);
        write(E2ERoomCodeSignal.testRoot().child("rooms").child(roomId).child("hostConnection"),
                hostConnection, "present host connection");
    }

    /** Stands in for the host removing someone. */
    static void markPlayerRemoved(String roomId, String playerKey) {
        Map<String, Object> update = new HashMap<String, Object>();
        update.put("removed", true);
        CountDownLatch latch = new CountDownLatch(1);
        E2ERoomCodeSignal.testRoot().child("rooms").child(roomId).child("players").child(playerKey)
                .updateChildren(update).addOnCompleteListener(task -> latch.countDown());
        try {
            if (!latch.await(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                fail("fixture write timed out (mark removed)");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Finds a player key by name, because the joining device generates its own random userID and
     * the test cannot know it in advance.
     */
    static String findPlayerKeyByName(String roomId, String userName) {
        DatabaseReference ref = E2ERoomCodeSignal.testRoot().child("rooms").child(roomId).child("players");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> found = new AtomicReference<String>(null);
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    String key = child.getKey();
                    if (key != null && key.startsWith(userName + "-")) {
                        found.set(key);
                        latch.countDown();
                        return;
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                latch.countDown();
            }
        };
        ref.addValueEventListener(listener);
        try {
            latch.await(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            ref.removeEventListener(listener);
        }
        if (found.get() == null) {
            fail("no player named " + userName + " in room " + roomId);
        }
        return found.get();
    }

    /** Fails if the room has gone - used where the point of the test is that it survives. */
    static void assertRoomExists(String roomId) {
        DatabaseReference ref = E2ERoomCodeSignal.testRoot().child("rooms").child(roomId);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> exists = new AtomicReference<Boolean>(false);
        ref.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                exists.set(task.getResult().exists());
            }
            latch.countDown();
        });
        try {
            latch.await(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!Boolean.TRUE.equals(exists.get())) {
            fail("room " + roomId + " no longer exists - something reaped it");
        }
    }

    static void deleteRoom(String roomId) {
        if (roomId == null || roomId.length() == 0) {
            return;
        }
        write(E2ERoomCodeSignal.testRoot().child("rooms").child(roomId), null, "delete room");
    }

    private static Map<String, Object> basePlayer(String roomId, String userName, int userID, boolean host) {
        Map<String, Object> player = new HashMap<String, Object>();
        player.put("userName", userName);
        player.put("userID", userID);
        player.put("gameRoom", roomId);
        player.put("host", host);
        player.put("accountPlay", false);
        player.put("ifFinished", false);
        player.put("thenFinished", false);
        player.put("ifSentence", "");
        player.put("thenSentence", "");
        player.put("playAgain", false);
        player.put("hostPlayedAgain", "");
        player.put("uid", "");
        player.put("email", "");
        player.put("gamesPlayed", 0);
        player.put("madeLeaderBoard", false);
        player.put("perfectLeaderBoard", false);
        return player;
    }

    private static DatabaseReference playerRef(String roomId, String userName, int userID) {
        return E2ERoomCodeSignal.testRoot().child("rooms").child(roomId).child("players")
                .child(playerKey(userName, userID));
    }

    private static void write(DatabaseReference ref, Object value, String what) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<Exception>();
        ref.setValue(value).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                failure.set(task.getException());
            }
            latch.countDown();
        });
        try {
            if (!latch.await(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                fail("fixture write timed out (" + what + ")");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (failure.get() != null) {
            fail("fixture write failed (" + what + "): " + failure.get());
        }
    }
}
