package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.test.core.app.ApplicationProvider;

import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;
import com.CadeMixedUpGame.api.repositories.FirebaseGameRepository;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Covers the expiredRooms tombstone cleanup paths beyond the markRoomExpired/listenToExpiredRoom
 * round trip already covered by HostLeaveEmulatorTest: a reconnecting host removing its own
 * marker (deleteExpiredRoomMarker), and the app-startup sweep of markers older than 24 hours
 * (cleanupOldExpiredRoomMarkers) - only the pure timing math had coverage before this session, not
 * the actual Firebase read + conditional delete. Same harness/pattern as MultiplayerEmulatorTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RoomExpiryCleanupEmulatorTest {
    private static final int WAIT_SECONDS = 10;

    private FirebaseApp app;
    private DatabaseReference db;
    private RoomViewModel room;

    @Before
    public void setUp() {
        assumeTrue("Firebase Emulator Suite not running on 127.0.0.1:9000 - start it with "
                        + "`firebase emulators:start --only database,auth` to run this test",
                emulatorReachable());

        FirebaseOptions options = new FirebaseOptions.Builder()
                .setApiKey("fake-api-key")
                .setApplicationId("1:0:android:0")
                .setProjectId("demo-mixedupgame")
                .setDatabaseUrl("https://demo-mixedupgame-default-rtdb.firebaseio.com")
                .build();

        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "rec-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(app, "localhost", true);
        db = FirebaseDatabase.getInstance(app).getReference();
        room = new RoomViewModel(new FirebaseGameRepository(db));
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.delete();
        }
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

    @Test
    public void reconnectingHostCanDeleteItsOwnExpiredRoomMarker() throws InterruptedException {
        String roomId = "expiry-room-" + System.nanoTime();
        CountDownLatch marked = new CountDownLatch(1);
        room.markRoomExpired(roomId, "Host disconnected", marked::countDown);
        assertTrue("markRoomExpired timed out", awaitLatch(marked));
        assertTrue("marker must exist right after being written", markerExists(roomId));

        room.deleteExpiredRoomMarker(roomId);
        waitUntil(() -> !markerExists(roomId));
        assertFalse("reconnecting host must be able to clear its own expired-room marker", markerExists(roomId));
    }

    @Test
    public void cleanupOnlyDeletesMarkersOlderThanTheCutoff() throws InterruptedException {
        String oldRoomId = "expiry-old-" + System.nanoTime();
        String recentRoomId = "expiry-recent-" + System.nanoTime();
        long now = System.currentTimeMillis();
        long oldExpiredAt = now - (25 * 60 * 60 * 1000L); // 25 hours ago
        long recentExpiredAt = now - (1 * 60 * 60 * 1000L); // 1 hour ago
        long cutoff = now - (24 * 60 * 60 * 1000L); // 24-hour sweep window, matches production usage

        writeMarker(oldRoomId, oldExpiredAt);
        writeMarker(recentRoomId, recentExpiredAt);
        assertTrue(markerExists(oldRoomId));
        assertTrue(markerExists(recentRoomId));

        room.cleanupOldExpiredRoomMarkers(cutoff);

        waitUntil(() -> !markerExists(oldRoomId));
        assertFalse("marker older than the cutoff must be deleted", markerExists(oldRoomId));
        assertTrue("marker newer than the cutoff must be left alone", markerExists(recentRoomId));
    }

    private void writeMarker(String roomId, long expiredAt) throws InterruptedException {
        CountDownLatch written = new CountDownLatch(1);
        db.child("expiredRooms").child(roomId).child("expiredAt").setValue(expiredAt)
                .addOnCompleteListener(t -> written.countDown());
        assertTrue("marker write timed out", awaitLatch(written));
    }

    private boolean markerExists(String roomId) {
        AtomicBoolean exists = new AtomicBoolean(true);
        try {
            CountDownLatch latch = new CountDownLatch(1);
            db.child("expiredRooms").child(roomId).get()
                    .addOnCompleteListener(t -> {
                        exists.set(t.isSuccessful() && t.getResult() != null && t.getResult().exists());
                        latch.countDown();
                    });
            awaitLatch(latch);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return exists.get();
    }

    private interface Condition {
        boolean isMet();
    }

    private boolean awaitLatch(CountDownLatch latch) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(50);
        }
        return latch.getCount() == 0;
    }

    private void waitUntil(Condition condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
        while (!condition.isMet() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(50);
        }
    }
}
