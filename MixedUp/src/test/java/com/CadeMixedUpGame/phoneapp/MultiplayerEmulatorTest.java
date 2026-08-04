package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
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
 * "Tier A" multiplayer harness (see CHANGELOG.md design notes for this branch): two simulated
 * players, each with their own RoomViewModel/UserViewModel pair against separate named
 * FirebaseApp instances, both pointed at the local Firebase Emulator Suite. Runs as a plain JVM
 * test via Robolectric — real multi-client Firebase behavior under test, no emulator/device UI.
 *
 * Requires `firebase emulators:start --only database,auth` running locally first (see README's
 * "Local Firebase Emulator" section). Skips (does not fail) if the emulator isn't reachable, so
 * this doesn't break a normal `./gradlew test` run for anyone who hasn't started it.
 *
 * Firebase's Task/ChildEventListener callbacks post back to Robolectric's shadow main looper,
 * which (in Robolectric's default PAUSED mode) never runs queued work on its own - waits below
 * drive it explicitly via {@code shadowOf(Looper.getMainLooper()).idle()} instead of a plain
 * blocking {@code CountDownLatch.await()}, which would otherwise deadlock forever.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34) // Robolectric doesn't support simulating this project's compileSdk/targetSdk (36) yet.
public class MultiplayerEmulatorTest {
    private static final int WAIT_SECONDS = 10;

    private FirebaseApp hostApp;
    private FirebaseApp guestApp;
    private RoomViewModel hostRoom;
    private UserViewModel hostUser;
    private UserViewModel guestUser;

    @Before
    public void setUp() {
        assumeTrue("Firebase Emulator Suite not running on 127.0.0.1:9000 - start it with "
                        + "`firebase emulators:start --only database,auth` to run this test",
                emulatorReachable());

        // Fake-but-well-formed values: only the local "demo-" emulator is ever contacted, which
        // does not validate these against a real project.
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setApiKey("fake-api-key")
                .setApplicationId("1:0:android:0")
                .setProjectId("demo-mixedupgame")
                .setDatabaseUrl("https://demo-mixedupgame-default-rtdb.firebaseio.com")
                .build();

        hostApp = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "host-" + System.nanoTime());
        guestApp = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "guest-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(hostApp, "localhost", true);
        FirebaseEmulatorConfig.configureIfEnabled(guestApp, "localhost", true);

        // Call FirebaseDatabase.getInstance(app).getReference() exactly ONCE per app and share
        // that single DatabaseReference between RoomViewModel and UserViewModel. Calling
        // .getReference() a second time for the same app was isolated (via several throwaway
        // repro tests, down to raw DatabaseReference calls with no ViewModel/Auth involved at
        // all) as a genuine Robolectric-environment quirk: the second-obtained reference's writes
        // never deliver their completion callback, even though the first one works fine. Not
        // reproducible on a real device - this is purely about this JVM test harness.
        DatabaseReference hostDb = FirebaseDatabase.getInstance(hostApp).getReference();
        DatabaseReference guestDb = FirebaseDatabase.getInstance(guestApp).getReference();
        hostRoom = new RoomViewModel(new FirebaseGameRepository(hostDb));
        // auth=null is safe here: these are free-play users (buildUserFree/pushPerson/loadUsers/
        // removeCurrentPlayerFromRoom never touch UserViewModel's auth field).
        hostUser = new UserViewModel(hostDb, null, false);
        guestUser = new UserViewModel(guestDb, null, false);
    }

    @After
    public void tearDown() {
        if (hostApp != null) {
            hostApp.delete();
        }
        if (guestApp != null) {
            guestApp.delete();
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

    /**
     * Covers the "late joins" gap flagged in README's testing roadmap: a second player joining
     * after the host is already listening to the player list must show up in real time.
     */
    @Test
    public void guestJoiningAfterHostSeesEachOtherInPlayerList() throws InterruptedException {
        String roomId = createRoom();

        MutableLiveData<User> hostUserLive = hostUser.buildUserFree("HostPlayer");
        hostUserLive.getValue().gameRoom = roomId;
        hostUserLive.getValue().host = true;
        joinRoom(hostUser, hostUserLive);

        // Host starts listening to the player list, as the real waiting-for-host screen does,
        // BEFORE the guest joins - this is what makes it a "late join" scenario.
        hostUser.loadUsers(roomId);

        MutableLiveData<User> guestUserLive = guestUser.buildUserFree("GuestPlayer");
        guestUserLive.getValue().gameRoom = roomId;
        guestUserLive.getValue().host = false;
        joinRoom(guestUser, guestUserLive);

        waitUntil(() -> hostUser.getUsers().size() >= 2);
        assertEquals("host should see both players after guest's late join", 2, hostUser.getUsers().size());
    }

    /**
     * Covers the "player leaves" gap flagged in README's testing roadmap: removing a player's
     * node must be observed by the other client's real-time listener.
     */
    @Test
    public void guestLeavingIsObservedByHostListener() throws InterruptedException {
        String roomId = createRoom();

        MutableLiveData<User> hostUserLive = hostUser.buildUserFree("HostPlayer");
        hostUserLive.getValue().gameRoom = roomId;
        hostUserLive.getValue().host = true;
        joinRoom(hostUser, hostUserLive);
        hostUser.loadUsers(roomId);

        MutableLiveData<User> guestUserLive = guestUser.buildUserFree("GuestPlayer");
        guestUserLive.getValue().gameRoom = roomId;
        guestUserLive.getValue().host = false;
        joinRoom(guestUser, guestUserLive);
        waitUntil(() -> hostUser.getUsers().size() >= 2);

        CountDownLatch guestLeft = new CountDownLatch(1);
        guestUser.removeCurrentPlayerFromRoom(guestLeft::countDown);
        assertTrue("guest leave timed out", awaitLatch(guestLeft));

        waitUntil(() -> hostUser.getUsers().size() <= 1);
        assertEquals("host should see the guest removed after they leave", 1, hostUser.getUsers().size());
    }

    /**
     * Uses the plain (non-transactional) {@code pushRoom} instead of {@code createUniqueRoom}.
     * The collision-retry transaction logic that createUniqueRoom wraps is already covered
     * without Firebase at all by RoomCreationPolicyTest; a plain write is also enough to sidestep
     * a Robolectric/Firebase-SDK quirk where a write immediately following a runTransaction call
     * on the same connection never delivers its completion callback in this JVM test environment
     * (confirmed not to be a serialization or pushPerson-specific issue via isolated repro tests;
     * transactions and plain writes each work individually, just not back-to-back on one Repo).
     */
    private String createRoom() throws InterruptedException {
        String roomId = "test-room-" + System.nanoTime();
        CountDownLatch roomCreated = new CountDownLatch(1);
        hostRoom.pushRoom(roomId, roomCreated::countDown);
        assertTrue("room creation timed out", awaitLatch(roomCreated));
        return roomId;
    }

    private void joinRoom(UserViewModel userViewModel, MutableLiveData<User> userLive) throws InterruptedException {
        CountDownLatch joined = new CountDownLatch(1);
        userViewModel.pushPerson(userLive, joined::countDown);
        assertTrue("join timed out", awaitLatch(joined));
    }

    private interface Condition {
        boolean isMet();
    }

    /** Drives Robolectric's shadow main looper while waiting, since Firebase's async callbacks
     * are delivered through it and it does not advance on its own (see class javadoc). */
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
