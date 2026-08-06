package com.CadeMixedUpGame.phoneapp;

/**
 * Espresso has no built-in "wait for real async state" primitive - its idling-resource mechanism
 * waits for the main thread to go idle, not for a Firebase listener callback that hasn't arrived
 * yet. NavigationFlowTest never needed this (purely synchronous local navigation); Tier B's
 * cross-device flows depend on real Firebase round trips, so assertions need to be retried until
 * they pass or a timeout elapses - the same polling-with-timeout shape as this session's Tier A
 * Robolectric waitUntil helpers, just retrying an Espresso check instead of idling a shadow looper.
 */
final class EspressoWaitUtils {
    private static final long POLL_INTERVAL_MS = 200L;

    private EspressoWaitUtils() {
    }

    interface Check {
        void run();
    }

    /** Retries check.run() until it stops throwing or the timeout elapses. Rethrows the most
     * recent failure on timeout, so a genuine failure reads like a normal Espresso assertion
     * failure rather than an opaque "timed out" message.
     *
     * Catches Throwable, not just RuntimeException: Espresso's own ViewAssertions.matches(...)
     * throws AssertionError-derived failures (via AssertionErrorHandler) when a view EXISTS but a
     * property doesn't match yet (isEnabled(), withText(...), etc.) - only NoMatchingViewException
     * (the view doesn't exist in the hierarchy at all) extends RuntimeException. A first version of
     * this method only caught RuntimeException, so "wait for displayed" (NoMatchingViewException
     * while absent) retried correctly, but "wait for enabled"/"wait for this text" threw
     * AssertionError on the very first check and never actually polled - confirmed by a real
     * TwoDeviceFullGameLoopTest run where the host's own already-enabled button masked the bug
     * (its very first check happened to pass) while the guest's genuinely-not-yet-its-turn button
     * failed immediately instead of waiting for its turn to arrive. */
    static void waitFor(Check check, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Throwable lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                check.run();
                return;
            }
            catch (Throwable t) {
                lastFailure = t;
                sleepQuietly();
            }
        }
        if (lastFailure != null) {
            if (lastFailure instanceof RuntimeException) {
                throw (RuntimeException) lastFailure;
            }
            throw new RuntimeException("waitFor timed out; last failure: " + lastFailure, lastFailure);
        }
        throw new RuntimeException("waitFor timed out after " + timeoutMs + "ms with no failure recorded");
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(interrupted);
        }
    }
}
