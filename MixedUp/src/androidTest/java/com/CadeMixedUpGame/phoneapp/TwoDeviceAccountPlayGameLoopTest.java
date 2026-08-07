package com.CadeMixedUpGame.phoneapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewAssertion;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Same real two-device round-loop coverage as {@link TwoDeviceFullGameLoopTest}, but for signed-in
 * Account Play instead of Free Play - two REAL Firebase Auth accounts (real production credentials,
 * never hardcoded here - see below), sign in, create/join, and play a full round including
 * VoteFrag, which Free Play skips entirely (see GameFlowPolicy.allPlayersHaveAccounts()). Confirmed
 * by hand this session that the two modes diverge in real, catchable ways: a fragile inflate(...,
 * null) bug in VoteFrag/LeaderBoardFrag only manifests for account players actually reaching those
 * screens, and a ConstraintLayout GONE-collapse bug in StartFragment only manifested once a real
 * signed-in account (not a Free Play guest name) reached it.
 *
 * Credentials are read from instrumentation arguments (-e email, -e password), never hardcoded in
 * this file - see local-test-accounts.md (gitignored) for the actual test account pool and
 * scripts/run-tier-b-account-play.ps1 for how they get passed in per role. Sign-up is deliberately
 * NOT exercised here (it would either collide with an already-existing test account or require
 * cleaning up a fresh Firebase Auth user after every run); both roles always sign in to an account
 * that's expected to already exist against whichever Firebase project the app under test is built
 * for (local Emulator Suite Auth if built with -PuseFirebaseEmulator=true, real production
 * otherwise - see CLAUDE.md's namespace note, same caution applies to Auth as to the database).
 *
 * Real flow traced by hand this session: AccountFrag (R.id.email/R.id.password/R.id.signIn) ->
 * StartFragment (same create_game/joinGame IDs as Free Play - the layout is shared) ->
 * CreateGameFrag/JoinGameFrag -> WaitingForHostFrag -> WriteIfFrag -> CollectingQuestionsFrag
 * (auto-advance) -> WriteThenFrag -> CollectingAnswersFrag (auto-advance) -> ReadSentenceFrag
 * (same host-first single show/pass button as Free Play) -> VoteFrag (R.id.potential_lbiList's
 * dynamically-inflated, ID-less children - see clickFirstVoteOption() - then R.id.vote_submit) ->
 * EndFrag, same again_ending/home_ending semantics as Free Play.
 */
@RunWith(AndroidJUnit4.class)
public class TwoDeviceAccountPlayGameLoopTest {
    private static final long WAIT_TIMEOUT_MS = 20_000L;
    private static final int DEFAULT_ROUNDS_TO_PLAY = 2;

    private String role;
    private int roundsToPlay;

    @Test
    public void twoAccountPlayersCompleteRoundsWithVoting() {
        Bundle args = InstrumentationRegistry.getArguments();
        role = args.getString("role");
        roundsToPlay = parseRounds(args.getString("rounds"));
        String correlationId = args.getString("correlationId");
        String email = args.getString("email");
        String password = args.getString("password");
        if (correlationId == null || correlationId.length() == 0) {
            fail("both roles require a correlationId instrumentation argument (-e correlationId <id>)");
        }
        if (email == null || email.length() == 0 || password == null || password.length() == 0) {
            fail("both roles require -e email <email> -e password <password> instrumentation arguments");
        }
        if ("host".equals(role)) {
            runHost(correlationId, email, password);
        }
        else if ("guest".equals(role)) {
            runGuest(correlationId, email, password);
        }
        else {
            fail("Missing/unknown role instrumentation argument - expected -e role host|guest, got: " + role);
        }
    }

    private void runHost(String correlationId, String email, String password) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            signIn(email, password);

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

    private void runGuest(String correlationId, String email, String password) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            signIn(email, password);

            onView(withId(R.id.joinGame)).perform(click());
            onView(withId(R.id.enterGameCode)).check(matches(isDisplayed()));

            // Runs in parallel with the host's own room-creation steps, same as
            // TwoDeviceFullGameLoopTest - only this wait actually depends on the host being ahead.
            String roomCode = E2ERoomCodeSignal.awaitRoomCode(correlationId, WAIT_TIMEOUT_MS);

            onView(withId(R.id.enterGameCode)).perform(typeText(roomCode), closeSoftKeyboard());
            onView(withId(R.id.joinGame_start)).perform(click());

            for (int round = 1; round <= roundsToPlay; round++) {
                playOneRound(round, false, round == roundsToPlay);
            }
        }
    }

    /** AccountFrag -> StartFragment. Sign-in is real Firebase Auth (whichever project this build
     * is configured for), so this genuinely round-trips a real account - see this class's javadoc
     * for why sign-up isn't exercised here instead. */
    private void signIn(String email, String password) {
        onView(withId(R.id.accountPlay)).perform(click());
        onView(withId(R.id.email)).perform(typeText(email), closeSoftKeyboard());
        onView(withId(R.id.password)).perform(typeText(password), closeSoftKeyboard());
        onView(withId(R.id.signIn)).perform(click());
        EspressoWaitUtils.waitFor(() -> onView(withId(R.id.create_game)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
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

    /** Same per-round sequence as TwoDeviceFullGameLoopTest.playOneRound, plus the VoteFrag step
     * account play adds between reading and the end screen. */
    private void playOneRound(int roundNumber, boolean isHost, boolean isLastRound) {
        if (isHost) {
            // Same nurfAllUsers() re-push race documented in TwoDeviceFullGameLoopTest - round 2+
            // needs to re-wait for 2 players, not just round 1.
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

        castVote();

        if (isLastRound) {
            if (isHost) {
                // A single click is enough now: CollectingVotesFrag holds everyone until the last
                // vote lands, so Home can no longer be silently refused for outstanding votes.
                EspressoWaitUtils.waitFor(() -> onView(withId(R.id.again_ending)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
                onView(withId(R.id.home_ending)).perform(click());
            }
            // Ending the game sends BOTH devices back to StartFragment, not to the sign-in screen:
            // going Home doesn't sign anybody out, so an account player is still authenticated and
            // never sees AccountFrag again. (Waiting on R.id.email here failed the test even though
            // the whole game had just played through correctly, voting and all - the guest gets
            // here on its own via EndFrag's replayState=="no" observer, no click needed.)
            // create_game is the StartFragment control that is visible in the signed-in state.
            EspressoWaitUtils.waitFor(() -> onView(withId(R.id.create_game)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
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

    private void playMyReadingTurn() {
        // Before the turn starts the mic must be hidden, not merely dimmed: it used to sit on the
        // page at alpha 0.35 the whole time someone else was reading, which still reads as an
        // available control. Only meaningful once the sentence is actually revealed below.
        onView(withId(R.id.readSentence)).check(matches(not(isDisplayed())));

        EspressoWaitUtils.waitFor(() -> onView(withId(R.id.next_frag)).check(matches(isEnabled())), WAIT_TIMEOUT_MS);
        onView(withId(R.id.next_frag)).perform(click());
        EspressoWaitUtils.waitFor(() -> onView(withId(R.id.next_frag)).check(matches(withText("pass"))), WAIT_TIMEOUT_MS);
        // Revealed and it's this player's turn - now the mic is a real control.
        onView(withId(R.id.readSentence)).check(matches(isDisplayed()));
        onView(withId(R.id.next_frag)).perform(click());
    }

    /** Account play only - VoteFrag between reading and the end screen. Vote options are
     * dynamically inflated lb_vote_item.xml children of R.id.potential_lbiList with no per-item
     * ID (every item reuses the same R.id.if_part/then_part), so a plain withId() match would be
     * ambiguous - clickFirstVoteOption() clicks the list's first child directly instead. */
    private void castVote() {
        EspressoWaitUtils.waitFor(() -> onView(withId(R.id.vote_submit)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        clickFirstVoteOption();
        onView(withId(R.id.vote_submit)).perform(click());
    }

    private void clickFirstVoteOption() {
        onView(withId(R.id.potential_lbiList)).perform(new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isAssignableFrom(LinearLayout.class);
            }

            @Override
            public String getDescription() {
                return "click the first vote option in the list";
            }

            @Override
            public void perform(UiController uiController, View view) {
                LinearLayout list = (LinearLayout) view;
                if (list.getChildCount() == 0) {
                    throw new RuntimeException("no vote options available yet");
                }
                list.getChildAt(0).performClick();
                uiController.loopMainThreadUntilIdle();
            }
        });
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
