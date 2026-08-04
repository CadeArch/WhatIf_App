package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ApplicationProvider;

import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;
import com.CadeMixedUpGame.api.models.LeaderBoardItem;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.LeaderBoardViewModel;
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

/**
 * Tier A coverage for the voting/leaderboard flow, against a real emulator round trip (not just
 * the pure comparison functions - selectWinningVotingItem/removeWhichItem already had unit
 * coverage before this session). Same harness/pattern as MultiplayerEmulatorTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LeaderBoardEmulatorTest {
    private static final int WAIT_SECONDS = 10;

    private FirebaseApp app;
    private DatabaseReference db;
    private LeaderBoardViewModel leaderBoard;
    private MutableLiveData<User> userA;
    private MutableLiveData<User> userB;
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

        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "lb-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(app, "localhost", true);
        db = FirebaseDatabase.getInstance(app).getReference();

        // leaderBoard is a single global (not room-scoped) top-level node in the real schema, and
        // the local emulator's data persists across separate test methods/runs within a session -
        // wipe it before each test so isLeaderBoardFull()'s "size() < 20" check and exact-size
        // assertions below are actually isolated, not polluted by a previous test's writes.
        clearGlobalLeaderBoard();

        leaderBoard = new LeaderBoardViewModel(db, false);

        roomId = "lb-room-" + System.nanoTime();
        userA = new MutableLiveData<>(new User("PlayerA"));
        userA.getValue().gameRoom = roomId;
        userA.getValue().userID = 1;
        userB = new MutableLiveData<>(new User("PlayerB"));
        userB.getValue().gameRoom = roomId;
        userB.getValue().userID = 2;
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.delete();
        }
    }

    private void clearGlobalLeaderBoard() throws InterruptedException {
        CountDownLatch cleared = new CountDownLatch(1);
        db.child("leaderBoard").removeValue().addOnCompleteListener(t -> cleared.countDown());
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
        while (cleared.getCount() > 0 && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(50);
        }
        assertTrue("clearing the global leaderboard before the test timed out", cleared.getCount() == 0);
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
    public void votingRoundTripPicksTheMostVotedItemAndPushesItToTheLeaderboard() throws InterruptedException {
        leaderBoard.loadLeaderBoardItems();
        leaderBoard.loadVotingItems(userA);
        LeaderBoardItem itemA = votingItem("a-item", "PlayerA");
        LeaderBoardItem itemB = votingItem("b-item", "PlayerB");
        leaderBoard.pushVoteItem(userA, itemA);
        leaderBoard.pushVoteItem(userB, itemB);
        waitUntil(() -> leaderBoard.getPotentialLeaderBoardItems().size() >= 2);

        leaderBoard.createAndListenToCastVotes(roomId);
        leaderBoard.castVoteListener(2);

        CountDownLatch aVoted = new CountDownLatch(1);
        leaderBoard.castVote(userA, "a-item", aVoted::countDown);
        assertTrue("first vote timed out", awaitLatch(aVoted));
        CountDownLatch bVoted = new CountDownLatch(1);
        leaderBoard.castVote(userB, "a-item", bVoted::countDown);
        assertTrue("second vote timed out", awaitLatch(bVoted));

        waitUntil(() -> leaderBoard.getLeaderBoard().size() >= 1);
        assertEquals("the unanimous winner must reach the leaderboard", 1, leaderBoard.getLeaderBoard().size());
        assertEquals("a-item", leaderBoard.getLeaderBoard().get(0).getId());
        assertEquals("unanimous vote should be 100% loved", 100.0, leaderBoard.getLeaderBoard().get(0).getPercentLoved(), 0.001);
    }

    @Test
    public void tiedVotesKeepTheFirstEncounteredItemAsTheWinner() throws InterruptedException {
        leaderBoard.loadLeaderBoardItems();
        leaderBoard.loadVotingItems(userA);
        // IDs chosen so Firebase's default key-ordering places "tie-a" before "tie-b", matching
        // insertion order into potentialLeaderBoardItems - selectWinningVotingItem uses a strict
        // "<" comparison, so a tie is won by whichever item it encountered first, not the other.
        leaderBoard.pushVoteItem(userA, votingItem("tie-a", "PlayerA"));
        leaderBoard.pushVoteItem(userB, votingItem("tie-b", "PlayerB"));
        waitUntil(() -> leaderBoard.getPotentialLeaderBoardItems().size() >= 2);

        leaderBoard.createAndListenToCastVotes(roomId);
        leaderBoard.castVoteListener(2);

        CountDownLatch aVoted = new CountDownLatch(1);
        leaderBoard.castVote(userA, "tie-a", aVoted::countDown);
        assertTrue(awaitLatch(aVoted));
        CountDownLatch bVoted = new CountDownLatch(1);
        leaderBoard.castVote(userB, "tie-b", bVoted::countDown);
        assertTrue(awaitLatch(bVoted));

        waitUntil(() -> leaderBoard.getLeaderBoard().size() >= 1);
        assertEquals("a 1-1 tie must resolve to the first item encountered, not crash or pick randomly",
                "tie-a", leaderBoard.getLeaderBoard().get(0).getId());
        assertEquals(50.0, leaderBoard.getLeaderBoard().get(0).getPercentLoved(), 0.001);
    }

    @Test
    public void findBestSentenceDoesNotAutoFireUntilEveryExpectedPlayerHasVoted() throws InterruptedException {
        leaderBoard.loadLeaderBoardItems();
        leaderBoard.loadVotingItems(userA);
        leaderBoard.pushVoteItem(userA, votingItem("a-item", "PlayerA"));
        leaderBoard.pushVoteItem(userB, votingItem("b-item", "PlayerB"));
        waitUntil(() -> leaderBoard.getPotentialLeaderBoardItems().size() >= 2);

        leaderBoard.createAndListenToCastVotes(roomId);
        // A third, non-existent player is expected - findBestSentence must not auto-fire with
        // only 2 of 3 votes in.
        leaderBoard.castVoteListener(3);

        CountDownLatch aVoted = new CountDownLatch(1);
        leaderBoard.castVote(userA, "a-item", aVoted::countDown);
        assertTrue(awaitLatch(aVoted));
        CountDownLatch bVoted = new CountDownLatch(1);
        leaderBoard.castVote(userB, "b-item", bVoted::countDown);
        assertTrue(awaitLatch(bVoted));

        // Give the (correctly non-firing) auto-advance every chance to wrongly fire before asserting.
        Thread.sleep(500);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("a player who never votes must block auto-advance to the leaderboard",
                0, leaderBoard.getLeaderBoard().size());
    }

    @Test
    public void newWinnerReplacesOnlyTheWeakestFullLeaderboardEntry() throws InterruptedException {
        leaderBoard.loadLeaderBoardItems();
        seedFullLeaderboard();
        waitUntil(() -> leaderBoard.getLeaderBoard().size() >= 20);
        assertEquals(20, leaderBoard.getLeaderBoard().size());

        leaderBoard.loadVotingItems(userA);
        leaderBoard.pushVoteItem(userA, votingItem("new-winner", "PlayerA"));
        waitUntil(() -> leaderBoard.getPotentialLeaderBoardItems().size() >= 1);

        leaderBoard.createAndListenToCastVotes(roomId);
        leaderBoard.castVoteListener(2);
        CountDownLatch aVoted = new CountDownLatch(1);
        leaderBoard.castVote(userA, "new-winner", aVoted::countDown);
        assertTrue(awaitLatch(aVoted));
        CountDownLatch bVoted = new CountDownLatch(1);
        leaderBoard.castVote(userB, "new-winner", bVoted::countDown);
        assertTrue(awaitLatch(bVoted));

        waitUntil(() -> containsId(leaderBoard.getLeaderBoard(), "new-winner"));
        assertTrue("100%-loved new item must beat the weakest (0% loved) entry",
                containsId(leaderBoard.getLeaderBoard(), "new-winner"));
        // The weakest seeded entry ("weak-0", percentLoved=0) must actually be gone locally too -
        // this is what the onChildRemoved handling added this session fixes; before that fix the
        // local list only ever grew (21 entries) even though Firebase correctly had 20.
        waitUntil(() -> leaderBoard.getLeaderBoard().size() == 20);
        assertEquals("leaderboard must stay capped at 20 after a replacement, locally as well as in Firebase",
                20, leaderBoard.getLeaderBoard().size());
        assertFalse("weakest entry must be removed from the local list, not just Firebase",
                containsId(leaderBoard.getLeaderBoard(), "weak-0"));
    }

    private static boolean containsId(Iterable<LeaderBoardItem> items, String id) {
        for (LeaderBoardItem item : items) {
            if (id.equals(item.getId())) {
                return true;
            }
        }
        return false;
    }

    private static void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    private void seedFullLeaderboard() throws InterruptedException {
        CountDownLatch seeded = new CountDownLatch(20);
        for (int i = 0; i < 20; i++) {
            LeaderBoardItem item = votingItem("weak-" + i, "Seed");
            item.setPercentLoved(i); // "weak-0" is the strictly weakest (lowest percentLoved) entry
            item.setLoadedToLeaderBoard(1_000_000L + i);
            db.child("leaderBoard").child(Long.toString(item.getLoadedToLeaderBoard())).setValue(item)
                    .addOnCompleteListener(t -> seeded.countDown());
        }
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
        while (seeded.getCount() > 0 && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(50);
        }
        assertTrue("seeding the leaderboard timed out", seeded.getCount() == 0);
    }

    private LeaderBoardItem votingItem(String id, String contributor) {
        return new LeaderBoardItem("if " + id, "then " + id, contributor, contributor, contributor + "-id", contributor + "-id", id);
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
