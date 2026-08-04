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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Two host-leave paths, traced in full this session before writing these tests - they behave
 * differently and must not be conflated:
 *
 * Path A (explicit): host taps Home from EndFrag/WaitingForHostFrag -> writes
 * {@code replayState="no"} and deletes the room immediately, no grace timer. Remaining players
 * observe the replayState flip and navigate to StartFragment with a "host ended the game" message.
 *
 * Path B (disconnect/kill): the host's connection drops or the app is force-closed. MainActivity's
 * HostDisconnectScheduler (unit-tested separately in HostDisconnectSchedulerTest, so the ~20-24s
 * timing math itself is not re-tested here) eventually decides the room is expired; this test
 * covers the Firebase *data* side that decision acts on - hostConnection observation,
 * expiredRooms tombstone read/write, room deletion - by simulating the host's onDisconnect()
 * write directly (a real onDisconnect() trigger needs an actual socket drop, not reproducible
 * from a JVM test) and driving the same RoomViewModel/UserViewModel calls MainActivity would.
 * Remaining players end up at the landing FirstFrag with a different message than Path A.
 *
 * Same harness/pattern as MultiplayerEmulatorTest - see that class for setup rationale.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HostLeaveEmulatorTest {
    private static final int WAIT_SECONDS = 10;

    private FirebaseApp hostApp;
    private FirebaseApp guestApp;
    private RoomViewModel hostRoom;
    private RoomViewModel guestRoom;
    private UserViewModel hostUser;
    private UserViewModel guestUser;
    private DatabaseReference hostDb;

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

        hostApp = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "hlhost-" + System.nanoTime());
        guestApp = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "hlguest-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(hostApp, "localhost", true);
        FirebaseEmulatorConfig.configureIfEnabled(guestApp, "localhost", true);

        hostDb = FirebaseDatabase.getInstance(hostApp).getReference();
        DatabaseReference guestDb = FirebaseDatabase.getInstance(guestApp).getReference();
        hostRoom = new RoomViewModel(new FirebaseGameRepository(hostDb));
        guestRoom = new RoomViewModel(new FirebaseGameRepository(guestDb));
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

    @Test
    public void explicitHostLeaveWritesReplayStateNoAndGuestObservesIt() throws InterruptedException {
        String roomId = createRoom();
        joinRoom(hostUser, roomId, true);
        joinRoom(guestUser, roomId, false);

        // Guest starts listening before the host writes, as WaitingForHostFrag/EndFrag do.
        guestRoom.listenToReplayState(roomId);

        // Mirrors EndFrag's host-leaves-from-End handler: replayState="no" then delete the room.
        CountDownLatch replayWritten = new CountDownLatch(1);
        hostRoom.setReplayState(roomId, "no", replayWritten::countDown);
        assertTrue("replayState write timed out", awaitLatch(replayWritten));

        waitUntil(() -> "no".equals(guestRoom.replayState.getValue()));
        assertEquals("guest must observe the explicit host-leave signal", "no", guestRoom.replayState.getValue());

        // Path A must not go through the disconnect/expired-room path at all.
        assertEquals("", guestRoom.expiredRoomMessage.getValue() == null ? "" : guestRoom.expiredRoomMessage.getValue());

        CountDownLatch roomDeleted = new CountDownLatch(1);
        hostRoom.deleteRoom(roomId, roomDeleted::countDown);
        assertTrue("room delete timed out", awaitLatch(roomDeleted));
    }

    @Test
    public void hostConnectionDropWritesTombstoneAndGuestObservesExpiry() throws InterruptedException {
        String roomId = createRoom();
        joinRoom(hostUser, roomId, true);
        joinRoom(guestUser, roomId, false);
        // pushPerson's success path already calls listenToHostConnection for a non-host joiner.

        // Simulate the host's onDisconnect()-registered write (a real onDisconnect() trigger
        // needs an actual dropped socket, not reproducible from a JVM test).
        long disconnectedAt = System.currentTimeMillis();
        Map<String, Object> update = new HashMap<>();
        update.put("connected", false);
        update.put("disconnectedAt", disconnectedAt);
        CountDownLatch hostConnectionWritten = new CountDownLatch(1);
        DatabaseReference hostConnectionRef = hostDb.child("rooms").child(roomId).child("hostConnection");
        hostConnectionRef.updateChildren(update).addOnCompleteListener(t -> hostConnectionWritten.countDown());
        assertTrue("hostConnection write timed out", awaitLatch(hostConnectionWritten));

        waitUntil(() -> guestUser.hostDisconnectedAt.getValue() != null && guestUser.hostDisconnectedAt.getValue() > 0L);
        assertEquals("guest's UserViewModel must observe the host connection drop via its own listener",
                Long.valueOf(disconnectedAt), guestUser.hostDisconnectedAt.getValue());

        // What MainActivity does once HostDisconnectScheduler (tested separately) decides to
        // expire: write the tombstone, then delete the room.
        guestRoom.listenToExpiredRoom(roomId);
        String message = "Sorry! Host disconnected - create a new game!";
        CountDownLatch expiredWritten = new CountDownLatch(1);
        guestRoom.markRoomExpired(roomId, message, expiredWritten::countDown);
        assertTrue("markRoomExpired timed out", awaitLatch(expiredWritten));

        waitUntil(() -> message.equals(guestRoom.expiredRoomMessage.getValue()));
        assertEquals("guest must observe the expired-room tombstone via listenToExpiredRoom",
                message, guestRoom.expiredRoomMessage.getValue());

        CountDownLatch roomDeleted = new CountDownLatch(1);
        guestRoom.deleteRoom(roomId, roomDeleted::countDown);
        assertTrue("room delete timed out", awaitLatch(roomDeleted));
    }

    private String createRoom() throws InterruptedException {
        String roomId = "hl-room-" + System.nanoTime();
        CountDownLatch roomCreated = new CountDownLatch(1);
        hostRoom.pushRoom(roomId, roomCreated::countDown);
        assertTrue("room creation timed out", awaitLatch(roomCreated));
        return roomId;
    }

    private void joinRoom(UserViewModel userViewModel, String roomId, boolean host) throws InterruptedException {
        MutableLiveData<User> userLive = userViewModel.buildUserFree(host ? "Host" : "Guest");
        userLive.getValue().gameRoom = roomId;
        userLive.getValue().host = host;
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
