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

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.ViewAssertion;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A player removed <b>during reading</b>, where the host has to take the turn they left behind.
 *
 * <p>The sibling test ({@code RemovePlayerMidGameTest}) removes someone before reading starts, which
 * rebuilds the round and makes their slot disappear. This is the other half, and the more fragile
 * one: once reading is under way the round is deliberately <em>not</em> rebuilt, because re-pairing
 * would reshuffle sentences players have already heard. So the departed player keeps their place in
 * the read order - and reading advances by matching the active reader's key, which means their slot
 * is a dead stop that no one else can move past.
 *
 * <p>The host covering that turn is what keeps the round alive. Without it this test hangs at the
 * ghost's turn and both devices time out, which is exactly the failure it exists to catch.
 *
 * <p>The absent player is written in having already finished their If <em>and</em> Then, so the
 * round genuinely reaches reading with three readers in the order.
 */
@RunWith(AndroidJUnit4.class)
public class HostCoversReadingTurnTest {
    private static final long WAIT_TIMEOUT_MS = 40000L;
    private static final long LONG_GONE_MS = 10L * 60L * 1000L;
    private static final String ABSENT_NAME = "guest-Ghost";
    private static final int ABSENT_ID = 4242;
    /**
     * Whole-phase budget, and it has to be generous. A device starts this loop as soon as it has
     * written its Then, which on the guest is well before the host has finished the removal dance -
     * so most of this budget is spent waiting for the *other* device, not reading. An earlier,
     * tighter value expired on the guest seconds before its turn arrived, leaving it sitting
     * passively at the exact moment it needed to click.
     */
    private static final long READING_BUDGET_MS = 180000L;

    private String role;

    @Test
    public void hostTakesOverTheReadingTurnOfAPlayerRemovedDuringReading() {
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

            // Already wrote everything, then vanished - so the round reaches reading with them in
            // the read order, which is the only way to exercise the orphaned-turn path.
            E2ERoomFixture.addAbsentPlayerWhoFinishedWriting(roomCode, ABSENT_NAME, ABSENT_ID,
                    LONG_GONE_MS, "If the ghost wrote this", "Then the ghost vanished");

            onView(withId(R.id.createGame_start)).perform(click());
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.recyclerView)).check(playerCountAtLeast(3)), WAIT_TIMEOUT_MS);
            onView(withId(R.id.waitingForHost_start)).perform(click());

            writeIfAndThen("host");

            // Reading has started, so removal here must NOT rebuild the round.
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.next_frag)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            EspressoWaitUtils.waitFor(() ->
                    onView(withText("Remove")).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            onView(withText("Remove")).perform(click());
            EspressoWaitUtils.waitFor(() ->
                    onView(withText("Remove " + ABSENT_NAME + "?")).check(matches(isDisplayed())),
                    WAIT_TIMEOUT_MS);
            onView(withText("Remove")).perform(click());
            E2ERoomFixture.awaitPlayerGone(roomCode, E2ERoomFixture.playerKey(ABSENT_NAME, ABSENT_ID),
                    WAIT_TIMEOUT_MS);

            // The host now has to take two turns: its own, and the one nobody is left to take.
            readUntilRoundEnds();
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

            writeIfAndThen("guest");

            // The guest takes only its own turn. Reaching the end screen means the ghost's turn was
            // dealt with by someone - if it had simply stalled, this is where the guest would hang.
            readUntilRoundEnds();
        }
    }

    private void writeIfAndThen(String who) {
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.ifQuestion)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        onView(withId(R.id.ifQuestion)).perform(typeText("If " + who + " plays on"), closeSoftKeyboard());
        onView(withId(R.id.writeIf_submit)).perform(click());

        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.thenAnswer)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        onView(withId(R.id.thenAnswer)).perform(typeText("Then " + who + " finishes"), closeSoftKeyboard());
        onView(withId(R.id.writeThen_submit)).perform(click());
    }

    /**
     * Takes whatever reading turns fall to this device until the round ends.
     *
     * <p>Time-bounded rather than counted, and that distinction cost a run: a fixed number of
     * iterations is really a budget for *waiting on the other device*, not for this one's own
     * turns. With three readers - one of them a ghost the host has to cover - a device can sit idle
     * through several turns before it gets one, and a step count that looked generous ran out while
     * the round was progressing perfectly well.
     */
    private void readUntilRoundEnds() {
        long deadline = System.currentTimeMillis() + READING_BUDGET_MS;
        while (System.currentTimeMillis() < deadline) {
            if (isShowing(R.id.again_ending)) {
                return;
            }
            if (isTappable(R.id.next_frag)) {
                onView(withId(R.id.next_frag)).perform(click());
                EspressoWaitUtils.waitFor(() ->
                        onView(withId(R.id.next_frag)).check(matches(withText("pass"))), WAIT_TIMEOUT_MS);
                onView(withId(R.id.next_frag)).perform(click());
            }
            else {
                sleep(500);
            }
        }
        fail("reading never finished within " + (READING_BUDGET_MS / 1000) + "s - the round is stuck, "
                + "which is what this test exists to catch (an orphaned reading slot nobody can take)");
    }

    private boolean isShowing(int viewId) {
        try {
            onView(withId(viewId)).check(matches(isDisplayed()));
            return true;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isTappable(int viewId) {
        try {
            onView(withId(viewId)).check(matches(isEnabled()));
            return true;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
