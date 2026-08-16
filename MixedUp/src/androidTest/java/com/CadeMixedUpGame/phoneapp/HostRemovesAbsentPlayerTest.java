package com.CadeMixedUpGame.phoneapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The host removing a player who has gone quiet, driven entirely through the real UI.
 *
 * <p>Single device on purpose. The subject is the host's experience - a control appearing when
 * someone has been away long enough, a confirmation, and the round carrying on without them - and
 * the absent player only has to *exist and be absent*. {@link E2ERoomFixture} writes them straight
 * into the room, which is deterministic, where killing a second device at the right moment is not.
 *
 * <p>No threshold is shortened for this test. The player is written with a {@code disconnectedAt}
 * from ten minutes ago, so the real ninety-second rule in {@code GameFlowPolicy.canKickPlayer} is
 * genuinely satisfied by the production code and its own clock.
 */
@RunWith(AndroidJUnit4.class)
public class HostRemovesAbsentPlayerTest {
    private static final long WAIT_TIMEOUT_MS = 20000L;
    private static final long LONG_GONE_MS = 10L * 60L * 1000L;
    private static final String ABSENT_NAME = "guest-Ghost";
    private static final int ABSENT_ID = 4242;

    private String roomId;

    @After
    public void tearDown() {
        if (roomId != null) {
            E2ERoomFixture.deleteRoom(roomId);
        }
    }

    @Test
    public void hostRemovesAPlayerWhoHasBeenAwayAndTheRoundCarriesOn() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            onView(withId(R.id.enterName)).perform(typeText("E2E-Host"), closeSoftKeyboard());
            onView(withId(R.id.create_game)).perform(click());

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.replace)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            roomId = extractText(R.id.replace);
            assertTrue("room code should be readable", roomId != null && roomId.length() > 0);

            onView(withId(R.id.createGame_start)).perform(click());
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.recyclerView)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);

            // A second player who joined and then vanished a long time ago.
            E2ERoomFixture.addAbsentPlayer(roomId, ABSENT_NAME, ABSENT_ID, LONG_GONE_MS);

            // The host is offered the control from wherever they are - it lives in the Activity's
            // chrome rather than in this screen's layout, which is what makes it work on all of them.
            EspressoWaitUtils.waitFor(() ->
                    onView(withText("Remove")).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            onView(withText("Remove")).perform(click());

            // Removing someone is destructive for them, so it is confirmed rather than immediate.
            EspressoWaitUtils.waitFor(() ->
                    onView(withText("Remove " + ABSENT_NAME + "?")).check(matches(isDisplayed())),
                    WAIT_TIMEOUT_MS);
            onView(withText("Remove")).perform(click());

            E2ERoomFixture.awaitPlayerGone(roomId, E2ERoomFixture.playerKey(ABSENT_NAME, ABSENT_ID),
                    WAIT_TIMEOUT_MS);

            // The host is still in their game - removing someone must not take the room down with
            // them. Two players remain (the host and nobody else is required here), so the room
            // stays open on the waiting screen.
            onView(withId(R.id.recyclerView)).check(matches(isDisplayed()));
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
}
