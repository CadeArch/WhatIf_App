package com.CadeMixedUpGame.phoneapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.not;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Solo-reachable navigation/validation flows only — the game disallows single-player matches, so
 * anything past "create/join a room" needs a second simulated player and isn't covered here (see
 * README's testing roadmap for the planned two-device harness). Espresso interacts with real
 * views on a real Activity, so these exercise actual navigation code (Utils.navigateToFragment
 * and friends), not fakes.
 *
 * Needs a device/emulator on a *stable* Android release (API 34-36) — a preview API level ahead
 * of the installed Espresso release fails instrumented test startup with
 * "NoSuchMethodException: InputManager.getInstance" (an Espresso/OS-preview compatibility gap,
 * confirmed by running this same suite on a preview API 37 emulator vs. a stable API 35 one).
 */
@RunWith(AndroidJUnit4.class)
public class NavigationFlowTest {

    @Test
    public void launchShowsFirstFragWithAccountAndFreePlayButtons() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.accountPlay)).check(matches(isDisplayed()));
            onView(withId(R.id.freePlay)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void freePlayNavigatesToStartScreen() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            onView(withId(R.id.enterName)).check(matches(isDisplayed()));
            onView(withId(R.id.create_game)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void createGameWithEmptyNameShowsValidationError() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            onView(withId(R.id.enterName)).perform(clearText(), closeSoftKeyboard());
            onView(withId(R.id.create_game)).perform(click());
            onView(withId(R.id.enterName)).check(matches(hasErrorText("Name required")));
        }
    }

    @Test
    public void joinGameWithEmptyNameShowsValidationError() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            onView(withId(R.id.enterName)).perform(clearText(), closeSoftKeyboard());
            onView(withId(R.id.joinGame)).perform(click());
            onView(withId(R.id.enterName)).check(matches(hasErrorText("Name required")));
        }
    }

    /**
     * Free Play mode always keeps the name editable (StartFragment.applyUserMode only swaps to
     * the read-only displayName TextView for account-mode users) — this asserts that stays true
     * rather than asserting displayName appears, which only happens in account mode.
     */
    @Test
    public void freePlayKeepsNameEditableAfterTyping() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            onView(withId(R.id.enterName)).perform(typeText("Tester"), closeSoftKeyboard());
            onView(withId(R.id.enterName)).check(matches(isDisplayed()));
            onView(withId(R.id.displayName)).check(matches(not(isDisplayed())));
        }
    }

    /**
     * Regression test for the portrait/landscape work: recreating the Activity (what happens on
     * rotation) must not crash or lose the current screen, and must not silently re-add a second
     * copy of the starting fragment on top of the current one.
     */
    @Test
    public void activityRecreateAfterNavigatingSurvivesWithoutCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.freePlay)).perform(click());
            onView(withId(R.id.enterName)).check(matches(isDisplayed()));

            scenario.recreate();

            onView(withId(R.id.enterName)).check(matches(isDisplayed()));
        }
    }
}
