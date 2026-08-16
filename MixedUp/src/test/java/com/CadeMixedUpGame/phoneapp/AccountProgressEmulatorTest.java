package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ApplicationProvider;

import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;
import com.CadeMixedUpGame.api.UnlockPolicy;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Characterization tests for the account progression record — games played, the unlockables rows,
 * and the leaderboard flags — written before that cluster was extracted out of
 * {@code UserViewModel} so the move could be proven not to change behavior. The same assertions run
 * against the old implementation and the new one.
 *
 * <p>None of this had emulator coverage: the unlock *rules* are unit tested in
 * {@code UnlockPolicyTest}, but what actually gets written to and read back from
 * {@code AccountPlayers/<uid>/<name>/...} was only ever checked by playing the game.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AccountProgressEmulatorTest {
    private static final int WAIT_SECONDS = 10;

    private FirebaseApp app;
    private DatabaseReference db;
    private UserViewModel userViewModel;
    private MutableLiveData<User> user;

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

        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "acctprog-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(app, "localhost", true);
        db = FirebaseDatabase.getInstance(app).getReference();
        userViewModel = new UserViewModel(db, null, false);

        // A fresh uid per test method: AccountPlayers is global and the emulator keeps data across
        // methods and gradle runs, so reusing one would let an earlier method decide the result.
        User account = new User("Progress");
        account.uid = "acct-" + System.nanoTime();
        account.userName = "Progress";
        account.accountPlay = true;
        user = new MutableLiveData<User>(account);
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.delete();
        }
    }

    @Test
    public void seedingAnAccountWritesEveryVoiceLocked() throws InterruptedException {
        userViewModel.fillUnlockables(user);

        waitUntil(() -> childCount(unlockablesRef()) == UnlockPolicy.catalog().size());
        assertEquals("a new account gets one row per catalog voice",
                UnlockPolicy.catalog().size(), childCount(unlockablesRef()));
        for (UnlockPolicy.Voice voice : UnlockPolicy.catalog()) {
            assertFalse("every voice starts locked: " + voice.getVoiceType(),
                    readBoolean(unlockablesRef().child(voice.getVoiceType()).child("unlocked")));
        }
    }

    @Test
    public void gamesPlayedStartsAtZeroAndIncrementsPersistently() throws InterruptedException {
        userViewModel.fillGamesPlayed(user);
        waitUntil(() -> readLong(gamesPlayedRef()) == 0L);
        assertEquals("a new account starts at zero games", 0L, readLong(gamesPlayedRef()));

        user.getValue().gamesPlayed = 0;
        userViewModel.incrementGamesPlayed(user);
        waitUntil(() -> readLong(gamesPlayedRef()) == 1L);
        assertEquals("playing a game persists the new count", 1L, readLong(gamesPlayedRef()));
        assertEquals("and updates the in-memory user too", 1, user.getValue().gamesPlayed);
    }

    @Test
    public void unlockingWritesExactlyTheEarnedVoicesAndLeavesTheRestLocked() throws InterruptedException {
        userViewModel.fillUnlockables(user);
        waitUntil(() -> childCount(unlockablesRef()) == UnlockPolicy.catalog().size());

        // 5 games, no leaderboard: earns the first two rungs and nothing else.
        user.getValue().gamesPlayed = 5;
        user.getValue().madeLeaderBoard = false;
        user.getValue().perfectLeaderBoard = false;
        userViewModel.unlockEarnedVoices(user);

        waitUntil(() -> readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_BACKWORDS).child("unlocked")));
        assertTrue(readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_JOKESTER).child("unlocked")));
        assertTrue(readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_BACKWORDS).child("unlocked")));
        assertFalse("a voice that isn't earned yet stays locked",
                readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_FORGETFUL).child("unlocked")));
        assertFalse("leaderboard voices need the leaderboard, not games",
                readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_FUDDIFY).child("unlocked")));
    }

    @Test
    public void unlockingIsIdempotentAndRepairsAnAccountThatMissedAnEarlierUnlock() throws InterruptedException {
        userViewModel.fillUnlockables(user);
        waitUntil(() -> childCount(unlockablesRef()) == UnlockPolicy.catalog().size());

        // Simulates a player who was offline when they crossed 5 games and only comes back at 20:
        // the sweep re-asserts everything earned so far, not just the newest rung.
        user.getValue().gamesPlayed = 20;
        userViewModel.unlockEarnedVoices(user);

        waitUntil(() -> readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_SHAGGY).child("unlocked")));
        assertTrue(readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_JOKESTER).child("unlocked")));
        assertTrue(readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_BACKWORDS).child("unlocked")));
        assertTrue(readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_FORGETFUL).child("unlocked")));
        assertTrue(readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_SHAGGY).child("unlocked")));

        // Running it again changes nothing and must not fail.
        userViewModel.unlockEarnedVoices(user);
        Thread.sleep(300L);
        assertTrue(readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_SHAGGY).child("unlocked")));
        assertFalse(readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_DISOBEDIENT).child("unlocked")));
    }

    @Test
    public void leaderboardUnlocksAlsoPersistTheFlagOnTheAccountRecord() throws InterruptedException {
        userViewModel.fillUnlockables(user);
        waitUntil(() -> childCount(unlockablesRef()) == UnlockPolicy.catalog().size());

        user.getValue().madeLeaderBoard = true;
        user.getValue().perfectLeaderBoard = true;
        userViewModel.unlockEarnedVoices(user);

        waitUntil(() -> readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_PIG_LATIN).child("unlocked")));
        assertTrue(readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_FUDDIFY).child("unlocked")));
        assertTrue(readBoolean(unlockablesRef().child(UnlockPolicy.VOICE_PIG_LATIN).child("unlocked")));
        // The flags live on the player record as well as driving the unlock.
        assertTrue(readBoolean(accountRef().child("madeLeaderBoard")));
        assertTrue(readBoolean(accountRef().child("perfectLeaderBoard")));
    }

    @Test
    public void loadingUnlockablesPopulatesTheListTheVoicePickerReads() throws InterruptedException {
        userViewModel.fillUnlockables(user);
        waitUntil(() -> childCount(unlockablesRef()) == UnlockPolicy.catalog().size());

        userViewModel.getUnlocked(user);

        waitUntil(() -> userViewModel.userUnlocked.size() == UnlockPolicy.catalog().size());
        assertEquals("every stored row is loaded for the picker",
                UnlockPolicy.catalog().size(), userViewModel.userUnlocked.size());
    }

    private DatabaseReference accountRef() {
        return db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName);
    }

    private DatabaseReference unlockablesRef() {
        return accountRef().child("unlockables");
    }

    private DatabaseReference gamesPlayedRef() {
        return accountRef().child("gamesPlayed");
    }

    private int childCount(DatabaseReference ref) {
        DataSnapshot snapshot = read(ref);
        return snapshot == null ? -1 : (int) snapshot.getChildrenCount();
    }

    private boolean readBoolean(DatabaseReference ref) {
        DataSnapshot snapshot = read(ref);
        return snapshot != null && Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
    }

    private long readLong(DatabaseReference ref) {
        DataSnapshot snapshot = read(ref);
        Long value = snapshot == null ? null : snapshot.getValue(Long.class);
        return value == null ? -1L : value;
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

    private void waitUntil(Condition condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
        while (!condition.isMet() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(25);
        }
    }
}
