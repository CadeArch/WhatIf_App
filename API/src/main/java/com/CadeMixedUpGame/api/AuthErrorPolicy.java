package com.CadeMixedUpGame.api;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

/**
 * Turns a Firebase Auth failure into the short message the sign-in/sign-up screen shows.
 *
 * <p>Pulled out of {@code UserViewModel} because it is a pure decision — exception in, message out
 * — that was previously two chains of {@code if}s comparing {@code getMessage()} against
 * hard-coded English sentences from the SDK, mixed in with the Firebase calls around them and so
 * untestable without an auth emulator.
 *
 * <p>Those string comparisons are inherently brittle (the SDK is free to reword them, and it does),
 * which is exactly how "User Disabled" ended up being shown for an ordinary network blip: anything
 * unrecognised fell through to a catch-all that blamed the account. The typed checks below run
 * first for that reason, with the string matching kept only as a fallback for cases the SDK
 * doesn't give a distinct exception type for.
 */
public final class AuthErrorPolicy {
    // Exact SDK message strings, kept as a fallback where there is no distinct exception type.
    private static final String BAD_EMAIL_FORMAT = "The email address is badly formatted.";
    private static final String WEAK_PASSWORD = "The given password is invalid. [ Password should be at least 6 characters ]";
    private static final String EMAIL_IN_USE = "The email address is already in use by another account.";
    private static final String WRONG_PASSWORD = "The password is invalid or the user does not have a password.";
    private static final String NO_SUCH_USER = "There is no user record corresponding to this identifier. The user may have been deleted.";

    private AuthErrorPolicy() {
    }

    /** Adapter for callers holding a Firebase failure. Deliberately thin - it only classifies the
     * exception and hands off, so all the decision-making lives in the pure method below where it
     * can be unit tested. (Constructing a real {@code FirebaseNetworkException} in a JVM test is
     * not possible anyway: its constructor reaches into {@code android.text.TextUtils}.) */
    public static String signInMessageFor(Exception failure) {
        return signInMessage(failure instanceof FirebaseNetworkException, isDisabledUser(failure), messageOf(failure));
    }

    /** Adapter for callers holding a Firebase failure - see {@link #signInMessageFor}. */
    public static String signUpMessageFor(Exception failure) {
        return signUpMessage(failure instanceof FirebaseNetworkException, messageOf(failure));
    }

    /**
     * What to show the user for a failed sign-in.
     *
     * <p>Order matters: the typed classifications are checked before any string matching, because
     * the SDK's message wording is not a stable contract. A blip that didn't match one of the known
     * sentences used to fall through to a catch-all that told the player their account was
     * disabled.
     */
    public static String signInMessage(boolean networkFailure, boolean disabledUser, String rawMessage) {
        if (networkFailure) {
            return "Network Error";
        }
        if (disabledUser) {
            return "User Disabled";
        }
        if (WRONG_PASSWORD.equals(rawMessage)) {
            return "Invalid Password";
        }
        if (NO_SUCH_USER.equals(rawMessage)) {
            return "Invalid Email";
        }
        if (BAD_EMAIL_FORMAT.equals(rawMessage)) {
            return "Email Badly Formatted";
        }
        return "Sign In Failed";
    }

    /** What to show the user for a failed sign-up. */
    public static String signUpMessage(boolean networkFailure, String rawMessage) {
        if (networkFailure) {
            return "Network Error";
        }
        if (BAD_EMAIL_FORMAT.equals(rawMessage)) {
            return "Email Badly Formatted";
        }
        if (WEAK_PASSWORD.equals(rawMessage)) {
            return "Weak Password";
        }
        if (EMAIL_IN_USE.equals(rawMessage)) {
            return "Email in Use";
        }
        return "Error";
    }

    /** True only for an account that genuinely is disabled, never for "we couldn't tell". */
    private static boolean isDisabledUser(Exception failure) {
        return failure instanceof FirebaseAuthInvalidUserException
                && "ERROR_USER_DISABLED".equals(((FirebaseAuthInvalidUserException) failure).getErrorCode());
    }

    private static String messageOf(Exception failure) {
        return failure == null || failure.getMessage() == null ? "" : failure.getMessage();
    }
}
