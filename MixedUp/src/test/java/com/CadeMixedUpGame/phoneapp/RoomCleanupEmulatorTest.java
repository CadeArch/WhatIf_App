package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ApplicationProvider;

import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.repositories.FirebaseGameRepository;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Covers two things Cade flagged as suspected gaps this session: (1) whether a room gets cleaned
 * up once its last non-host player leaves (confirmed absent via direct code reading - this is a
 * real product bug, not a false alarm), and (2) whether duplicate display names cause any
 * collision (confirmed intentional/fine - userID, assigned randomly by pushPerson, is what
 * actually disambiguates players internally, not the name).
 *
 * Same harness/pattern as MultiplayerEmulatorTest - see that class for setup rationale.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RoomCleanupEmulatorTest {
    private static final int WAIT_SECONDS = 10;

    private FirebaseApp aApp;
    private FirebaseApp bApp;
    private UserViewModel aUser;
    private UserViewModel bUser;
    private RoomViewModel sharedRoom;
    private DatabaseReference sharedDb;

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

        aApp = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "rca-" + System.nanoTime());
        bApp = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "rcb-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(aApp, "localhost", true);
        FirebaseEmulatorConfig.configureIfEnabled(bApp, "localhost", true);

        DatabaseReference aDb = FirebaseDatabase.getInstance(aApp).getReference();
        DatabaseReference bDb = FirebaseDatabase.getInstance(bApp).getReference();
        sharedDb = aDb;
        sharedRoom = new RoomViewModel(new FirebaseGameRepository(aDb));
        aUser = new UserViewModel(aDb, null, false);
        bUser = new UserViewModel(bDb, null, false);
    }

    @After
    public void tearDown() {
        if (aApp != null) {
            aApp.delete();
        }
        if (bApp != null) {
            bApp.delete();
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
    public void roomIsDeletedAfterTheLastNonHostPlayerLeaves() throws InterruptedException {
        String roomId = createRoom("cleanup-room-");
        joinRoom(aUser, roomId, "Guest A");
        joinRoom(bUser, roomId, "Guest B");
        assertTrue("room should exist right after both players join", roomExists(roomId));

        CountDownLatch aLeft = new CountDownLatch(1);
        aUser.removeCurrentPlayerFromRoom(aLeft::countDown);
        assertTrue("first player's leave timed out", awaitLatch(aLeft));
        assertTrue("room must still exist while a second player remains", roomExists(roomId));

        CountDownLatch bLeft = new CountDownLatch(1);
        bUser.removeCurrentPlayerFromRoom(bLeft::countDown);
        assertTrue("second player's leave timed out", awaitLatch(bLeft));

        waitUntil(() -> !roomExists(roomId));
        assertFalse("room must be deleted once its last player leaves", roomExists(roomId));
    }

    @Test
    public void duplicateDisplayNamesJoinAndAreTrackedAsDistinctPlayers() throws InterruptedException {
        String roomId = createRoom("dupe-name-room-");
        // Same display name on purpose - userID (random, assigned by pushPerson) is what
        // disambiguates players internally, not the name.
        joinRoom(aUser, roomId, "Same Name");
        joinRoom(bUser, roomId, "Same Name");

        int aId = aUser.getUser().getValue().userID;
        int bId = bUser.getUser().getValue().userID;
        assertTrue("both joins must have assigned a userID", aId != 0 && bId != 0);
        assertTrue("duplicate display names must still get distinct userIDs", aId != bId);

        AtomicBoolean checked = new AtomicBoolean(false);
        CountDownLatch read = new CountDownLatch(1);
        sharedDb.child("rooms").child(roomId).child("players").get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        checked.set(task.getResult().getChildrenCount() == 2);
                    }
                    read.countDown();
                });
        assertTrue("players read timed out", awaitLatch(read));
        assertTrue("both same-named players must be tracked as separate room entries", checked.get());
    }

    private String createRoom(String prefix) throws InterruptedException {
        String roomId = prefix + System.nanoTime();
        CountDownLatch roomCreated = new CountDownLatch(1);
        sharedRoom.pushRoom(roomId, roomCreated::countDown);
        assertTrue("room creation timed out", awaitLatch(roomCreated));
        return roomId;
    }

    private boolean roomExists(String roomId) {
        AtomicBoolean exists = new AtomicBoolean(true);
        try {
            CountDownLatch latch = new CountDownLatch(1);
            sharedDb.child("rooms").child(roomId).get()
                    .addOnCompleteListener(task -> {
                        DataSnapshot snapshot = task.isSuccessful() ? task.getResult() : null;
                        exists.set(snapshot != null && snapshot.exists());
                        latch.countDown();
                    });
            awaitLatch(latch);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return exists.get();
    }

    private void joinRoom(UserViewModel userViewModel, String roomId, String name) throws InterruptedException {
        userViewModel.myRoom = roomId;
        MutableLiveData<User> userLive = userViewModel.buildUserFree(name);
        userLive.getValue().gameRoom = roomId;
        userLive.getValue().host = false;
        CountDownLatch joined = new CountDownLatch(1);
        userViewModel.pushPerson(userLive, joined::countDown);
        assertTrue("join timed out", awaitLatch(joined));
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
