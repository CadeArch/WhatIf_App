package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.test.core.app.ApplicationProvider;

import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;
import com.CadeMixedUpGame.api.GameFlowPolicy;
import com.CadeMixedUpGame.api.repositories.FirebaseGameRepository;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Characterization tests for the abandoned-room sweep and its once-a-day claim, written before
 * that code was extracted out of {@code RoomViewModel} so the extraction could be proven not to
 * change behavior — the same assertions pass against the implementation in its old home and its
 * new one.
 *
 * <p>This behavior previously had no emulator coverage at all: only the pure decision
 * ({@code GameFlowPolicy.isRoomAbandoned}/{@code isMaintenanceSweepDue}) was unit tested, while the
 * part that actually reads every room, decides, and deletes was verified once by hand on a device.
 * That is precisely the code an extraction can silently break.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RoomMaintenanceEmulatorTest {
    private static final int WAIT_SECONDS = 10;

    private FirebaseApp app;
    private DatabaseReference db;
    private RoomViewModel room;

    @Before
    public void setUp() throws InterruptedException {
        assumeTrue("Firebase Emulator Suite not running on 127.0.0.1:9000 - start it with "
                        + "`firebase emulators:start --only database,auth` to run this test",
                emulatorReachable());

        FirebaseOptions options = new FirebaseOptions.Builder()
                .setApiKey("fake-api-key")
                .setApplicationId("1:0:android:0")
                .setProjectId("demo-mixedupgame")
                .setDatabaseUrl("https://demo-mixedupgame-default-rtdb.firebaseio.com")
                .build();

        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "maint-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(app, "localhost", true);
        // One getReference() per FirebaseApp, reused - a second call yields a reference whose
        // writes never deliver their completion callback in this harness.
        db = FirebaseDatabase.getInstance(app).getReference();
        room = new RoomViewModel(new FirebaseGameRepository(db));

        // rooms/ and maintenance/ are global, not room-scoped, and the emulator keeps data across
        // test methods and gradle invocations, so a previous run's leftovers would otherwise decide
        // these assertions. Same reason LeaderBoardEmulatorTest wipes leaderBoard in @Before.
        awaitRemoval(db.child("rooms"));
        awaitRemoval(db.child("maintenance"));
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.delete();
        }
    }

    @Test
    public void sweepDeletesRoomsWhoseHostStoppedHeartbeatingButKeepsLiveOnes() throws InterruptedException {
        long now = System.currentTimeMillis();
        // The host writes lastSeenAt every second, so an hour-old heartbeat is thousands of missed
        // beats - the game is long over.
        writeRoomWithHeartbeat("maint-dead", now - (60L * 60L * 1000L));
        writeRoomWithHeartbeat("maint-live", now - 2000L);

        room.cleanupAbandonedRooms(now);

        waitUntil(() -> !roomExists("maint-dead"));
        assertFalse("a room with a long-stale heartbeat must be swept", roomExists("maint-dead"));
        assertTrue("a room whose host is still heartbeating must never be swept", roomExists("maint-live"));
    }

    @Test
    public void sweepUsesCreatedAtWhenTheHostNeverHeartbeatedAtAll() throws InterruptedException {
        long now = System.currentTimeMillis();
        // Created and walked away from before the first heartbeat ever landed.
        writeRoomWithCreatedAt("maint-stillborn", now - GameFlowPolicy.ABANDONED_ROOM_TTL_MS - 60_000L);
        writeRoomWithCreatedAt("maint-fresh", now);

        room.cleanupAbandonedRooms(now);

        waitUntil(() -> !roomExists("maint-stillborn"));
        assertFalse("an old room that never heartbeated must be swept", roomExists("maint-stillborn"));
        assertTrue("a just-created room must survive", roomExists("maint-fresh"));
    }

    @Test
    public void sweepDeletesATombstonedRoomImmediatelyAndClearsItsMarker() throws InterruptedException {
        long now = System.currentTimeMillis();
        // Heartbeat from a second ago, so age alone would keep it - but the app has already
        // declared the room dead, and that wins.
        writeRoomWithHeartbeat("maint-tombstoned", now - 1000L);
        awaitWrite(db.child("expiredRooms").child("maint-tombstoned").child("expiredAt"), now);

        room.cleanupAbandonedRooms(now);

        waitUntil(() -> !roomExists("maint-tombstoned"));
        assertFalse("a tombstoned room must go regardless of how recent its heartbeat is",
                roomExists("maint-tombstoned"));
        waitUntil(() -> !exists(db.child("expiredRooms").child("maint-tombstoned")));
        assertFalse("the tombstone has nothing left to guard once the room is gone",
                exists(db.child("expiredRooms").child("maint-tombstoned")));
    }

    @Test
    public void firstClientToLaunchClaimsTheDailySweepAndTheRestDoNothing() throws InterruptedException {
        long now = System.currentTimeMillis();
        writeRoomWithHeartbeat("maint-claim-dead", now - (60L * 60L * 1000L));

        room.runDailyMaintenanceIfDue(now);
        waitUntil(() -> !roomExists("maint-claim-dead"));
        assertFalse("the client that wins the claim does the sweep", roomExists("maint-claim-dead"));

        long claimedAt = readLong(db.child("maintenance").child("lastSweepAt"));
        assertEquals("the claim records when it ran", now, claimedAt);

        // A second launch a minute later - well inside the interval - must not sweep again.
        writeRoomWithHeartbeat("maint-claim-second", now - (60L * 60L * 1000L));
        room.runDailyMaintenanceIfDue(now + 60_000L);
        Thread.sleep(1500L);
        assertTrue("a client launching again the same day must not sweep", roomExists("maint-claim-second"));
        assertEquals("and must not move the claim", claimedAt, readLong(db.child("maintenance").child("lastSweepAt")));
    }

    @Test
    public void theSweepRunsAgainOnceTheIntervalHasPassed() throws InterruptedException {
        long now = System.currentTimeMillis();
        room.runDailyMaintenanceIfDue(now);
        waitUntil(() -> readLong(db.child("maintenance").child("lastSweepAt")) == now);

        writeRoomWithHeartbeat("maint-nextday-dead", now - (60L * 60L * 1000L));
        long nextDay = now + GameFlowPolicy.MAINTENANCE_SWEEP_INTERVAL_MS;

        room.runDailyMaintenanceIfDue(nextDay);

        waitUntil(() -> !roomExists("maint-nextday-dead"));
        assertFalse("once the interval has elapsed the next launch sweeps again", roomExists("maint-nextday-dead"));
    }

    private void writeRoomWithHeartbeat(String roomId, long lastSeenAt) throws InterruptedException {
        awaitWrite(db.child("rooms").child(roomId).child("roomID"), roomId);
        awaitWrite(db.child("rooms").child(roomId).child("hostConnection").child("lastSeenAt"), lastSeenAt);
    }

    private void writeRoomWithCreatedAt(String roomId, long createdAt) throws InterruptedException {
        awaitWrite(db.child("rooms").child(roomId).child("roomID"), roomId);
        awaitWrite(db.child("rooms").child(roomId).child("createdAt"), createdAt);
    }

    private void awaitWrite(DatabaseReference ref, Object value) throws InterruptedException {
        CountDownLatch written = new CountDownLatch(1);
        ref.setValue(value).addOnCompleteListener(t -> written.countDown());
        assertTrue("write timed out for " + ref, awaitLatch(written));
    }

    private void awaitRemoval(DatabaseReference ref) throws InterruptedException {
        CountDownLatch removed = new CountDownLatch(1);
        ref.removeValue().addOnCompleteListener(t -> removed.countDown());
        assertTrue("removal timed out for " + ref, awaitLatch(removed));
    }

    private boolean roomExists(String roomId) {
        return exists(db.child("rooms").child(roomId));
    }

    private boolean exists(DatabaseReference ref) {
        AtomicReference<Boolean> found = new AtomicReference<Boolean>(null);
        ref.get().addOnCompleteListener(task ->
                found.set(task.isSuccessful() && task.getResult() != null && task.getResult().exists()));
        try {
            waitUntil(() -> found.get() != null);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
        return Boolean.TRUE.equals(found.get());
    }

    private long readLong(DatabaseReference ref) {
        AtomicReference<Long> value = new AtomicReference<Long>(null);
        AtomicReference<Boolean> done = new AtomicReference<Boolean>(null);
        ref.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                DataSnapshot snapshot = task.getResult();
                value.set(snapshot.getValue(Long.class));
            }
            done.set(true);
        });
        try {
            waitUntil(() -> done.get() != null);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return value.get() == null ? -1L : value.get();
    }

    private boolean emulatorReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 9000), 1000);
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    private interface Condition {
        boolean isMet();
    }

    private boolean awaitLatch(CountDownLatch latch) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(25);
        }
        return latch.getCount() == 0;
    }

    private void waitUntil(Condition condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
        while (!condition.isMet() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(25);
        }
    }
}
