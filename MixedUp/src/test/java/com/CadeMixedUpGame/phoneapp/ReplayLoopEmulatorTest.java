package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
 * Regression test for the reported "2 players play, then Play Again twice in a row causes
 * crashes / guest stuck on the wrong screen after the host leaves EndFrag" bug.
 *
 * Mirrors EndFrag.java's real method call sequence for the "Again" button exactly (both the host
 * and non-host branches), rather than testing clearLocalRoundState() in isolation the way
 * RoomViewModelTest already does - including the listener attach/detach timing
 * (removePlayersListenerOnDB on arrival, re-attach via loadUsers in finishPlayAgainNavigation)
 * since the leading hypothesis is a false "host disconnected" signal caused by that re-attach
 * racing the room-reset writes, not a pure local-state-reset bug.
 *
 * Same harness/pattern as MultiplayerEmulatorTest/HostLeaveEmulatorTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ReplayLoopEmulatorTest {
    private static final int WAIT_SECONDS = 10;

    private FirebaseApp hostApp;
    private FirebaseApp guestApp;
    private RoomViewModel hostRoom;
    private RoomViewModel guestRoom;
    private UserViewModel hostUser;
    private UserViewModel guestUser;
    private String roomId;

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

        hostApp = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "rlhost-" + System.nanoTime());
        guestApp = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "rlguest-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(hostApp, "localhost", true);
        FirebaseEmulatorConfig.configureIfEnabled(guestApp, "localhost", true);

        DatabaseReference hostDb = FirebaseDatabase.getInstance(hostApp).getReference();
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
    public void twoConsecutivePlayAgainCyclesDoNotFalselyDisconnectTheGuest() throws InterruptedException {
        roomId = createRoom();
        joinRoom(hostUser, true);
        joinRoom(guestUser, false);

        // Round 1: both "arrive at EndFrag" and the host clicks Again, guest follows once they
        // observe replayState=="yes" - exactly EndFrag.java's real sequence, twice in a row.
        playOneReplayCycle(1);
        assertNoFalseHostDisconnect("after round 1's replay cycle");

        playOneReplayCycle(2);
        assertNoFalseHostDisconnect("after round 2's replay cycle");

        // Sanity: after two full cycles, both players are still present under the same room.
        waitUntil(() -> hostUser.getUsers().size() >= 2);
        assertEquals("both players should still be in the room after 2 replay cycles",
                2, hostUser.getUsers().size());
    }

    /** Mirrors EndFrag.onViewCreated + the "Again" button handlers for both host and guest. */
    private void playOneReplayCycle(int roundNumber) throws InterruptedException {
        // EndFrag.onViewCreated: both clients detach their players listener and (re)attach the
        // replayState listener while sitting on EndFrag.
        hostUser.removePlayersListenerOnDB();
        guestUser.removePlayersListenerOnDB();
        hostUser.onEndFrag = true;
        guestUser.onEndFrag = true;
        hostRoom.listenToReplayState(roomId);
        guestRoom.listenToReplayState(roomId);

        // Host clicks "Again": clearLocalStateForReplayOrExit() then the host branch of
        // resetRoomForReplay() -> continuePlayAgain() -> finishPlayAgainNavigation().
        hostUser.reset();
        hostRoom.clearLocalRoundState();

        CountDownLatch roundStateCleared = new CountDownLatch(1);
        hostRoom.clearRoomRoundStateForReplay(roomId, roundStateCleared::countDown);
        assertTrue("round " + roundNumber + ": clearRoomRoundStateForReplay timed out", awaitLatch(roundStateCleared));

        CountDownLatch nurfed = new CountDownLatch(1);
        hostUser.nurfAllUsers(nurfed::countDown);
        assertTrue("round " + roundNumber + ": nurfAllUsers timed out", awaitLatch(nurfed));

        // Guest observes replayState=="yes" only after the host actually writes it below, but in
        // real usage the guest's Again button only becomes clickable at that point too - drive
        // the host's continuePlayAgain() first since that's what unblocks the guest.
        CountDownLatch hostPushed = new CountDownLatch(1);
        MutableLiveData<User> hostUserLive = hostUser.getUser();
        hostUser.pushPerson(hostUserLive, hostPushed::countDown);
        assertTrue("round " + roundNumber + ": host pushPerson timed out", awaitLatch(hostPushed));

        CountDownLatch replaySet = new CountDownLatch(1);
        hostRoom.setReplayState(roomId, "yes", replaySet::countDown);
        assertTrue("round " + roundNumber + ": setReplayState(yes) timed out", awaitLatch(replaySet));

        CountDownLatch gameInProgressFalse = new CountDownLatch(1);
        hostRoom.gameInProgressFalse(roomId, gameInProgressFalse::countDown);
        assertTrue("round " + roundNumber + ": gameInProgressFalse timed out", awaitLatch(gameInProgressFalse));

        // Host's finishPlayAgainNavigation(): stop listening for replay state, re-attach players.
        hostRoom.removeReplayStateListener();
        hostUser.loadUsers(roomId);

        // Guest now observes "yes" (mirrors the Again button becoming enabled) and clicks it:
        // same clearLocalStateForReplayOrExit() + non-host continuePlayAgain() branch.
        waitUntil(() -> "yes".equals(guestRoom.replayState.getValue()));
        assertEquals("round " + roundNumber + ": guest must observe this round's replayState=yes",
                "yes", guestRoom.replayState.getValue());

        guestUser.reset();
        guestRoom.clearLocalRoundState();

        CountDownLatch guestPushed = new CountDownLatch(1);
        guestUser.pushPerson(guestUser.getUser(), guestPushed::countDown);
        assertTrue("round " + roundNumber + ": guest pushPerson timed out", awaitLatch(guestPushed));

        // Guest's finishPlayAgainNavigation(): stop listening for replay state, re-attach players.
        guestRoom.removeReplayStateListener();
        guestUser.loadUsers(roomId);

        // Let both freshly re-attached player-list listeners settle before asserting anything.
        waitUntil(() -> hostUser.getUsers().size() >= 2 && guestUser.getUsers().size() >= 2);
    }

    /**
     * The clean-sequential test above passed on the first try because every step waits for the
     * previous one to fully complete - it never actually exercises the race. In real play the
     * host and guest are on independent devices: if the host reaches EndFrag and taps "Again"
     * before the guest has arrived at EndFrag themselves, the guest's players-list listener is
     * still the one attached from whatever screen they were previously on (EndFrag.onViewCreated
     * is what calls removePlayersListenerOnDB() - a guest who hasn't gotten there yet still has
     * it live). This reproduces exactly that interleaving: attach the guest's players listener
     * up front (as if from an earlier screen) and never detach it before the host runs its full
     * "Again" sequence, matching the reported "guest not moving onto the correct frag after the
     * host leaves the ending frag" symptom.
     */
    @Test
    public void hostReplayResetDoesNotFalselyDisconnectAGuestWhoseListenerIsStillAttached() throws InterruptedException {
        roomId = createRoom();
        joinRoom(hostUser, true);
        joinRoom(guestUser, false);

        // Guest is still on an earlier screen (e.g. VoteFrag) - its players listener from that
        // screen is still attached, unlike the "both already on EndFrag" cycle above.
        guestUser.loadUsers(roomId);
        waitUntil(() -> guestUser.getUsers().size() >= 2);

        // Host reaches EndFrag and taps "Again" - mirrors EndFrag.onViewCreated + the host branch
        // of continuePlayAgain()/finishPlayAgainNavigation(), exactly like playOneReplayCycle()
        // above, but without the guest ever detaching its own listener first.
        hostUser.removePlayersListenerOnDB();
        hostUser.onEndFrag = true;
        hostRoom.listenToReplayState(roomId);
        hostUser.reset();
        hostRoom.clearLocalRoundState();

        CountDownLatch roundStateCleared = new CountDownLatch(1);
        hostRoom.clearRoomRoundStateForReplay(roomId, roundStateCleared::countDown);
        assertTrue("clearRoomRoundStateForReplay timed out", awaitLatch(roundStateCleared));

        CountDownLatch nurfed = new CountDownLatch(1);
        hostUser.nurfAllUsers(nurfed::countDown);
        assertTrue("nurfAllUsers timed out", awaitLatch(nurfed));

        CountDownLatch hostPushed = new CountDownLatch(1);
        hostUser.pushPerson(hostUser.getUser(), hostPushed::countDown);
        assertTrue("host pushPerson timed out", awaitLatch(hostPushed));

        CountDownLatch replaySet = new CountDownLatch(1);
        hostRoom.setReplayState(roomId, "yes", replaySet::countDown);
        assertTrue("setReplayState(yes) timed out", awaitLatch(replaySet));

        CountDownLatch gameInProgressFalse = new CountDownLatch(1);
        hostRoom.gameInProgressFalse(roomId, gameInProgressFalse::countDown);
        assertTrue("gameInProgressFalse timed out", awaitLatch(gameInProgressFalse));

        hostRoom.removeReplayStateListener();
        hostUser.loadUsers(roomId);

        // Give the guest's still-attached listener every chance to (mis)fire before asserting.
        waitUntil(() -> hostUser.getUsers().size() >= 2);
        assertNoFalseHostDisconnect("while the guest's listener was still attached from an earlier screen");
    }

    private void assertNoFalseHostDisconnect(String when) {
        String message = guestUser.hostDisconnectedMessage.getValue();
        assertNull("guest must not receive a false host-disconnected message " + when
                        + " (got: \"" + message + "\") - this is the leading hypothesis for the "
                        + "reported bug: nurfAllUsers() deletes the host's own player node as "
                        + "part of resetting for replay, and if the guest's player-list listener "
                        + "is still attached when that delete lands, it can misread it as a real "
                        + "host departure",
                message == null || message.isEmpty() ? null : message);
    }

    private String createRoom() throws InterruptedException {
        String id = "replay-room-" + System.nanoTime();
        CountDownLatch roomCreated = new CountDownLatch(1);
        hostRoom.pushRoom(id, roomCreated::countDown);
        assertTrue("room creation timed out", awaitLatch(roomCreated));
        return id;
    }

    private void joinRoom(UserViewModel userViewModel, boolean host) throws InterruptedException {
        // myRoom is a public field Fragments set directly (StartFragment/JoinGameFrag), not
        // derived internally by UserViewModel - nurfAllUsers()/loadUsers() etc. depend on it.
        userViewModel.myRoom = roomId;
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
