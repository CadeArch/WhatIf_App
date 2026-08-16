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
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A removed player must not hold up voting.
 *
 * <p>Voting completes by counting votes against players, and a removed player can never cast one -
 * so counting them makes the total unreachable and the collecting-votes screen waits forever, with
 * everybody who is still playing having already voted. That is a hard hang at the very end of an
 * account-play round, after all the work of it is done.
 *
 * <p>Account play specifically, because voting only happens when every player has an account - so
 * the fabricated third player is written in as an account player too, otherwise the round would
 * skip voting altogether and prove nothing.
 */
@RunWith(AndroidJUnit4.class)
public class RemovedPlayerDoesNotBlockVotingTest {
    private static final long WAIT_TIMEOUT_MS = 40000L;
    private static final long LONG_GONE_MS = 10L * 60L * 1000L;
    private static final String ABSENT_NAME = "Ghost";
    private static final int ABSENT_ID = 4242;

    private String role;

    @Test
    public void votingCompletesAfterTheAbsentPlayerIsRemoved() {
        Bundle args = InstrumentationRegistry.getArguments();
        role = args.getString("role");
        String correlationId = args.getString("correlationId");
        String email = args.getString("email");
        String password = args.getString("password");
        if (correlationId == null || email == null || password == null) {
            fail("requires -e correlationId, -e email and -e password");
        }
        if ("host".equals(role)) {
            runHost(correlationId, email, password);
        }
        else if ("guest".equals(role)) {
            runGuest(correlationId, email, password);
        }
        else {
            fail("expected -e role host|guest, got: " + role);
        }
    }

    private void runHost(String correlationId, String email, String password) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            signIn(email, password);
            onView(withId(R.id.create_game)).perform(click());

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.replace)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
            String roomCode = extractText(R.id.replace);
            E2ERoomCodeSignal.publish(correlationId, roomCode);

            // An account player, so the round still qualifies for voting, who has written everything
            // and then vanished.
            E2ERoomFixture.addAbsentAccountPlayerWhoFinishedWriting(roomCode, ABSENT_NAME, ABSENT_ID,
                    LONG_GONE_MS, "If the ghost wrote this", "Then the ghost vanished");

            onView(withId(R.id.createGame_start)).perform(click());
            EspressoWaitUtils.waitFor(() -> onView(withId(R.id.recyclerView))
                    .check(TwoDevicePlayerCount.atLeast(3)), WAIT_TIMEOUT_MS);
            onView(withId(R.id.waitingForHost_start)).perform(click());

            writeIfAndThen("host");

            // Remove them during reading, so the round is not rebuilt and the host also has to cover
            // their reading turn on the way to the vote.
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

            readUntilVoting();
            castVote();

            // Reaching the end screen is the whole assertion: it is only reachable once every vote
            // that can be cast has been counted.
            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.again_ending)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        }
    }

    private void runGuest(String correlationId, String email, String password) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            signIn(email, password);
            onView(withId(R.id.joinGame)).perform(click());
            String roomCode = E2ERoomCodeSignal.awaitRoomCode(correlationId, WAIT_TIMEOUT_MS);
            onView(withId(R.id.enterGameCode)).perform(typeText(roomCode), closeSoftKeyboard());
            onView(withId(R.id.joinGame_start)).perform(click());

            writeIfAndThen("guest");
            readUntilVoting();
            castVote();

            EspressoWaitUtils.waitFor(() ->
                    onView(withId(R.id.again_ending)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
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

    /** Takes whatever reading turns fall to this device until the vote screen appears. */
    private void readUntilVoting() {
        long deadline = System.currentTimeMillis() + 180000L;
        while (System.currentTimeMillis() < deadline) {
            if (isShowing(R.id.vote_submit)) {
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
        fail("never reached the vote screen - reading is stuck");
    }

    private void castVote() {
        EspressoWaitUtils.waitFor(() ->
                onView(withId(R.id.vote_submit)).check(matches(isDisplayed())), WAIT_TIMEOUT_MS);
        onView(withId(R.id.potential_lbiList)).perform(clickFirstChild());
        onView(withId(R.id.vote_submit)).perform(click());
    }

    private androidx.test.espresso.ViewAction clickFirstChild() {
        return new androidx.test.espresso.ViewAction() {
            @Override
            public org.hamcrest.Matcher<View> getConstraints() {
                return androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(ViewGroup.class);
            }

            @Override
            public String getDescription() {
                return "click the first vote option";
            }

            @Override
            public void perform(androidx.test.espresso.UiController uiController, View view) {
                ViewGroup group = (ViewGroup) view;
                if (group.getChildCount() == 0) {
                    throw new RuntimeException("no vote options to choose from");
                }
                group.getChildAt(0).performClick();
                uiController.loopMainThreadUntilIdle();
            }
        };
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
