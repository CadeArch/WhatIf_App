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
 * Removing a player <b>mid-round</b> and proving the remaining players still finish the game.
 *
 * <p>This is the case that actually matters. Removing someone in the lobby is nearly free - no
 * round exists yet, so there is nothing to corrupt. Removing them once a round is under way is
 * where it can go wrong: the round's assignments are a fixed plan naming who reads whose If and
 * whose Then, so a player who leaves after the plan is built can strand an If with no Then, or
 * leave a reading slot whose owner never arrives - and reading advances by key match, so an
 * orphaned slot stops the round dead for everyone still playing.
 *
 * <p>The shape here: three players start the round (two real devices plus one written directly into
 * the room, absent from the outset), the third is removed during the writing phase, and then the two
 * real players are driven all the way through writing, reading and onto the end screen. The
 * assertion is not "the removal succeeded" - it is that both survivors reach {@code again_ending},
 * which is only reachable if the rebuilt round was internally consistent the whole way through.
 *
 * <p>Runs through {@code scripts/run-tier-b.ps1 -TestClass ...RemovePlayerMidGameTest}, same
 * role/correlationId convention as the other two-device tests.
 */
@RunWith(AndroidJUnit4.class)
public class RemovePlayerMidGameTest {
    private static final long WAIT_TIMEOUT_MS = 40000L;
    private static final long LONG_GONE_MS = 10L * 60L * 1000L;
    private static final String ABSENT_NAME = "guest-Ghost";
    private static final int ABSENT_ID = 4242;

    private String role;

    @Test
    public void aPlayerRemovedMidRoundDoesNotStopTheOthersFinishing() {
        Bundle args = InstrumentationRegistry.getArguments();
        role = args.getString("role");
        String correlationId = args.getString("correlationId");
        if (correlationId == null || correlationId.length() == 0) {
            fail("both roles require a correlationId instrumentation argument");
        }
        if ("host".equals(role)) {
            runHost(correlationId);
        }
        else if ("guest".equals(role)) {
            runGuest(correlationId);
        }
        else {
            fail("expected -e role host|guest, got: " + role);
        }
    }

    private void runHost(String correlationId) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            onView(withId(R.id.enterName)).perform(typeText("E2E-Host"), closeSoftKeyboard());
            onView(withId(R.id.create_game)).perform(click());

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.replace)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            String roomCode = extractText(R.id.replace);
            E2ERoomCodeSignal.publish(correlationId, roomCode);

            // A third player who is in the round when the plan is built, and who *has already
            // written an If* before vanishing. That is what makes this the interesting case: their
            // sentence is part of the round, so the If phase can complete without them, and the
            // round only jams later - at the Thens they will never write. Losing them there is
            // precisely the "four Ifs, three Thens, one If stranded" situation.
            E2ERoomFixture.addAbsentPlayerWithIf(roomCode, ABSENT_NAME, ABSENT_ID, LONG_GONE_MS,
                    "If the ghost had written this");

            onView(withId(R.id.createGame_start)).perform(click());
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.recyclerView)).check(playerCountAtLeast(3)), WAIT_TIMEOUT_MS);
            onView(withId(R.id.waitingForHost_start)).perform(click());

            // All three Ifs are in - ours, the guest's, and the ghost's - so the round advances to
            // the Thens, where it jams: the ghost is never going to write one.
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.ifQuestion)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            onView(withId(R.id.ifQuestion)).perform(typeText("If the host keeps playing"), closeSoftKeyboard());
            onView(withId(R.id.writeIf_submit)).perform(click());

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.thenAnswer)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);

            // Removing them here discards the If they wrote - the round is rebuilt for the two
            // players left, so nothing references it. Dropping one sentence is the price of not
            // stranding it, and the round staying playable is what the rest of this proves.
            EspressoWaitUtils.waitFor(() ->
                    onView(withText("Remove")).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            onView(withText("Remove")).perform(click());
            EspressoWaitUtils.waitFor(() ->
                    onView(withText("Remove " + ABSENT_NAME + "?")).check(matches(isDisplayed())),
                    WAIT_TIMEOUT_MS);
            onView(withText("Remove")).perform(click());

            E2ERoomFixture.awaitPlayerGone(roomCode, E2ERoomFixture.playerKey(ABSENT_NAME, ABSENT_ID),
                    WAIT_TIMEOUT_MS);

            finishRoundFromThen("host");
        }
    }

    private void runGuest(String correlationId) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            onView(withId(R.id.enterName)).perform(typeText("E2E-Guest"), closeSoftKeyboard());
            onView(withId(R.id.joinGame)).perform(click());
            onView(withId(R.id.enterGameCode)).check(matches(isDisplayed()));

            String roomCode = E2ERoomCodeSignal.awaitRoomCode(correlationId, WAIT_TIMEOUT_MS);
            onView(withId(R.id.enterGameCode)).perform(typeText(roomCode), closeSoftKeyboard());
            onView(withId(R.id.joinGame_start)).perform(click());

            // The guest does nothing about the removal - it is the host's action. What is being
            // proved here is that the guest is never stranded by it.
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.ifQuestion)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            onView(withId(R.id.ifQuestion)).perform(typeText("If the guest keeps playing"), closeSoftKeyboard());
            onView(withId(R.id.writeIf_submit)).perform(click());

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.thenAnswer)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            finishRoundFromThen("guest");
        }
    }

    /** Everything from the Then onward: the reading turn, and arriving at the end screen. */
    private void finishRoundFromThen(String who) {
        onView(withId(R.id.thenAnswer)).perform(typeText("Then " + who + " finishes"), closeSoftKeyboard());
        onView(withId(R.id.writeThen_submit)).perform(click());

        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.next_frag)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        playMyReadingTurn();

        // The whole point: the survivors reach the end of the round. Getting here at all means the
        // rebuilt plan had no stranded sentence and no reading slot belonging to someone who left.
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.again_ending)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
    }

    /** Reads when it is our turn; the other device's turn is simply waited out. */
    private void playMyReadingTurn() {
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.next_frag)).check(matches(isEnabled())), WAIT_TIMEOUT_MS);
        onView(withId(R.id.next_frag)).perform(click());
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.next_frag)).check(matches(withText("pass"))), WAIT_TIMEOUT_MS);
        onView(withId(R.id.next_frag)).perform(click());
    }

    private ViewAssertion playerCountAtLeast(int expected) {
        return (view, exception) -> {
            if (exception != null) {
                throw exception;
            }
            RecyclerView recyclerView = (RecyclerView) view;
            int count = recyclerView.getAdapter() == null ? 0 : recyclerView.getAdapter().getItemCount();
            if (count < expected) {
                throw new RuntimeException("player count " + count + " has not yet reached " + expected);
            }
        };
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
