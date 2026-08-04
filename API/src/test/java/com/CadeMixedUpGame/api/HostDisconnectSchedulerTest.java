package com.CadeMixedUpGame.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class HostDisconnectSchedulerTest {

    /** Records every postDelayed call and lets the test fire the most recently scheduled
     * runnable on demand instead of actually waiting - this is what makes ~24s of real timer
     * behavior testable in milliseconds. */
    private static class FakeDelayedRunner implements HostDisconnectScheduler.DelayedRunner {
        List<Long> scheduledDelays = new ArrayList<>();
        Runnable scheduled;
        boolean cancelled;

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            scheduled = runnable;
            scheduledDelays.add(delayMs);
            cancelled = false;
        }

        @Override
        public void cancel(Runnable runnable) {
            if (runnable == scheduled) {
                cancelled = true;
            }
        }

        void fireIfNotCancelled() {
            if (scheduled != null && !cancelled) {
                scheduled.run();
            }
        }
    }

    private FakeDelayedRunner delayedRunner;
    private List<String> expiredReasons;
    private HostDisconnectScheduler scheduler;

    @Before
    public void setUp() {
        delayedRunner = new FakeDelayedRunner();
        expiredReasons = new ArrayList<>();
        scheduler = new HostDisconnectScheduler(delayedRunner, expiredReasons::add);
    }

    @Test
    public void disconnectTimestampWithNoElapsedTimeSchedulesFullGracePeriod() {
        long now = 1_000_000L;
        scheduler.scheduleForDisconnectTimestamp(now, now);

        assertEquals(1, delayedRunner.scheduledDelays.size());
        assertEquals(GameFlowPolicy.CONNECTION_GRACE_MS, (long) delayedRunner.scheduledDelays.get(0));
    }

    @Test
    public void disconnectTimestampWithElapsedTimeSchedulesRemainder() {
        long disconnectedAt = 1_000_000L;
        long now = disconnectedAt + 5_000L;
        scheduler.scheduleForDisconnectTimestamp(disconnectedAt, now);

        assertEquals(GameFlowPolicy.CONNECTION_GRACE_MS - 5_000L, (long) delayedRunner.scheduledDelays.get(0));
    }

    @Test
    public void disconnectTimestampFiringInvokesExpireExactlyOnce() {
        long now = 1_000_000L;
        scheduler.scheduleForDisconnectTimestamp(now, now);
        delayedRunner.fireIfNotCancelled();

        assertEquals(1, expiredReasons.size());
        assertEquals("timer expired", expiredReasons.get(0));
        assertTrue(scheduler.isExpired());
    }

    /** The "hard kill" path: only heartbeat staleness detects it (no onDisconnect() write in
     * time), so this is nested - the CLIENT_HOME_AFTER_HOST_EXPIRE_DELAY_MS buffer is scheduled
     * only once the first (heartbeat-staleness) stage actually fires, matching the original
     * MainActivity code exactly. */
    @Test
    public void heartbeatExpirationIsTwoStageNestedDelay() {
        long now = 1_000_000L;
        scheduler.scheduleForHeartbeat(now, now); // lastSeenAt == now: fully stale immediately once grace elapses

        assertEquals(1, delayedRunner.scheduledDelays.size());
        assertEquals(GameFlowPolicy.CONNECTION_GRACE_MS, (long) delayedRunner.scheduledDelays.get(0));
        assertFalse("must not fire the listener until both stages complete", expiredReasons.size() > 0);

        delayedRunner.fireIfNotCancelled(); // first stage fires -> schedules the second stage
        assertEquals(2, delayedRunner.scheduledDelays.size());
        assertEquals(GameFlowPolicy.CLIENT_HOME_AFTER_HOST_EXPIRE_DELAY_MS, (long) delayedRunner.scheduledDelays.get(1));
        assertTrue("second stage must not have fired yet", expiredReasons.isEmpty());

        delayedRunner.fireIfNotCancelled(); // second stage fires -> now it actually expires
        assertEquals(1, expiredReasons.size());
        assertEquals("host heartbeat expired", expiredReasons.get(0));
        assertTrue(scheduler.isExpired());
    }

    @Test
    public void cancelBeforeFiringPreventsExpiry() {
        long now = 1_000_000L;
        scheduler.scheduleForDisconnectTimestamp(now, now);
        scheduler.cancel();
        delayedRunner.fireIfNotCancelled();

        assertTrue(expiredReasons.isEmpty());
        assertFalse(scheduler.isExpired());
    }

    /** Covers "a reconnect before the deadline cancels the pending expiry" from the test plan. */
    @Test
    public void reconnectBeforeDeadlineCancelsPendingExpiry() {
        long now = 1_000_000L;
        scheduler.scheduleForDisconnectTimestamp(now, now);

        // host reconnects well before the grace period elapses
        assertFalse(scheduler.isPastDeadline(now + 1_000L));
        scheduler.reset();
        delayedRunner.fireIfNotCancelled();

        assertTrue(expiredReasons.isEmpty());
        assertFalse(scheduler.isExpired());
    }

    @Test
    public void isPastDeadlineReflectsElapsedTimeWithoutFiring() {
        long now = 1_000_000L;
        scheduler.scheduleForDisconnectTimestamp(now, now);

        assertFalse(scheduler.isPastDeadline(now + GameFlowPolicy.CONNECTION_GRACE_MS - 1));
        assertTrue(scheduler.isPastDeadline(now + GameFlowPolicy.CONNECTION_GRACE_MS));
        // isPastDeadline is a pure query - it must not itself trigger the listener.
        assertTrue(expiredReasons.isEmpty());
    }

    @Test
    public void reschedulingClearsPreviousExpiredState() {
        long now = 1_000_000L;
        scheduler.scheduleForDisconnectTimestamp(now, now);
        delayedRunner.fireIfNotCancelled();
        assertTrue(scheduler.isExpired());

        scheduler.scheduleForDisconnectTimestamp(now + 100_000L, now + 100_000L);
        assertFalse("starting a fresh grace period should clear the previous expiry", scheduler.isExpired());
    }
}
