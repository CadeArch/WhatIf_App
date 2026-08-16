package com.CadeMixedUpGame.phoneapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.fail;

import android.os.Bundle;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The host deliberately leaving mid-round, which ends the game for everyone.
 *
 * <p>Distinct from every other flow in this model: a host who merely <em>disconnects</em> no longer
 * ends anything - the room holds and waits for them. A host who chooses to leave is the opposite,
 * because there is nobody left to run the game. So this is the one client-initiated path that still
 * writes an {@code expiredRooms} tombstone, and the tombstone is what turns a room silently
 * vanishing into other players being told what happened.
 *
 * <p>Two real devices, because the assertion is about what the <em>guest</em> experiences.
 */
@RunWith(AndroidJUnit4.class)
public class HostEndsGameMidRoundTest {
    private static final long WAIT_TIMEOUT_MS = 40000L;

    private String role;

    @Test
    public void hostLeavingMidRoundConfirmsThenSendsEveryoneHome() {
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
            E2ERoomCodeSignal.publish(correlationId, extractText(R.id.replace));

            onView(withId(R.id.createGame_start)).perform(click());
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.waitingForHost_start)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            EspressoWaitUtils.waitFor(() -> onView(withId(R.id.recyclerView))
                    .check(TwoDevicePlayerCount.atLeast(2)), WAIT_TIMEOUT_MS);
            onView(withId(R.id.waitingForHost_start)).perform(click());

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.ifQuestion)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);

            // Leaving is destructive for everyone else, so it asks first. A guest leaving is not
            // confirmed - it only affects them - and making them tap twice to escape a room they
            // joined by mistake would be its own small cruelty.
            Espresso.pressBack();
            EspressoWaitUtils.waitFor(() ->
                    onView(withText("End the game?")).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            onView(withText("End game")).perform(click());

            // The landing screen, not the start screen: ending a room clears the local player along
            // with it, and the landing screen is where a free-play player is rebuilt. Same place the
            // guest ends up, which is the point - both sides of an ended game land somewhere usable.
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.freePlay)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
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

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.ifQuestion)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);

            // The guest does nothing. Without the tombstone the room would simply evaporate around
            // them, which is the "came back to a broken app" shape - here they are sent home instead.
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.freePlay)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
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
