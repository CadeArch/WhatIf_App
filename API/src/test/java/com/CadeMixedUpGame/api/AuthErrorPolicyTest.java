package com.CadeMixedUpGame.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Targets the pure decision methods rather than the Firebase-typed adapters: a real
 * {@code FirebaseNetworkException} can't be constructed in a JVM test (its constructor calls
 * {@code android.text.TextUtils.isEmpty}), which is exactly why the classification is a boolean
 * parameter here and the {@code instanceof} checks live in a thin adapter.
 */
public class AuthErrorPolicyTest {

    /** The bug this policy exists to prevent: a connectivity blip told the player their account
     * was disabled, because anything unrecognised fell through to a catch-all that said so. */
    @Test
    public void networkFailureIsReportedAsNetworkNotAsADisabledAccount() {
        assertEquals("Network Error", AuthErrorPolicy.signInMessage(true, false, ""));
        assertEquals("Network Error", AuthErrorPolicy.signUpMessage(true, ""));
    }

    @Test
    public void networkFailureWinsEvenIfSomethingElseAlsoMatches() {
        // Precedence check: typed classification is trusted over the SDK's message wording.
        assertEquals("Network Error",
                AuthErrorPolicy.signInMessage(true, false, "The email address is badly formatted."));
    }

    @Test
    public void unrecognisedSignInFailureDoesNotBlameTheAccount() {
        assertEquals("Sign In Failed",
                AuthErrorPolicy.signInMessage(false, false, "something new the SDK started saying"));
    }

    @Test
    public void onlyAGenuinelyDisabledAccountSaysUserDisabled() {
        assertEquals("User Disabled", AuthErrorPolicy.signInMessage(false, true, ""));
        assertEquals("Sign In Failed", AuthErrorPolicy.signInMessage(false, false, ""));
    }

    @Test
    public void knownSignInFailuresKeepTheirExistingMessages() {
        assertEquals("Invalid Password", AuthErrorPolicy.signInMessage(false, false,
                "The password is invalid or the user does not have a password."));
        assertEquals("Invalid Email", AuthErrorPolicy.signInMessage(false, false,
                "There is no user record corresponding to this identifier. The user may have been deleted."));
        assertEquals("Email Badly Formatted", AuthErrorPolicy.signInMessage(false, false,
                "The email address is badly formatted."));
    }

    @Test
    public void knownSignUpFailuresKeepTheirExistingMessages() {
        assertEquals("Email Badly Formatted", AuthErrorPolicy.signUpMessage(false,
                "The email address is badly formatted."));
        assertEquals("Weak Password", AuthErrorPolicy.signUpMessage(false,
                "The given password is invalid. [ Password should be at least 6 characters ]"));
        assertEquals("Email in Use", AuthErrorPolicy.signUpMessage(false,
                "The email address is already in use by another account."));
        assertEquals("Error", AuthErrorPolicy.signUpMessage(false, "anything else"));
    }

    @Test
    public void aMissingMessageStillProducesAMessageRatherThanCrashing() {
        // Firebase can complete a failed task without attaching an exception at all.
        assertEquals("Sign In Failed", AuthErrorPolicy.signInMessage(false, false, null));
        assertEquals("Error", AuthErrorPolicy.signUpMessage(false, null));
    }
}
