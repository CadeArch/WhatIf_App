package com.CadeMixedUpGame.phoneapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.fail;

import android.os.Bundle;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.UnlockPolicy;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A hands-on harness for listening to the unlockable voices, not a pass/fail test.
 *
 * <p>It drives the real app to the one screen where the voice picker exists - the active reader's
 * revealed sentence in {@link ReadSentenceFrag} - with every voice in {@link UnlockPolicy#catalog()}
 * unlocked, and then <b>stays there</b> so the voices can actually be played with. Everything up to
 * that point is the genuine UI flow: real sign-in, real room creation, real writing phase. Only two
 * things are fabricated, and both only because they are the parts a single person at a single
 * device cannot supply:
 *
 * <ul>
 *   <li><b>The other players.</b> Written straight into the room by {@link E2ERoomFixture}, already
 *       finished writing and absent long enough that the host may cover their reading turns. That
 *       gives one person several sentences to audition instead of one, since "pass" walks to the
 *       next.</li>
 *   <li><b>The unlocks.</b> Earning all seven for real means forty games.</li>
 * </ul>
 *
 * <p><b>Why it hangs rather than asserting.</b> The point is the ear, not an assertion - the voice
 * mutations in {@code GameLogic} need tuning by listening to them, repeatedly, on the same sentence.
 * A normal test would tear the activity down at the end of the method and take the screen with it,
 * so this one parks on the reading screen for {@code holdMinutes} instead. Nothing here shortens a
 * threshold or flips a debug flag: the absent players are absent because their {@code disconnectedAt}
 * is genuinely old, exactly as in the Tier B removal tests.
 *
 * <p>Sentences are deliberately short. Long ones were tried first and are worse for this: a voice's
 * character shows up in the first few words, and waiting out a paragraph between every switch makes
 * comparing two voices needlessly slow.
 *
 * <p>Run it with {@code scripts/run-voice-audition.ps1}, which handles the emulator build, the
 * account seeding and the port tunnels. Stop it early with Ctrl-C; the hold ends on its own after
 * {@code holdMinutes}.
 */
@RunWith(AndroidJUnit4.class)
public class VoiceAuditionTest {
    private static final long WAIT_TIMEOUT_MS = 40000L;
    private static final long LONG_GONE_MS = 10L * 60L * 1000L;
    private static final int DEFAULT_HOLD_MINUTES = 45;

    /** Short on purpose - see the class comment. */
    private static final String[][] ABSENT_PLAYERS = {
            {"Robo", "cats could vote", "every nap would become a national holiday"},
            {"Pixel", "socks had opinions", "laundry day would end in a lawsuit"},
            {"Waffles", "gravity took weekends off", "brunch would get extremely dangerous"},
    };

    private static final String HOST_IF = "pigeons ran the post office";
    private static final String HOST_THEN = "every letter would arrive slightly chewed";

    /** Held so the activity is not a candidate for collection while the hold runs. */
    @SuppressWarnings("unused")
    private ActivityScenario<MainActivity> scenario;

    @Test
    public void holdOnTheReadingScreenWithEveryVoiceUnlocked() {
        Bundle args = InstrumentationRegistry.getArguments();
        String email = args.getString("email");
        String password = args.getString("password");
        if (email == null || password == null) {
            fail("requires -e email and -e password (see scripts/run-voice-audition.ps1)");
        }
        int holdMinutes = parseHoldMinutes(args.getString("holdMinutes"));

        scenario = ActivityScenario.launch(MainActivity.class);
        // Deliberately never closed: closing it finishes the activity, which is the one thing this
        // harness must not do.

        signIn(email, password);
        unlockEveryVoiceForSignedInAccount();

        onView(withId(R.id.create_game)).perform(click());
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.replace)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        String roomCode = extractText(R.id.replace);

        // Account players, so the round keeps the account-play shape the picker belongs to; absent,
        // so their reading turns fall to the host and one person can walk the whole read order.
        int userID = 5100;
        for (String[] player : ABSENT_PLAYERS) {
            E2ERoomFixture.addAbsentAccountPlayerWhoFinishedWriting(roomCode, player[0], userID++,
                    LONG_GONE_MS, player[1], player[2]);
        }

        onView(withId(R.id.createGame_start)).perform(click());
        EspressoWaitUtils.waitFor(() -> onView(withId(R.id.recyclerView))
                .check(TwoDevicePlayerCount.atLeast(ABSENT_PLAYERS.length + 1)), WAIT_TIMEOUT_MS);
        onView(withId(R.id.waitingForHost_start)).perform(click());

        writeIfAndThen();
        revealSentence();

        announce(roomCode, holdMinutes);
        hold(holdMinutes);
    }

    private int parseHoldMinutes(String raw) {
        if (raw == null) {
            return DEFAULT_HOLD_MINUTES;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : DEFAULT_HOLD_MINUTES;
        }
        catch (NumberFormatException notANumber) {
            return DEFAULT_HOLD_MINUTES;
        }
    }

    private void signIn(String email, String password) {
        onView(withId(R.id.accountPlay)).perform(click());
        onView(withId(R.id.email)).perform(typeText(email), closeSoftKeyboard());
        onView(withId(R.id.password)).perform(typeText(password), closeSoftKeyboard());
        onView(withId(R.id.signIn)).perform(click());
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.create_game)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
    }

    /**
     * The unlockables live under the account's own uid and display name, which only exist once the
     * sign-in above has actually completed - hence reading them from Auth here rather than taking
     * them as arguments, where a stale value would write a full set of voices to a path nothing
     * reads and leave the picker showing only "regular".
     */
    private void unlockEveryVoiceForSignedInAccount() {
        FirebaseUser signedIn = FirebaseAuth.getInstance().getCurrentUser();
        if (signedIn == null) {
            fail("sign-in reached the start screen but FirebaseAuth has no current user");
        }
        String userName = signedIn.getDisplayName();
        if (userName == null || userName.length() == 0) {
            fail("the signed-in account has no displayName, which the app uses as the player name -"
                    + " the Auth emulator account needs one (run-voice-audition.ps1 sets it)");
        }
        E2ERoomFixture.unlockAllVoices(signedIn.getUid(), userName);
    }

    private void writeIfAndThen() {
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.ifQuestion)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        onView(withId(R.id.ifQuestion)).perform(typeText(HOST_IF), closeSoftKeyboard());
        onView(withId(R.id.writeIf_submit)).perform(click());

        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.thenAnswer)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        onView(withId(R.id.thenAnswer)).perform(typeText(HOST_THEN), closeSoftKeyboard());
        onView(withId(R.id.writeThen_submit)).perform(click());
    }

    /**
     * Taps "show". The picker and the mic are both hidden until the sentence is revealed - showing
     * a voice picker before there is anything to say in that voice - so without this the harness
     * would park on a screen with nothing to audition.
     */
    private void revealSentence() {
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.next_frag)).check(matches(isEnabled())), WAIT_TIMEOUT_MS);
        onView(withId(R.id.next_frag)).perform(click());
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.next_frag)).check(matches(withText("pass"))), WAIT_TIMEOUT_MS);
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.spinnerObject)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
    }

    private void announce(String roomCode, int holdMinutes) {
        AppLog.i(AppLog.TTS, "VOICE AUDITION READY room=" + roomCode
                + " voices=" + (UnlockPolicy.catalog().size() + 1)
                + " holdMinutes=" + holdMinutes
                + " - pick a voice from the dropdown, tap the mic. \"pass\" moves to the next"
                + " sentence; after the last one the round ends, so re-run to get more.");
    }

    /**
     * Sleeps on the instrumentation thread, which is not the app's main thread - the UI stays fully
     * interactive throughout. Chunked so the log shows it is still alive rather than looking hung.
     */
    private void hold(int holdMinutes) {
        long deadline = System.currentTimeMillis() + holdMinutes * 60L * 1000L;
        while (System.currentTimeMillis() < deadline) {
            long remaining = deadline - System.currentTimeMillis();
            AppLog.i(AppLog.TTS, "VOICE AUDITION holding, " + (remaining / 60000L) + " min left");
            try {
                Thread.sleep(Math.min(60000L, remaining));
            }
            catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        AppLog.i(AppLog.TTS, "VOICE AUDITION hold finished - closing the app");
    }

    private String extractText(int viewId) {
        AtomicReference<String> captured = new AtomicReference<>();
        onView(withId(viewId)).check((view, exception) -> {
            if (exception != null) {
                throw exception;
            }
            captured.set(((TextView) view).getText().toString());
        });
        return captured.get();
    }
}
