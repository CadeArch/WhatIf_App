package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ApplicationProvider;

import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;
import com.CadeMixedUpGame.api.GameLogic;
import com.CadeMixedUpGame.api.models.User;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Characterization tests for player presence and the host heartbeat, written before that code was
 * extracted out of {@code UserViewModel} so the move could be proven not to change behavior.
 *
 * <p>This is the hard-won disconnect behavior, so the tests deliberately drive a **real** socket
 * drop rather than simulating the write: a second FirebaseApp acts as another device in the room
 * and observes what the server does when the first one's connection goes away. (An earlier test in
 * this repo, {@code HostLeaveEmulatorTest}, notes that a real onDisconnect trigger "needs an actual
 * socket drop, not reproducible" and writes the value by hand instead — calling
 * {@code goOffline()} on the presence app's own database instance turns out to be exactly that
 * socket drop, so the registration and the server-side firing can both be covered for real.)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RoomPresenceEmulatorTest {
    private static final int WAIT_SECONDS = 15;

    private FirebaseApp presenceApp;
    private FirebaseApp observerApp;
    private FirebaseDatabase presenceDatabase;
    private DatabaseReference presenceDb;
    private DatabaseReference observerDb;
    private UserViewModel presence;
    private String roomId;

    @Before
    public void setUp() throws InterruptedException {
        assumeTrue("Firebase Emulator Suite not running on 127.0.0.1:9000 - start it with "
                        + "`firebase emulators:start --only database,auth` to run this test",
                emulatorReachable());

        presenceApp = newApp("presence-");
        observerApp = newApp("observer-");
        // One getReference() per FirebaseApp, reused - a second call yields a reference whose
        // writes never deliver their completion callback in this harness.
        presenceDatabase = FirebaseDatabase.getInstance(presenceApp);
        presenceDb = presenceDatabase.getReference();
        observerDb = FirebaseDatabase.getInstance(observerApp).getReference();
        presence = new UserViewModel(presenceDb, null, false);
        roomId = "presence-room-" + System.nanoTime();
        awaitWrite(observerDb.child("rooms").child(roomId).child("roomID"), roomId);
    }

    @After
    public void tearDown() {
        if (presenceApp != null) {
            presenceApp.delete();
        }
        if (observerApp != null) {
            observerApp.delete();
        }
    }

    private FirebaseApp newApp(String prefix) {
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setApiKey("fake-api-key")
                .setApplicationId("1:0:android:0")
                .setProjectId("demo-mixedupgame")
                .setDatabaseUrl("https://demo-mixedupgame-default-rtdb.firebaseio.com")
                .build();
        FirebaseApp app = FirebaseApp.initializeApp(
                ApplicationProvider.getApplicationContext(), options, prefix + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(app, "localhost", true);
        return app;
    }

    @Test
    public void joiningARoomPublishesThePlayerAsConnected() throws InterruptedException {
        MutableLiveData<User> user = joinRoom("Presence", false);

        waitUntil(() -> Boolean.TRUE.equals(readBoolean(playerRef(user).child("connected"))));
        assertTrue("a player who has just joined is connected",
                Boolean.TRUE.equals(readBoolean(playerRef(user).child("connected"))));
    }

    @Test
    public void losingTheConnectionMarksThePlayerDisconnectedForEveryoneElse() throws InterruptedException {
        MutableLiveData<User> user = joinRoom("Dropper", false);
        waitUntil(() -> Boolean.TRUE.equals(readBoolean(playerRef(user).child("connected"))));

        // A real socket drop, not a simulated write - this is what makes the onDisconnect
        // registration in pushPerson meaningful, and it is the behavior the whole host-disconnect
        // grace flow is built on.
        presenceDatabase.goOffline();

        waitUntil(() -> Boolean.FALSE.equals(readBoolean(playerRef(user).child("connected"))));
        assertFalse("the server must mark a dropped player disconnected on its own",
                Boolean.TRUE.equals(readBoolean(playerRef(user).child("connected"))));
        assertTrue("and stamp when it happened, which the grace timer counts from",
                readLong(playerRef(user).child("disconnectedAt")) > 0L);
    }

    @Test
    public void aDroppedHostAlsoMarksTheRoomsHostConnectionDisconnected() throws InterruptedException {
        MutableLiveData<User> user = joinRoom("HostDropper", true);
        waitUntil(() -> Boolean.TRUE.equals(readBoolean(playerRef(user).child("connected"))));

        presenceDatabase.goOffline();

        waitUntil(() -> Boolean.FALSE.equals(readBoolean(hostConnectionRef().child("connected"))));
        assertFalse("guests watch rooms/<id>/hostConnection to notice the host vanished",
                Boolean.TRUE.equals(readBoolean(hostConnectionRef().child("connected"))));
    }

    @Test
    public void hostHeartbeatRecordsThatTheHostIsStillThere() throws InterruptedException {
        MutableLiveData<User> user = joinRoom("Beater", true);
        waitUntil(() -> Boolean.TRUE.equals(readBoolean(playerRef(user).child("connected"))));

        presence.writeHostHeartbeat();

        waitUntil(() -> readLong(hostConnectionRef().child("lastSeenAt")) > 0L);
        assertTrue("lastSeenAt is what the abandoned-room sweep and the guests' expiry timer read",
                readLong(hostConnectionRef().child("lastSeenAt")) > 0L);
        assertTrue(Boolean.TRUE.equals(readBoolean(hostConnectionRef().child("connected"))));
    }

    @Test
    public void hostHeartbeatIsRefusedOnceTheRoomHasBeenDeclaredExpired() throws InterruptedException {
        MutableLiveData<User> user = joinRoom("LateBeater", true);
        waitUntil(() -> Boolean.TRUE.equals(readBoolean(playerRef(user).child("connected"))));
        awaitRemoval(hostConnectionRef().child("lastSeenAt"));

        // The room died while this host was away; a host that reconnects afterwards must not be
        // able to resurrect it by heartbeating into it.
        awaitWrite(observerDb.child("expiredRooms").child(roomId).child("expiredAt"), System.currentTimeMillis());

        presence.writeHostHeartbeat();

        Thread.sleep(1200L);
        assertEquals("a tombstoned room must not accept a heartbeat",
                -1L, readLong(hostConnectionRef().child("lastSeenAt")));
    }

    @Test
    public void hostHeartbeatIsRefusedWhenTheRoomIsAlreadyGone() throws InterruptedException {
        MutableLiveData<User> user = joinRoom("GhostBeater", true);
        waitUntil(() -> Boolean.TRUE.equals(readBoolean(playerRef(user).child("connected"))));
        awaitRemoval(observerDb.child("rooms").child(roomId));

        presence.writeHostHeartbeat();

        Thread.sleep(1200L);
        assertFalse("a deleted room must not be recreated by a stray heartbeat",
                exists(observerDb.child("rooms").child(roomId).child("hostConnection")));
    }

    @Test
    public void reconnectingMarksThePlayerConnectedAgain() throws InterruptedException {
        MutableLiveData<User> user = joinRoom("Returner", false);
        waitUntil(() -> Boolean.TRUE.equals(readBoolean(playerRef(user).child("connected"))));
        awaitWrite(playerRef(user).child("connected"), false);

        presence.markCurrentPlayerConnected();

        waitUntil(() -> Boolean.TRUE.equals(readBoolean(playerRef(user).child("connected"))));
        assertTrue("coming back online clears the disconnected state",
                Boolean.TRUE.equals(readBoolean(playerRef(user).child("connected"))));
    }

    private MutableLiveData<User> joinRoom(String name, boolean host) throws InterruptedException {
        MutableLiveData<User> user = presence.buildUserFree(name);
        user.getValue().gameRoom = roomId;
        user.getValue().host = host;
        user.getValue().userID = 4242;
        presence.myRoom = roomId;
        CountDownLatch joined = new CountDownLatch(1);
        presence.pushPerson(user, joined::countDown);
        assertTrue("joining the room timed out", awaitLatch(joined));
        return user;
    }

    /** Read through the observer app so assertions reflect what the server actually holds, not the
     * presence app's local cache - which still answers from memory after it goes offline. */
    private DatabaseReference playerRef(MutableLiveData<User> user) {
        return observerDb.child("rooms").child(roomId).child("players").child(GameLogic.playerKey(user.getValue()));
    }

    private DatabaseReference hostConnectionRef() {
        return observerDb.child("rooms").child(roomId).child("hostConnection");
    }

    private Boolean readBoolean(DatabaseReference ref) {
        DataSnapshot snapshot = read(ref);
        return snapshot == null ? null : snapshot.getValue(Boolean.class);
    }

    private long readLong(DatabaseReference ref) {
        DataSnapshot snapshot = read(ref);
        Long value = snapshot == null ? null : snapshot.getValue(Long.class);
        return value == null ? -1L : value;
    }

    private boolean exists(DatabaseReference ref) {
        DataSnapshot snapshot = read(ref);
        return snapshot != null && snapshot.exists();
    }

    private DataSnapshot read(DatabaseReference ref) {
        AtomicReference<DataSnapshot> result = new AtomicReference<DataSnapshot>();
        AtomicReference<Boolean> done = new AtomicReference<Boolean>();
        ref.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                result.set(task.getResult());
            }
            done.set(true);
        });
        try {
            waitUntil(() -> done.get() != null);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return result.get();
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
