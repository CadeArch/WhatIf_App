package com.CadeMixedUpGame.phoneapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
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
 * First Tier B test: two REAL devices/emulators, each running the actual app UI, both pointed at
 * the same local Firebase Emulator Suite (see README.md / CHANGELOG.md's Tier B design notes).
 * Deliberately minimal scope - proves the whole cross-device harness works for real (two real
 * emulators, real layouts, a real room-code handoff between two separate instrumented processes)
 * before building more scenarios on top of it, the same "prove the harness first" discipline used
 * for Tier A this session.
 *
 * Not runnable standalone via a normal `connectedDebugAndroidTest` invocation - it needs a role
 * ("host" or "guest") and a correlationId, both passed as instrumentation arguments by
 * scripts/run-tier-b.ps1, which launches both roles at the same time (see E2ERoomCodeSignal for
 * why - the room-code handoff goes through a shared Firebase location keyed by correlationId, not
 * a logcat signal the orchestration script has to wait on before even starting the guest process).
 * Requires the app to be installed with -PuseFirebaseEmulator=true on both devices and the local
 * Firebase Emulator Suite already running (`firebase emulators:start --only database,auth`) - the
 * orchestration script checks this before launching either device.
 */
@RunWith(AndroidJUnit4.class)
public class TwoDeviceMultiplayerTest {
    private static final long WAIT_TIMEOUT_MS = 20_000L;

    @Test
    public void hostAndGuestSeeEachOtherInTheSameRealRoom() {
        Bundle args = InstrumentationRegistry.getArguments();
        String role = args.getString("role");
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
            onView(withId(R.id.enterName)).perform(typeText("E2E-Host"), closeSoftKeyboard());
            onView(withId(R.id.create_game)).perform(click());

            // CreateGameFrag's "replace" TextView shows the already-created real room code as
            // soon as room creation + host player push both complete (StartFragment.createReservedRoom).
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.replace)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            String roomCode = extractText(R.id.replace);
            assertTrue("extracted room code must be non-empty", roomCode != null && roomCode.length() > 0);

            E2ERoomCodeSignal.publish(correlationId, roomCode);

            onView(withId(R.id.createGame_start)).perform(click());

            EspressoWaitUtils.waitFor(() -> onView(withId(R.id.recyclerView)).check(playerCountAtLeast(2)), WAIT_TIMEOUT_MS);
        }
    }

    private void runGuest(String correlationId) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            onView(withId(R.id.enterName)).perform(typeText("E2E-Guest"), closeSoftKeyboard());
            onView(withId(R.id.joinGame)).perform(click());
            onView(withId(R.id.enterGameCode)).check(matches(isDisplayed()));

            // Everything above runs immediately in parallel with the host's own room-creation
            // steps - this wait is the only point that actually depends on the host being ahead.
            String roomCode = E2ERoomCodeSignal.awaitRoomCode(correlationId, WAIT_TIMEOUT_MS);

            onView(withId(R.id.enterGameCode)).perform(typeText(roomCode), closeSoftKeyboard());
            onView(withId(R.id.joinGame_start)).perform(click());

            EspressoWaitUtils.waitFor(() -> onView(withId(R.id.recyclerView)).check(playerCountAtLeast(2)), WAIT_TIMEOUT_MS);
        }
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

    /** No espresso-contrib dependency needed for a plain RecyclerView adapter item-count check.
     * Throws RuntimeException (not AssertionError, which EspressoWaitUtils.waitFor's catch would
     * not retry on) while the count is still too low. */
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
