package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.test.core.app.ApplicationProvider;

import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;
import com.CadeMixedUpGame.api.repositories.FirebaseGameRepository;
import com.CadeMixedUpGame.api.repositories.GameRepository;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.ChildEventListener;
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

/**
 * Covers all four RoomViewModel.RoomJoinState outcomes from checkRoomCanJoin() against the real
 * emulator, plus the ERROR/cancelled-listener branch via a fake GameRepository pointed at a
 * top-level path database.rules.json does NOT explicitly allow (it default-denies everything
 * except the app's real top-level roots: rooms/leaderBoard/AccountPlayers/expiredRooms/etc - a
 * nested ".read": false under an already-allowed ancestor does NOT work for this, since Firebase
 * RTDB rules cascade as "OR": once an ancestor grants read, no descendant rule can revoke it).
 * Same harness/pattern as MultiplayerEmulatorTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RoomJoinValidationEmulatorTest {
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

        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "rjv-" + System.nanoTime());
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
    public void emptyRoomIdIsImmediatelyDoesNotExist() throws InterruptedException {
        room.checkRoomCanJoin("");
        waitUntil(() -> room.roomJoinState.getValue() != RoomViewModel.RoomJoinState.IDLE);
        assertEquals(RoomViewModel.RoomJoinState.DOES_NOT_EXIST, room.roomJoinState.getValue());
    }

    @Test
    public void neverCreatedRoomIsDoesNotExist() throws InterruptedException {
        room.checkRoomCanJoin("never-created-room-" + System.nanoTime());
        waitUntil(() -> room.roomJoinState.getValue() != RoomViewModel.RoomJoinState.IDLE);
        assertEquals(RoomViewModel.RoomJoinState.DOES_NOT_EXIST, room.roomJoinState.getValue());
    }

    @Test
    public void freshlyCreatedRoomIsAvailable() throws InterruptedException {
        String roomId = createRoom();
        room.checkRoomCanJoin(roomId);
        waitUntil(() -> room.roomJoinState.getValue() != RoomViewModel.RoomJoinState.IDLE);
        assertEquals(RoomViewModel.RoomJoinState.AVAILABLE, room.roomJoinState.getValue());
    }

    @Test
    public void roomWithGameInProgressBlocksJoining() throws InterruptedException {
        String roomId = createRoom();
        CountDownLatch started = new CountDownLatch(1);
        room.gameInProgressTrue(roomId, started::countDown);
        assertTrue("gameInProgressTrue write timed out", awaitLatch(started));

        room.checkRoomCanJoin(roomId);
        waitUntil(() -> room.roomJoinState.getValue() != RoomViewModel.RoomJoinState.IDLE);
        assertEquals(RoomViewModel.RoomJoinState.IN_PROGRESS, room.roomJoinState.getValue());
    }

    @Test
    public void cancelledLookupYieldsErrorState() throws InterruptedException {
        DatabaseReference lockedRoot = db.child("notAnAllowedTopLevelPath");
        RoomViewModel lockedRoom = new RoomViewModel(new LockedRootRepository(lockedRoot));
        lockedRoom.checkRoomCanJoin("any-room");
        waitUntil(() -> lockedRoom.roomJoinState.getValue() != RoomViewModel.RoomJoinState.IDLE);
        assertEquals(RoomViewModel.RoomJoinState.ERROR, lockedRoom.roomJoinState.getValue());
    }

    /** Only root() needs to work - RoomViewModel's constructor is the only caller of any
     * GameRepository method that checkRoomCanJoin's own code path depends on (it reads straight
     * off the db field afterward, not through the repository again). */
    private static class LockedRootRepository implements GameRepository {
        private final DatabaseReference lockedRoot;

        LockedRootRepository(DatabaseReference lockedRoot) {
            this.lockedRoot = lockedRoot;
        }

        @Override
        public DatabaseReference root() {
            return lockedRoot;
        }

        @Override
        public DatabaseReference room(String roomId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DatabaseReference players(String roomId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DatabaseReference player(String roomId, String playerKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Task<Void> setRoomInProgress(String roomId, boolean inProgress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void listenToPlayers(String roomId, ChildEventListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removePlayersListener(String roomId, ChildEventListener listener) {
            throw new UnsupportedOperationException();
        }
    }

    private String createRoom() throws InterruptedException {
        String roomId = "join-check-room-" + System.nanoTime();
        CountDownLatch created = new CountDownLatch(1);
        room.pushRoom(roomId, created::countDown);
        assertTrue("room creation timed out", awaitLatch(created));
        return roomId;
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
