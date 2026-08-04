package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
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
 * EndFrag.onViewCreated computes "were all players account players" with its own local loop
 * (counter == userViewModel.getUsers().size()) independently of GameFlowPolicy.allPlayersHaveAccounts
 * - this checks the two stay consistent for real, Firebase-round-tripped player lists (confirming
 * the accountPlay boolean itself survives serialization correctly, which matters given this
 * session's User.java encapsulation cleanup), not just as parallel in-memory implementations.
 *
 * The two are NOT actually equivalent for an empty player list: EndFrag's loop is vacuously true
 * (0 == 0), while GameFlowPolicy.allPlayersHaveAccounts explicitly returns false for an empty
 * list. Documented rather than "fixed" - EndFrag is only ever reached with >=2 players already in
 * the room (join requires an existing room; solo play isn't allowed per this app's own rules), so
 * the empty-list case is unreachable in practice, and reworking EndFrag's inline loop into a
 * shared helper is out of scope for a test-coverage pass.
 *
 * Same harness/pattern as MultiplayerEmulatorTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AccountPlayBranchingEmulatorTest {
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

        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "acct-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(app, "localhost", true);
        db = FirebaseDatabase.getInstance(app).getReference();
        observer = new UserViewModel(db, null, false);
        roomId = "acct-room-" + System.nanoTime();
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
    public void bothComputationsAgreeWhenEveryPlayerHasAnAccount() throws InterruptedException {
        joinPlayer("AcctHost", 1, true, true);
        joinPlayer("AcctGuest", 2, false, true);
        observer.loadUsers(roomId);
        waitUntil(() -> observer.getUsers().size() >= 2);

        assertTrue(GameFlowPolicy.allPlayersHaveAccounts(observer.getUsers()));
        assertTrue("EndFrag's local computation must agree with GameFlowPolicy",
                endFragStyleAllAccountPlayers(observer.getUsers()));
    }

    @Test
    public void bothComputationsAgreeForAMixedFreePlayAndAccountRoom() throws InterruptedException {
        joinPlayer("AcctHost", 1, true, true);
        joinPlayer("FreeGuest", 2, false, false);
        observer.loadUsers(roomId);
        waitUntil(() -> observer.getUsers().size() >= 2);

        assertFalse(GameFlowPolicy.allPlayersHaveAccounts(observer.getUsers()));
        assertFalse("EndFrag's local computation must agree with GameFlowPolicy",
                endFragStyleAllAccountPlayers(observer.getUsers()));
    }

    /** Mirrors EndFrag.onViewCreated's inline loop exactly - see this class's javadoc for why it
     * is deliberately NOT the same as GameFlowPolicy.allPlayersHaveAccounts for an empty list. */
    private boolean endFragStyleAllAccountPlayers(Iterable<User> users) {
        int counter = 0;
        int total = 0;
        for (User user : users) {
            total++;
            if (user.accountPlay) {
                counter += 1;
            }
        }
        return counter == total;
    }

    private void joinPlayer(String name, int userID, boolean host, boolean accountPlay) throws InterruptedException {
        UserViewModel joiner = new UserViewModel(db, null, false);
        MutableLiveData<User> userLive = joiner.buildUserFree(name);
        userLive.getValue().gameRoom = roomId;
        userLive.getValue().host = host;
        userLive.getValue().userID = userID;
        userLive.getValue().accountPlay = accountPlay;
        CountDownLatch joined = new CountDownLatch(1);
        joiner.pushPerson(userLive, joined::countDown);
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
