package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Tier A coverage for the reading/turn-passing flow: turn advancement via setActiveReaderIndex,
 * last-reader-passes -> completeReadingAfterFinalPass, and the stale-round-id guards in
 * listenToActiveReader/listenToReadingComplete (GameLogic.isCurrentRound - already unit tested in
 * isolation, but not previously exercised through the real listener + Firebase round trip that
 * actually uses it to drop late-arriving updates from a previous round during a race).
 * Same harness/pattern as MultiplayerEmulatorTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ReadingTurnEmulatorTest {
    private static final int WAIT_SECONDS = 10;

    private FirebaseApp app;
    private DatabaseReference db;
    private RoomViewModel room;
    private String roomId;

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

        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "rt-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(app, "localhost", true);
        db = FirebaseDatabase.getInstance(app).getReference();
        room = new RoomViewModel(new FirebaseGameRepository(db));

        roomId = "reading-room-" + System.nanoTime();
        CountDownLatch created = new CountDownLatch(1);
        room.pushRoom(roomId, created::countDown);
        assertTrue("room creation timed out", awaitLatch(created));

        room.listenToCurrentRoundId(roomId);
        writeCurrentRoundId("round-1");
        waitUntil(() -> "round-1".equals(room.currentRoundId.getValue()));
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
    public void settingActiveReaderIndexRoundTripsThroughFirebaseToTheListener() throws InterruptedException {
        room.readOrder.setValue(Arrays.asList("Player1-1", "Player2-2"));
        room.listenToActiveReader(roomId);

        CountDownLatch set = new CountDownLatch(1);
        room.setActiveReaderIndex(roomId, 1, set::countDown);
        assertTrue("setActiveReaderIndex timed out", awaitLatch(set));

        waitUntil(() -> room.activeReaderIndex.getValue() != null && room.activeReaderIndex.getValue() == 1);
        assertEquals(Integer.valueOf(1), room.activeReaderIndex.getValue());
        assertEquals("activeReaderKey must be resolved from readOrder by index",
                "Player2-2", room.activeReaderKey.getValue());
    }

    @Test
    public void activeReaderUpdateFromAPreviousRoundIsIgnored() throws InterruptedException {
        room.readOrder.setValue(Arrays.asList("Player1-1", "Player2-2"));
        room.listenToActiveReader(roomId);
        // Settle on round-1's real state first so we know what "unaffected by the stale write" means.
        CountDownLatch set = new CountDownLatch(1);
        room.setActiveReaderIndex(roomId, 0, set::countDown);
        assertTrue(awaitLatch(set));
        waitUntil(() -> room.activeReaderIndex.getValue() != null && room.activeReaderIndex.getValue() == 0);

        // A late-arriving write stamped with the previous round's id - must be dropped by the
        // isCurrentRound guard, not applied on top of round-1's real state.
        Map<String, Object> staleUpdate = new HashMap<>();
        staleUpdate.put("activeReaderIndex", 5);
        staleUpdate.put("activeReaderKey", "stale-reader");
        staleUpdate.put("activeReaderRoundId", "round-0");
        CountDownLatch staleWritten = new CountDownLatch(1);
        db.child("rooms").child(roomId).updateChildren(staleUpdate).addOnCompleteListener(t -> staleWritten.countDown());
        assertTrue("stale write timed out", awaitLatch(staleWritten));

        // Give the (correctly non-applying) listener every chance to wrongly apply it.
        Thread.sleep(500);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("a stale round's activeReader update must not overwrite the current round's state",
                Integer.valueOf(0), room.activeReaderIndex.getValue());
        assertFalse("stale reader key must not leak into the current round",
                "stale-reader".equals(room.activeReaderKey.getValue()));
    }

    @Test
    public void completingTheFinalReadingPassMarksTheRoundComplete() throws InterruptedException {
        room.listenToReadingComplete(roomId);

        CountDownLatch completed = new CountDownLatch(1);
        room.completeReadingAfterFinalPass(roomId, 2, completed::countDown);
        assertTrue("completeReadingAfterFinalPass timed out", awaitLatch(completed));

        waitUntil(() -> Boolean.TRUE.equals(room.readingComplete.getValue()));
        assertTrue("readingComplete must be observed true after the last reader passes",
                Boolean.TRUE.equals(room.readingComplete.getValue()));
    }

    @Test
    public void readingCompleteUpdateFromAPreviousRoundIsIgnored() throws InterruptedException {
        room.listenToReadingComplete(roomId);

        Map<String, Object> staleUpdate = new HashMap<>();
        staleUpdate.put("readingComplete", true);
        staleUpdate.put("readingCompleteRoundId", "round-0");
        CountDownLatch staleWritten = new CountDownLatch(1);
        db.child("rooms").child(roomId).updateChildren(staleUpdate).addOnCompleteListener(t -> staleWritten.countDown());
        assertTrue("stale write timed out", awaitLatch(staleWritten));

        Thread.sleep(500);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse("a stale round's readingComplete=true must not be observed for the current round",
                Boolean.TRUE.equals(room.readingComplete.getValue()));
    }

    private void writeCurrentRoundId(String roundId) throws InterruptedException {
        CountDownLatch written = new CountDownLatch(1);
        db.child("rooms").child(roomId).child("currentRoundId").setValue(roundId)
                .addOnCompleteListener(t -> written.countDown());
        assertTrue("currentRoundId write timed out", awaitLatch(written));
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
