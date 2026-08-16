package com.CadeMixedUpGame.phoneapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import com.CadeMixedUpGame.api.GameLogic;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A guest escaping a room whose host has gone quiet.
 *
 * <p>This is the exit that the "assume they are coming back" model makes necessary. Nothing ends a
 * room automatically any more, which is right - a host's heartbeat freezes for over two minutes
 * from nothing worse than a locked phone, and rooms used to be deleted on exactly that signal. The
 * cost of holding instead is that a guest whose host never returns would wait forever, so there has
 * to be a door, and it has to be reachable from whatever screen they are stuck on.
 *
 * <p>The host is written directly into the database rather than run on a second device: what
 * matters is that their heartbeat is stale, and a real device would have to be launched, joined and
 * then frozen at the right moment to produce that. The stale-but-still-connected shape is exactly
 * what a locked phone produces, so this is not a simplification of the failure - it is the failure.
 */
@RunWith(AndroidJUnit4.class)
public class GuestLeavesWhenHostAwayTest {
    private static final long WAIT_TIMEOUT_MS = 20000L;
    private static final long HOST_STALE_MS = 10L * 60L * 1000L;

    private String roomId;

    @After
    public void tearDown() {
        if (roomId != null) {
            E2ERoomFixture.deleteRoom(roomId);
        }
    }

    @Test
    public void guestCanLeaveARoomWhoseHostHasGoneQuiet() {
        // Use the app's own generator, not a made-up id. Room codes are two 4-letter words joined
        // by a dash, and the join field runs input through GameFlowPolicy.normalizeRoomCodeInput,
        // which is built around exactly that shape - anything else (an id with digits tacked on)
        // is something the app would never produce and the field would never be typed.
        roomId = GameLogic.randomRoomCode(null);
        E2ERoomFixture.createRoomWithAwayHost(roomId, "guest-AwayHost", 777, HOST_STALE_MS);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            onView(withId(R.id.enterName)).perform(typeText("E2E-Guest"), closeSoftKeyboard());
            onView(withId(R.id.joinGame)).perform(click());
            onView(withId(R.id.enterGameCode)).perform(typeText(roomId), closeSoftKeyboard());
            onView(withId(R.id.joinGame_start)).perform(click());

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.recyclerView)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);

            // The pause is explained rather than silently hanging, and the way out is offered with it.
            EspressoWaitUtils.waitFor(() ->
                    onView(withText("Leave game")).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            onView(withText("Leave game")).perform(click());

            // Leaving must land them somewhere they can actually start again - and in free-play mode,
            // not showing account-only controls, which is what a half-cleaned exit produced before.
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.create_game)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            onView(withId(R.id.enterName)).check(matches(isDisplayed()));

            // Deliberately not asserting on the guest's own player node: their userID is generated
            // randomly at join time, so the key is unknowable from here. Landing back on a working
            // free-play start screen is the observable outcome that matters, and the database side
            // of leaving is already pinned by PlayerRemovalEmulatorTest.
        }
    }
}
