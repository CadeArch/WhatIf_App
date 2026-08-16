package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ApplicationProvider;

import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;
import com.CadeMixedUpGame.api.GameFlowPolicy;
import com.CadeMixedUpGame.api.GameLogic;
import com.CadeMixedUpGame.api.models.GamePhase;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end coverage for leaving, being removed, and a host going away - against a real Firebase
 * emulator, one test per distinct flow.
 *
 * <h3>Why none of these wait 90 seconds</h3>
 * Every threshold in this feature is evaluated against <em>data</em>, not against a timer the app
 * owns: {@code canKickPlayer} reads {@code player.disconnectedAt}, and the host-away deadline reads
 * {@code hostConnection/lastSeenAt}. So a test makes someone look long-gone by writing a timestamp
 * from ten minutes ago and the production code - real clock, real policy, no test-only branches -
 * immediately agrees they are removable.
 *
 * <p>That is deliberate in place of adding an overridable threshold or a debug flag. Test-only
 * branching inside shipping code is exactly the pattern this repo rejected once before (routing
 * production database calls through a test-aware helper), and it has the same flaw here: it proves
 * the app behaves correctly <em>in test mode</em>, which is not the thing anyone needs to know.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PlayerRemovalEmulatorTest {
    private static final int WAIT_SECONDS = 10;
    /** Comfortably past KICK_ELIGIBLE_AFTER_MS without depending on its exact value. */
    private static final long LONG_GONE_MS = 10L * 60L * 1000L;

    private FirebaseApp app;
    private DatabaseReference db;
    private RoomViewModel room;
    private UserViewModel observer;
    private String roomId;

    @Before
    public void setUp() {
        assumeTrue("Firebase Emulator Suite not running on 127.0.0.1:9000 - start it with "
                        + "`firebase emulators:start --config firebase.emulator.json --only database,auth`",
                emulatorReachable());
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setApiKey("fake-api-key")
                .setApplicationId("1:0:android:0")
                .setProjectId("demo-mixedupgame")
                .setDatabaseUrl("https://demo-mixedupgame-default-rtdb.firebaseio.com")
                .build();
        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options,
                "removal-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(app, "localhost", true);
        db = FirebaseDatabase.getInstance(app).getReference();
        room = new RoomViewModel(new FirebaseGameRepository(db), false);
        observer = new UserViewModel(db, null, false);
        roomId = "removal-room-" + System.nanoTime();
    }

    @After
    public void tearDown() {
        if (db != null && roomId != null) {
            db.child("rooms").child(roomId).removeValue();
        }
        if (app != null) {
            app.delete();
        }
    }

    // --- Flow 1: a player removed before reading is written out of the rebuilt round ---

    @Test
    public void removingAPlayerBeforeReadingRebuildsTheRoundWithoutThem() throws Exception {
        UserViewModel host = joinPlayer("Host", 1, true);
        UserViewModel guest = joinPlayer("Guest", 2, false);
        UserViewModel leaver = joinPlayer("Leaver", 3, false);
        observer.loadUsers(roomId);
        waitUntil(() -> observer.getUsers().size() >= 3);

        awaitAssignments();
        String leaverKey = GameLogic.playerKey(leaver.getUser().getValue());
        assertTrue("the leaver should be in the original round",
                assignmentKeys().contains(leaverKey));

        makeLongGone(leaver.getUser().getValue());
        waitUntil(() -> GameFlowPolicy.canKickPlayer(findUser(leaverKey), System.currentTimeMillis()));

        removeAndWait(leaverKey, GamePhase.COLLECTING_IFS);

        // The rebuilt plan must not mention them anywhere - not as a reader, and not as the owner of
        // an If or Then someone else is waiting on. That is the whole point of rebuilding: otherwise
        // a player removed between the If and Then phases strands an If with no Then.
        waitUntil(() -> !assignmentKeys().contains(leaverKey));
        Map<String, Object> assignments = readAssignments();
        assertEquals("two players should remain in the round", 2, assignments.size());
        assertFalse("no surviving assignment may reference the removed player",
                readAssignmentsRaw().contains(leaverKey));
    }

    // --- Flow 2: a player removed during reading leaves the round alone ---

    @Test
    public void removingAPlayerDuringReadingKeepsTheExistingRound() throws Exception {
        UserViewModel host = joinPlayer("Host", 1, true);
        UserViewModel guest = joinPlayer("Guest", 2, false);
        UserViewModel leaver = joinPlayer("Leaver", 3, false);
        observer.loadUsers(roomId);
        waitUntil(() -> observer.getUsers().size() >= 3);

        awaitAssignments();
        String roundBefore = readRoundId();
        String leaverKey = GameLogic.playerKey(leaver.getUser().getValue());

        makeLongGone(leaver.getUser().getValue());
        removeAndWait(leaverKey, GamePhase.READING);

        // Re-pairing here would reshuffle sentences people have already heard, so the round must be
        // left exactly as it was - the departed player keeps their slot and the host covers it.
        settle(1000);
        assertEquals("the round must not be regenerated mid-reading", roundBefore, readRoundId());
        assertTrue("their reading slot stays so indexes do not shift",
                assignmentKeys().contains(leaverKey));
    }

    // --- Flow 3: a removed player stops holding up the round ---

    @Test
    public void aRemovedPlayerNoLongerBlocksProgression() throws Exception {
        UserViewModel host = joinPlayer("Host", 1, true);
        UserViewModel guest = joinPlayer("Guest", 2, false);
        UserViewModel stuck = joinPlayer("Stuck", 3, false);
        observer.loadUsers(roomId);
        waitUntil(() -> observer.getUsers().size() >= 3);

        submitIf(host);
        submitIf(guest);
        settle(500);
        assertFalse("the round waits for the third player indefinitely",
                GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));

        String stuckKey = GameLogic.playerKey(stuck.getUser().getValue());
        makeLongGone(stuck.getUser().getValue());
        removeAndWait(stuckKey, GamePhase.COLLECTING_IFS);

        waitUntil(() -> GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));
        assertTrue("removing the absent player releases the round",
                GameFlowPolicy.allPlayersFinishedIfs(observer.getUsers()));
    }

    // --- Flow 4: dropping below two players ends the round instead of stranding someone ---

    @Test
    public void removingDownToOnePlayerReportsTheRoundUnviable() throws Exception {
        UserViewModel host = joinPlayer("Host", 1, true);
        UserViewModel leaver = joinPlayer("Leaver", 2, false);
        observer.loadUsers(roomId);
        waitUntil(() -> observer.getUsers().size() >= 2);

        String leaverKey = GameLogic.playerKey(leaver.getUser().getValue());
        makeLongGone(leaver.getUser().getValue());

        AtomicBoolean removed = new AtomicBoolean(false);
        AtomicBoolean unviable = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);
        room.removePlayerFromRound(roomId, leaverKey, observer.getUsers(), GamePhase.COLLECTING_IFS,
                () -> { removed.set(true); done.countDown(); },
                () -> { unviable.set(true); done.countDown(); });
        assertTrue("removal timed out", awaitLatch(done));

        assertTrue("one player left cannot make a round - the caller must be told to end it",
                unviable.get());
        assertFalse("and must not be told the round simply carries on", removed.get());
    }

    // --- Flow 5: a stale host heartbeat reads as "host away", not as a dead room ---

    @Test
    public void aStaleHostHeartbeatMarksTheHostAwayWithoutEndingTheRoom() throws Exception {
        UserViewModel host = joinPlayer("Host", 1, true);
        UserViewModel guest = joinPlayer("Guest", 2, false);
        observer.loadUsers(roomId);
        waitUntil(() -> observer.getUsers().size() >= 2);

        // A host whose phone locked keeps connected=true while the heartbeat simply stops - measured
        // on a real device, that is exactly the shape of it, and it is why a stale timestamp rather
        // than a disconnect is the signal that matters here.
        long staleAt = System.currentTimeMillis() - LONG_GONE_MS;
        Map<String, Object> hostConnection = new HashMap<String, Object>();
        hostConnection.put("connected", true);
        hostConnection.put("lastSeenAt", staleAt);
        setValueAndWait(db.child("rooms").child(roomId).child("hostConnection"), hostConnection);

        assertTrue("the heartbeat deadline should have passed",
                GameFlowPolicy.hostHeartbeatExpired(System.currentTimeMillis(), staleAt));

        // The room itself must still be there. Deleting it on this signal is precisely the bug that
        // ended real games whose host was mid phone call.
        settle(1000);
        assertTrue("an away host must not cost anyone the room", roomExists());
    }

    // --- Flow 6: the host covers a reading turn belonging to someone who is gone ---

    @Test
    public void theHostCoversAReadingTurnForAPlayerWhoIsGone() throws Exception {
        UserViewModel host = joinPlayer("Host", 1, true);
        UserViewModel guest = joinPlayer("Guest", 2, false);
        UserViewModel absent = joinPlayer("Absent", 3, false);
        observer.loadUsers(roomId);
        waitUntil(() -> observer.getUsers().size() >= 3);

        makeLongGone(absent.getUser().getValue());
        String absentKey = GameLogic.playerKey(absent.getUser().getValue());
        waitUntil(() -> findUser(absentKey) != null
                && Boolean.FALSE.equals(findUser(absentKey).connected));

        User hostUser = findUser(GameLogic.playerKey(host.getUser().getValue()));
        User guestUser = findUser(GameLogic.playerKey(guest.getUser().getValue()));
        User absentUser = findUser(absentKey);
        long now = System.currentTimeMillis();

        // Reading advances by key match, so without this the round stops dead on their turn and
        // nobody - including the host - can move it on.
        assertTrue("the host must be able to take an absent player's turn",
                GameFlowPolicy.hostMayCoverReadingTurn(hostUser, absentUser, now));
        assertFalse("but a guest must not be able to take someone else's turn",
                GameFlowPolicy.hostMayCoverReadingTurn(guestUser, absentUser, now));
    }

    // --- helpers ---

    /** The "backdoor": backdate their disconnect so production code sees a long absence now. */
    private void makeLongGone(User user) throws Exception {
        Map<String, Object> update = new HashMap<String, Object>();
        update.put("connected", false);
        update.put("disconnectedAt", System.currentTimeMillis() - LONG_GONE_MS);
        updateAndWait(db.child("rooms").child(roomId).child("players")
                .child(GameLogic.playerKey(user)), update);
    }

    private void removeAndWait(String playerKey, GamePhase phase) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        room.removePlayerFromRound(roomId, playerKey, observer.getUsers(), phase,
                done::countDown, done::countDown);
        assertTrue("removal timed out", awaitLatch(done));
    }

    private void awaitAssignments() throws Exception {
        CountDownLatch built = new CountDownLatch(1);
        room.createRoundAssignments(roomId, observer.getUsers(), built::countDown);
        assertTrue("assignments timed out", awaitLatch(built));
        waitUntil(() -> !assignmentKeys().isEmpty());
    }

    private java.util.Set<String> assignmentKeys() {
        return readAssignments().keySet();
    }

    private Map<String, Object> readAssignments() {
        DataSnapshot snapshot = readOnce(db.child("rooms").child(roomId).child("roundAssignments"));
        Map<String, Object> out = new HashMap<String, Object>();
        if (snapshot != null) {
            for (DataSnapshot child : snapshot.getChildren()) {
                out.put(child.getKey(), child.getValue());
            }
        }
        return out;
    }

    private String readAssignmentsRaw() {
        DataSnapshot snapshot = readOnce(db.child("rooms").child(roomId).child("roundAssignments"));
        return snapshot == null || snapshot.getValue() == null ? "" : snapshot.getValue().toString();
    }

    private String readRoundId() {
        DataSnapshot snapshot = readOnce(db.child("rooms").child(roomId).child("currentRoundId"));
        return snapshot == null ? null : snapshot.getValue(String.class);
    }

    private boolean roomExists() {
        DataSnapshot snapshot = readOnce(db.child("rooms").child(roomId));
        return snapshot != null && snapshot.exists();
    }

    private User findUser(String playerKey) {
        for (User user : observer.getUsers()) {
            if (playerKey.equals(GameLogic.playerKey(user))) {
                return user;
            }
        }
        return null;
    }

    private DataSnapshot readOnce(DatabaseReference ref) {
        AtomicReference<DataSnapshot> result = new AtomicReference<DataSnapshot>();
        CountDownLatch latch = new CountDownLatch(1);
        ref.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                result.set(task.getResult());
            }
            latch.countDown();
        });
        try {
            awaitLatch(latch);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result.get();
    }

    private void updateAndWait(DatabaseReference ref, Map<String, Object> update) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ref.updateChildren(update).addOnCompleteListener(task -> latch.countDown());
        assertTrue("update timed out", awaitLatch(latch));
    }

    private void setValueAndWait(DatabaseReference ref, Object value) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ref.setValue(value).addOnCompleteListener(task -> latch.countDown());
        assertTrue("write timed out", awaitLatch(latch));
    }

    private void submitIf(UserViewModel player) throws Exception {
        User user = player.getUser().getValue();
        user.ifSentence = "if something";
        user.ifFinished = true;
        Map<String, Object> update = new HashMap<String, Object>();
        update.put("ifSentence", user.ifSentence);
        update.put("ifFinished", true);
        updateAndWait(db.child("rooms").child(roomId).child("players")
                .child(GameLogic.playerKey(user)), update);
    }

    private UserViewModel joinPlayer(String name, int userID, boolean host) throws Exception {
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

    private void waitUntil(Condition condition) throws Exception {
        long deadline = System.currentTimeMillis() + (WAIT_SECONDS * 1000L);
        while (System.currentTimeMillis() < deadline) {
            if (condition.isMet()) {
                return;
            }
            pump();
        }
        assertTrue("condition not met within " + WAIT_SECONDS + "s", condition.isMet());
    }

    /**
     * Runs whatever Firebase has queued on the main looper.
     *
     * <p>Robolectric does not run the main looper on its own, and the Firebase SDK delivers every
     * completion callback there - so a plain {@code latch.await()} blocks the test thread while the
     * work that would release it never executes. Every wait in this class has to idle the looper,
     * or it deadlocks and reports as a timeout that looks like the emulator being unreachable.
     */
    private void pump() throws InterruptedException {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Thread.sleep(50);
    }

    private boolean awaitLatch(CountDownLatch latch) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (WAIT_SECONDS * 1000L);
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
            pump();
        }
        return latch.getCount() == 0;
    }

    /** A settle period that still lets queued callbacks run. */
    private void settle(long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            pump();
        }
    }

    private boolean emulatorReachable() {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", 9000), 1000);
            return true;
        }
        catch (IOException e) {
            return false;
        }
        finally {
            try {
                socket.close();
            }
            catch (IOException ignored) {
                // nothing to do
            }
        }
    }
}
