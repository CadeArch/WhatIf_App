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
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.ViewAssertion;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The exact flow Cade said caught most of this session's real bugs (2 players join, write
 * If/Then, read turn-by-turn, reach the end screen, Play Again, loop a second round) - now
 * exercised through two REAL devices/emulators end to end, not just the ViewModel-level version
 * (ReplayLoopEmulatorTest) built earlier this session. This is the regression test meant to catch
 * anything like the false host-disconnect-during-replay race or the empty-room cleanup gap ever
 * coming back, at the real UI layer where they actually surfaced.
 *
 * Free Play mode only - account-play would branch through VoteFrag/LeaderBoardFrag instead of
 * going straight from ReadSentenceFrag to EndFrag (see GameFlowPolicy.allPlayersHaveAccounts),
 * which is out of scope here. Real flow traced in full: WaitingForHostFrag (host clicks start,
 * guest auto-advances by observing currentRoundId) -> WriteIfFrag -> CollectingQuestionsFrag
 * (auto-advance) -> WriteThenFrag -> CollectingAnswersFrag (auto-advance) -> ReadSentenceFrag
 * (host reads first; R.id.next_frag is a single "show" then "pass" button per turn -
 * R.id.pass_reading_turn is dead/always-hidden UI in the current build) -> EndFrag
 * (R.id.again_ending: host-enabled-by-default, guest-disabled-until-replayState=="yes").
 *
 * Every round except the last ends with both devices clicking again_ending and looping. The last
 * round ends with the HOST clicking home_ending instead - this exercises the other real end-of-game
 * path (host explicitly ending the game: replayState="no" + deleteRoom) rather than only ever
 * testing the replay loop. The guest does NOT click anything for this - EndFrag's own
 * replayState=="no" observer auto-navigates a non-host player home the moment the host ends the
 * game, matching real gameplay (see EndFrag.java's non-host replayState.observe(...) branch).
 *
 * Same role/correlationId instrumentation-argument convention as TwoDeviceMultiplayerTest - see
 * that class's javadoc and E2ERoomCodeSignal for the full harness rationale
 * (scripts/run-tier-b.ps1 orchestrates this via -TestClass
 * com.CadeMixedUpGame.phoneapp.TwoDeviceFullGameLoopTest). Round count defaults to 2 but is
 * overridable per run via an instrumentation argument (-e rounds <n>, scripts/run-tier-b.ps1's
 * -Rounds parameter) rather than a recompile.
 */
@RunWith(AndroidJUnit4.class)
public class TwoDeviceFullGameLoopTest {
    private static final long WAIT_TIMEOUT_MS = 20_000L;
    private static final int DEFAULT_ROUNDS_TO_PLAY = 2;

    private String role;
    private int roundsToPlay;

    @Test
    public void twoPlayersCompleteTwoFullRoundsWithPlayAgainBetween() {
        Bundle args = InstrumentationRegistry.getArguments();
        role = args.getString("role");
        roundsToPlay = parseRounds(args.getString("rounds"));
        String correlationId = args.getString("correlationId");
        if (correlationId == null || correlationId.length() == 0) {
            fail("both roles require a correlationId instrumentation argument (-e correlationId <id>)");
        }
        if ("host".equals(role)) {
            runHost(correlationId);
        }
        else if ("guest".equals(role)) {
            runGuest(correlationId);
        }
        else {
            fail("Missing/unknown role instrumentation argument - expected -e role host|guest, got: " + role);
        }
    }

    private void runHost(String correlationId) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            assertNoAccountOnlyControls();
            onView(withId(R.id.enterName)).perform(typeText("E2E-Host"), closeSoftKeyboard());
            onView(withId(R.id.create_game)).perform(click());

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.replace)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            String roomCode = extractText(R.id.replace);
            assertTrue("extracted room code must be non-empty", roomCode != null && roomCode.length() > 0);

            E2ERoomCodeSignal.publish(correlationId, roomCode);

            onView(withId(R.id.createGame_start)).perform(click());
            EspressoWaitUtils.waitFor(() -> onView(withId(R.id.recyclerView)).check(playerCountAtLeast(2)), WAIT_TIMEOUT_MS);

            for (int round = 1; round <= roundsToPlay; round++) {
                playOneRound(round, true, round == roundsToPlay);
            }
        }
    }

    private void runGuest(String correlationId) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            assertNoAccountOnlyControls();
            onView(withId(R.id.enterName)).perform(typeText("E2E-Guest"), closeSoftKeyboard());
            onView(withId(R.id.joinGame)).perform(click());
            onView(withId(R.id.enterGameCode)).check(matches(isDisplayed()));

            // Everything above runs immediately in parallel with the host's own room-creation
            // steps - this wait is the only point that actually depends on the host being ahead.
            String roomCode = E2ERoomCodeSignal.awaitRoomCode(correlationId, WAIT_TIMEOUT_MS);

            onView(withId(R.id.enterGameCode)).perform(typeText(roomCode), closeSoftKeyboard());
            onView(withId(R.id.joinGame_start)).perform(click());
            // Deliberately NOT waiting for the recyclerView to show 2 players here (unlike
            // TwoDeviceMultiplayerTest, which ends right after this and never races against
            // anything else): the host only needs to see 2 players on ITS OWN device before
            // clicking start, and can win that race before the guest's identical check ever
            // succeeds once. Once the host starts the game, WaitingForHostFrag - and its
            // recyclerView - is torn down and replaced by WriteIfFrag, so a guest still polling
            // for that view would wait for the full timeout and never even get to write anything.
            // playOneRound's own waitFor(ifQuestion) is what actually confirms the guest is in the
            // right place, and does so regardless of which device won that race.
            for (int round = 1; round <= roundsToPlay; round++) {
                playOneRound(round, false, round == roundsToPlay);
            }
        }
    }

    private static int parseRounds(String rawValue) {
        if (rawValue == null || rawValue.length() == 0) {
            return DEFAULT_ROUNDS_TO_PLAY;
        }
        try {
            int parsed = Integer.parseInt(rawValue);
            return parsed > 0 ? parsed : DEFAULT_ROUNDS_TO_PLAY;
        }
        catch (NumberFormatException e) {
            return DEFAULT_ROUNDS_TO_PLAY;
        }
    }

    /** Mirrors the real per-round sequence exactly - see this class's javadoc for the traced flow.
     * Both roles call the same sequence; only who clicks waitingForHost_start / who has to wait
     * for again_ending to become enabled / what happens at the end differs. */
    private void playOneRound(int roundNumber, boolean isHost, boolean isLastRound) {
        if (isHost) {
            // nurfAllUsers() (from the PREVIOUS round's again_ending click) wipes every player,
            // then each device re-pushes itself - there's a real window, on round 2+, where the
            // host's own local player list has caught back up to only 1 player by the time
            // waitingForHost_start becomes visible again. Clicking too early hits the app's own
            // (correct) "Wait for at least one more player" guard and silently no-ops, which is
            // exactly what happened watching this run live before this wait was added here too
            // (round 1 already had this check, just not re-applied for later rounds).
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.waitingForHost_start)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            EspressoWaitUtils.waitFor(() -> onView(withId(R.id.recyclerView)).check(playerCountAtLeast(2)), WAIT_TIMEOUT_MS);
            onView(withId(R.id.waitingForHost_start)).perform(click());
        }

        EspressoWaitUtils.waitFor(() -> onView(withId(R.id.ifQuestion)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        onView(withId(R.id.ifQuestion)).perform(typeText("If round " + roundNumber + " " + role), closeSoftKeyboard());
        onView(withId(R.id.writeIf_submit)).perform(click());

        EspressoWaitUtils.waitFor(() -> onView(withId(R.id.thenAnswer)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        onView(withId(R.id.thenAnswer)).perform(typeText("Then round " + roundNumber + " " + role), closeSoftKeyboard());
        onView(withId(R.id.writeThen_submit)).perform(click());

        EspressoWaitUtils.waitFor(() -> onView(withId(R.id.next_frag)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        playMyReadingTurn();

        if (isLastRound) {
            if (isHost) {
                EspressoWaitUtils.waitFor(() -> onView(withId(R.id.again_ending)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
                onView(withId(R.id.home_ending)).perform(click());
            }
            // The guest deliberately does NOT wait for EndFrag to be on screen first. It clicks
            // nothing here - EndFrag's replayState=="no" observer auto-navigates it home as soon as
            // the host's home click lands - and against the local emulator that round trip can beat
            // the guest's own poll: observed EndFrag at :21.881 and StartFragment at :22.152, a
            // 271ms window, so a wait for again_ending would flake out having never seen it. Waiting
            // only for the home screen is both race-free and the thing actually worth asserting.
            EspressoWaitUtils.waitFor(() -> onView(withId(R.id.enterName)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            return;
        }

        EspressoWaitUtils.waitFor(() -> onView(withId(R.id.again_ending)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);

        if (!isHost) {
            // Guest's button starts disabled ("waiting") until it observes replayState=="yes",
            // which only happens after the host clicks first.
            EspressoWaitUtils.waitFor(() -> onView(withId(R.id.again_ending)).check(matches(isEnabled())), WAIT_TIMEOUT_MS);
        }
        onView(withId(R.id.again_ending)).perform(click());
    }

    /** Free play must not surface account-mode controls on the start screen. These are wired to
     * account features (a leaderboard fed by the account-only vote round, a profile, a sign-out)
     * that a guest can neither use nor appear in - Leader Boards in particular used to be left
     * visible here because applyUserMode never toggled it. */
    private void assertNoAccountOnlyControls() {
        onView(withId(R.id.enterName)).check(matches(isDisplayed()));
        onView(withId(R.id.leaderboards_button)).check(matches(not(isDisplayed())));
        onView(withId(R.id.profile_button)).check(matches(not(isDisplayed())));
        onView(withId(R.id.signOut)).check(matches(not(isDisplayed())));
    }

    /** The mic is an account-play control (text-to-speech of your own sentence) and free play
     * never gets one, on any reading turn. */
    private void assertNoMicControl() {
        onView(withId(R.id.readSentence)).check(matches(not(isDisplayed())));
    }

    /** R.id.next_frag is a single dual-purpose button: disabled until it's your turn, then "show"
     * (reveals the sentence) then "pass" (advances to the next reader). Waiting for it to become
     * enabled is what makes this correct regardless of read order - the other device's button
     * just stays disabled throughout its own turn. */
    private void playMyReadingTurn() {
        EspressoWaitUtils.waitFor(() -> onView(withId(R.id.next_frag)).check(matches(isEnabled())), WAIT_TIMEOUT_MS);
        assertNoMicControl();
        onView(withId(R.id.next_frag)).perform(click());
        EspressoWaitUtils.waitFor(() -> onView(withId(R.id.next_frag)).check(matches(withText("pass"))), WAIT_TIMEOUT_MS);
        onView(withId(R.id.next_frag)).perform(click());
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

    private ViewAssertion playerCountAtLeast(int minCount) {
        return (view, exception) -> {
            if (exception != null) {
                throw exception;
            }
            if (!(view instanceof RecyclerView)) {
                throw new RuntimeException("expected a RecyclerView, got " + view.getClass());
            }
            RecyclerView.Adapter<?> adapter = ((RecyclerView) view).getAdapter();
            int count = adapter == null ? 0 : adapter.getItemCount();
            if (count < minCount) {
                throw new RuntimeException("player count " + count + " has not yet reached " + minCount);
            }
        };
    }
}
