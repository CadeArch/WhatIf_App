package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.databinding.ObservableArrayList;
import androidx.test.core.app.ApplicationProvider;

import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;
import com.CadeMixedUpGame.api.GameLogic;
import com.CadeMixedUpGame.api.models.RoundAssignment;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.repositories.FirebaseGameRepository;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tier A coverage for RoomViewModel.createRoundAssignments: the full Firebase write/read-back
 * round trip (only the pure GameLogic.randomizedAssignment function had coverage before this
 * session) - no-self-assignment through the real write path, buildHostFirstReadOrder putting the
 * host first (previously zero coverage, pure or otherwise, despite being private/only reachable
 * through this method), readOrderIndex matching each player's actual position, and the
 * fewer-than-2-players guard no-op. Same harness/pattern as MultiplayerEmulatorTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RoundAssignmentEmulatorTest {
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

        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "ra-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(app, "localhost", true);
        db = FirebaseDatabase.getInstance(app).getReference();
        room = new RoomViewModel(new FirebaseGameRepository(db));

        roomId = "assign-room-" + System.nanoTime();
        CountDownLatch created = new CountDownLatch(1);
        room.pushRoom(roomId, created::countDown);
        assertTrue("room creation timed out", awaitLatch(created));
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
    public void assignmentsRoundTripWithNoSelfAssignmentsHostFirstOrderAndMatchingIndexes() throws InterruptedException {
        User host = user("Host", 1, true);
        User guestA = user("GuestA", 2, false);
        User guestB = user("GuestB", 3, false);
        ObservableArrayList<User> users = new ObservableArrayList<>();
        users.add(host);
        users.add(guestA);
        users.add(guestB);

        CountDownLatch created = new CountDownLatch(1);
        room.createRoundAssignments(roomId, users, created::countDown);
        assertTrue("createRoundAssignments timed out", awaitLatch(created));

        assertTrue("currentRoundId must be set locally after a successful write",
                room.currentRoundId.getValue() != null && room.currentRoundId.getValue().length() > 0);
        assertEquals(Integer.valueOf(0), room.activeReaderIndex.getValue());

        List<String> readOrder = room.readOrder.getValue();
        assertEquals("all 3 players must appear exactly once in the read order", 3, readOrder.size());
        assertEquals("buildHostFirstReadOrder must put the host first",
                GameLogic.playerKey(host), readOrder.get(0));

        Map<String, RoundAssignment> assignments = readAssignmentsFromFirebase();
        assertEquals(3, assignments.size());
        for (Map.Entry<String, RoundAssignment> entry : assignments.entrySet()) {
            String playerKey = entry.getKey();
            RoundAssignment assignment = entry.getValue();
            assertNotEquals("a player must never be assigned their own If", playerKey, assignment.ifOwnerKey);
            assertNotEquals("a player must never be assigned their own Then", playerKey, assignment.thenOwnerKey);
            assertEquals("readOrderIndex must match this player's actual position in readOrder",
                    readOrder.indexOf(playerKey), assignment.readOrderIndex);
        }
    }

    @Test
    public void fewerThanTwoPlayersLeavesRoomUntouched() throws InterruptedException {
        User onlyPlayer = user("Solo", 1, true);
        ObservableArrayList<User> users = new ObservableArrayList<>();
        users.add(onlyPlayer);

        // No onSuccess callback ever fires for the guard branch (it returns before any Firebase
        // call), so just give it every chance to wrongly write something, then assert nothing did.
        room.createRoundAssignments(roomId, users, () -> {
            throw new AssertionError("onSuccess must not fire when there are fewer than 2 players");
        });
        Thread.sleep(300);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertTrue("currentRoundId must remain unset locally",
                room.currentRoundId.getValue() == null || room.currentRoundId.getValue().isEmpty());

        CountDownLatch read = new CountDownLatch(1);
        AtomicReference<Boolean> hasAssignments = new AtomicReference<>(true);
        db.child("rooms").child(roomId).child("roundAssignments").get()
                .addOnCompleteListener(t -> {
                    hasAssignments.set(t.isSuccessful() && t.getResult() != null && t.getResult().exists());
                    read.countDown();
                });
        assertTrue("read timed out", awaitLatch(read));
        assertTrue("no roundAssignments should ever be written to Firebase for a single player", !hasAssignments.get());
    }

    private Map<String, RoundAssignment> readAssignmentsFromFirebase() throws InterruptedException {
        CountDownLatch read = new CountDownLatch(1);
        AtomicReference<Map<String, RoundAssignment>> result = new AtomicReference<>();
        db.child("rooms").child(roomId).child("roundAssignments").get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DataSnapshot snapshot = task.getResult();
                        GenericTypeIndicator<Map<String, RoundAssignment>> type =
                                new GenericTypeIndicator<Map<String, RoundAssignment>>() {};
                        result.set(snapshot.getValue(type));
                    }
                    read.countDown();
                });
        assertTrue("assignments read timed out", awaitLatch(read));
        return result.get();
    }

    private User user(String name, int userID, boolean host) {
        User user = new User(name);
        user.userID = userID;
        user.host = host;
        user.gameRoom = roomId;
        return user;
    }

    private boolean awaitLatch(CountDownLatch latch) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(50);
        }
        return latch.getCount() == 0;
    }
}
