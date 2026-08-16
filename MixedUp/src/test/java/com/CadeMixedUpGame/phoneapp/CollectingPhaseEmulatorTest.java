package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ApplicationProvider;

import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;
import com.CadeMixedUpGame.api.GameFlowPolicy;
import com.CadeMixedUpGame.api.models.User;
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
 * Tier A coverage for the collecting-phase auto-advance gate: GameFlowPolicy.allPlayersFinishedIfs
 * already had pure-function coverage, but not against a real Firebase-loaded player list - this
 * covers the connected-gate (a disconnected "finished" player still blocks advancement) and a
 * late-joining player being correctly picked up by an already-attached players listener and
 * blocking advancement until they finish too. Same harness/pattern as MultiplayerEmulatorTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CollectingPhaseEmulatorTest {
    private static final int WAIT_SECONDS = 10;

    private FirebaseApp app;
    private DatabaseReference db;
    private UserViewModel observer;
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

        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "collect-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(app, "localhost", true);
        db = FirebaseDatabase.getInstance(app).getReference();
        observer = new UserViewModel(db, null, false);
        roomId = "collect-room-" + System.nanoTime();
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
    public void advanceGateOnlyOpensOnceEveryConnectedPlayerHasFinishedIfs() throws InterruptedException {
        UserViewModel playerA = joinPlayer("PlayerA", 1, true);
        UserViewModel playerB = joinPlayer("PlayerB", 2, false);
        observer.loadUsers(roomId);
        waitUntil(() -> observer.getUsers().size() >= 2);
        assertFalse("no one has finished yet", GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));

        submitIf(playerA);
        waitUntil(() -> GameFlowPolicy.countFinishedIfs(observer.getUsers()) == 1);
        assertFalse("only one of two players has finished", GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));

        submitIf(playerB);
        waitUntil(() -> GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));
        assertTrue("gate must open once every player has finished",
                GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));
    }

    @Test
    public void aDisconnectedPlayerWhoAlreadyFinishedNoLongerBlocksAutoAdvance() throws InterruptedException {
        // Deliberate behaviour change. This used to assert the opposite: that going offline pulled
        // the round back to "not ready" even for someone who had already submitted. That rule made
        // a locked phone freeze the collecting screen for everyone, and it could never resolve -
        // an offline player cannot un-disconnect by submitting again, because they already had.
        // The gate is now about missing work, not about connection state; a player who has not
        // written still holds the round indefinitely, and only a host kick removes them.
        UserViewModel playerA = joinPlayer("PlayerA", 1, true);
        UserViewModel playerB = joinPlayer("PlayerB", 2, false);
        observer.loadUsers(roomId);
        waitUntil(() -> observer.getUsers().size() >= 2);

        submitIf(playerA);
        submitIf(playerB);
        waitUntil(() -> GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));
        assertTrue(GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));

        markDisconnected(playerB.getUser().getValue());
        Thread.sleep(1000);
        assertTrue("dropping out after submitting must not un-ready the round",
                GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));
    }

    @Test
    public void aLateJoiningPlayerIsIncludedAndBlocksAdvanceUntilTheyFinishToo() throws InterruptedException {
        UserViewModel playerA = joinPlayer("PlayerA", 1, true);
        UserViewModel playerB = joinPlayer("PlayerB", 2, false);
        observer.loadUsers(roomId);
        waitUntil(() -> observer.getUsers().size() >= 2);

        submitIf(playerA);
        submitIf(playerB);
        waitUntil(() -> GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));
        assertTrue(GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));

        UserViewModel playerC = joinPlayer("PlayerC", 3, false);
        waitUntil(() -> observer.getUsers().size() >= 3);
        assertFalse("a late joiner who hasn't finished yet must reopen the gate as blocked",
                GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));

        submitIf(playerC);
        waitUntil(() -> GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));
        assertTrue("gate must open again once the late joiner also finishes",
                GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));
    }

    private void submitIf(UserViewModel player) throws InterruptedException {
        player.getUser().getValue().ifSentence = "a test if sentence";
        player.getUser().getValue().ifFinished = true;
        CountDownLatch pushed = new CountDownLatch(1);
        player.pushIf(player.getUser(), pushed::countDown);
        assertTrue("pushIf timed out", awaitLatch(pushed));
    }

    private void markDisconnected(User user) throws InterruptedException {
        CountDownLatch written = new CountDownLatch(1);
        db.child("rooms").child(roomId).child("players").child(user.userName + "-" + user.userID)
                .child("connected").setValue(false)
                .addOnCompleteListener(t -> written.countDown());
        assertTrue("disconnect write timed out", awaitLatch(written));
    }

    private UserViewModel joinPlayer(String name, int userID, boolean host) throws InterruptedException {
        UserViewModel joiner = new UserViewModel(db, null, false);
        MutableLiveData<User> userLive = joiner.buildUserFree(name);
        userLive.getValue().gameRoom = roomId;
        userLive.getValue().host = host;
        userLive.getValue().userID = userID;
        CountDownLatch joined = new CountDownLatch(1);
        joiner.pushPerson(userLive, joined::countDown);
        assertTrue("join timed out", awaitLatch(joined));
        return joiner;
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
