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

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The remaining membership flows in the "assume they are coming back" model, one test each.
 *
 * <p>Single device throughout, with the other player written into the room by
 * {@link E2ERoomFixture}. Each of these turns on what the <em>other</em> player is doing - away and
 * returning, gone for good, removed - which is state, not behaviour, and state is exactly what a
 * fixture can produce reliably where a second device cannot.
 */
@RunWith(AndroidJUnit4.class)
public class RoomMembershipEdgeCasesTest {
    private static final long WAIT_TIMEOUT_MS = 30000L;
    private static final long LONG_GONE_MS = 10L * 60L * 1000L;
    private static final long JUST_DROPPED_MS = 5000L;
    private static final String OTHER_NAME = "guest-Ghost";
    private static final int OTHER_ID = 4242;

    private String roomId;

    @After
    public void tearDown() {
        if (roomId != null) {
            E2ERoomFixture.deleteRoom(roomId);
        }
    }

    // --- A: the premise of the whole model - they drop, and they come back ---

    @Test
    public void aPlayerWhoDropsBrieflyIsWaitedForAndTheRoundContinuesWhenTheyReturn() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            hostAGame("E2E-Host");

            // Dropped seconds ago: not removable, and the round must simply hold for them.
            E2ERoomFixture.addAbsentPlayer(roomId, OTHER_NAME, OTHER_ID, JUST_DROPPED_MS);
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.recyclerView)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);

            // No removal offered - offering one here would be the app giving up on someone who is
            // probably mid-notification.
            sleep(3000);
            assertTrue("a player who just dropped must not be offered up for removal",
                    !isShowing("Remove"));

            // ...and coming back is a non-event: they are simply present again.
            E2ERoomFixture.addPresentPlayer(roomId, OTHER_NAME, OTHER_ID, false);
            sleep(3000);
            assertTrue("returning must not leave a removal offer on screen", !isShowing("Remove"));
            onView(withId(R.id.recyclerView)).check(matches(isDisplayed()));
        }
    }

    // --- F: backgrounding mid-game must not cost anyone the room ---

    @Test
    public void backgroundingAndReturningKeepsTheRoomAndThePlayerInIt() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            hostAGame("E2E-Host");
            E2ERoomFixture.addPresentPlayer(roomId, OTHER_NAME, OTHER_ID, false);
            String room = roomId;

            // CREATED then RESUMED is a real onPause/onStop -> onStart/onResume cycle, which is what
            // an incoming call does to the Activity. The room used to be deleted out from under a
            // host for exactly this, once their heartbeat went quiet for twenty seconds.
            scenario.moveToState(Lifecycle.State.CREATED);
            sleep(4000);
            scenario.moveToState(Lifecycle.State.RESUMED);

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.recyclerView)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            E2ERoomFixture.assertRoomExists(room);
        }
    }

    // --- D: a round that drops below two players ends rather than stranding someone ---

    @Test
    public void removingTheLastOtherPlayerEndsTheRoundAndSendsTheHostHome() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            hostAGame("E2E-Host");
            E2ERoomFixture.addAbsentPlayer(roomId, OTHER_NAME, OTHER_ID, LONG_GONE_MS);

            // The round has to be under way for this to mean anything: one player sitting in the
            // lobby is just a host waiting for people to arrive, and ending the room there would be
            // wrong. It is only once a round has started that a single player is a dead game.
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.waitingForHost_start)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            onView(withId(R.id.waitingForHost_start)).perform(click());
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.ifQuestion)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);

            EspressoWaitUtils.waitFor(() ->
                    onView(withText("Remove")).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            onView(withText("Remove")).perform(click());
            EspressoWaitUtils.waitFor(() ->
                    onView(withText("Remove " + OTHER_NAME + "?")).check(matches(isDisplayed())),
                    WAIT_TIMEOUT_MS);
            onView(withText("Remove")).perform(click());

            // One player cannot make a round, so the host is sent home rather than left alone in a
            // game that can never progress.
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.freePlay)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        }
    }

    // --- E: the removed player finds out, instead of sitting in a room that ignores them ---

    @Test
    public void aPlayerWhoIsRemovedIsToldAndSentHome() {
        roomId = GameLogic_randomRoomCode();
        E2ERoomFixture.createRoomWithPresentHost(roomId, "guest-Owner", 555);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            onView(withId(R.id.enterName)).perform(typeText("E2E-Guest"), closeSoftKeyboard());
            onView(withId(R.id.joinGame)).perform(click());
            onView(withId(R.id.enterGameCode)).perform(typeText(roomId), closeSoftKeyboard());
            onView(withId(R.id.joinGame_start)).perform(click());

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.recyclerView)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);

            // The host removes us. Nothing about our own screen changes on its own - removal is a
            // flag on our player record - so without the app noticing, we would sit here in a game
            // that no longer counts us.
            E2ERoomFixture.markPlayerRemoved(roomId, E2ERoomFixture.findPlayerKeyByName(roomId, "guest-E2E-Guest"));

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.freePlay)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        }
    }

    // --- helpers ---

    private void hostAGame(String name) {
        onView(withId(R.id.freePlay)).perform(click());
        onView(withId(R.id.enterName)).perform(typeText(name), closeSoftKeyboard());
        onView(withId(R.id.create_game)).perform(click());
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.replace)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        roomId = extractText(R.id.replace);
        onView(withId(R.id.createGame_start)).perform(click());
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.recyclerView)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
    }

    private String GameLogic_randomRoomCode() {
        return com.CadeMixedUpGame.api.GameLogic.randomRoomCode(null);
    }

    private boolean isShowing(String text) {
        try {
            onView(withText(text)).check(matches(isDisplayed()));
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
