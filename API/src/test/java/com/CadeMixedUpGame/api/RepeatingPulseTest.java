package com.CadeMixedUpGame.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Pins the semantics the three hand-rolled pulses in {@code MainActivity} had, so replacing them
 * with {@link RepeatingPulse} preserves behavior rather than quietly "improving" it — an immediate
 * first tick and a self-stopping tick both matter to the heartbeat.
 */
public class RepeatingPulseTest {
    private static final long INTERVAL_MS = 1000L;

    /** Stands in for the Activity's Handler: records what was posted so a test can advance time
     * deliberately instead of sleeping. */
    private static final class FakeRunner implements HostDisconnectScheduler.DelayedRunner {
        private final List<Runnable> pending = new ArrayList<Runnable>();
        private final List<Long> delays = new ArrayList<Long>();

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            pending.add(runnable);
            delays.add(delayMs);
        }

        @Override
        public void cancel(Runnable runnable) {
            int index = pending.indexOf(runnable);
            while (index >= 0) {
                pending.remove(index);
                delays.remove(index);
                index = pending.indexOf(runnable);
            }
        }

        /** Fires everything currently scheduled, once. */
        void advance() {
            List<Runnable> due = new ArrayList<Runnable>(pending);
            pending.clear();
            delays.clear();
            for (Runnable runnable : due) {
                runnable.run();
            }
        }

        int scheduledCount() {
            return pending.size();
        }

        long lastDelay() {
            return delays.isEmpty() ? -1L : delays.get(delays.size() - 1);
        }
    }

    private FakeRunner runner;
    private int ticks;

    @Before
    public void setUp() {
        runner = new FakeRunner();
        ticks = 0;
    }

    @Test
    public void firstTickHappensImmediatelyOnStartNotAfterOneInterval() {
        RepeatingPulse pulse = new RepeatingPulse(runner, INTERVAL_MS, () -> ticks++);

        pulse.start();

        assertEquals("the heartbeat must land as soon as it starts, not a second later", 1, ticks);
        assertEquals("and the next one is queued", 1, runner.scheduledCount());
        assertEquals(INTERVAL_MS, runner.lastDelay());
    }

    @Test
    public void keepsTickingOnEveryInterval() {
        RepeatingPulse pulse = new RepeatingPulse(runner, INTERVAL_MS, () -> ticks++);
        pulse.start();

        runner.advance();
        runner.advance();

        assertEquals(3, ticks);
        assertTrue(pulse.isRunning());
    }

    @Test
    public void stoppingPreventsAnyFurtherTicks() {
        RepeatingPulse pulse = new RepeatingPulse(runner, INTERVAL_MS, () -> ticks++);
        pulse.start();

        pulse.stop();
        runner.advance();

        assertEquals("no tick after stop", 1, ticks);
        assertEquals(0, runner.scheduledCount());
        assertFalse(pulse.isRunning());
    }

    @Test
    public void workThatStopsItsOwnPulseDoesNotGetRescheduled() {
        // Exactly what the host heartbeat does: it calls stopHostHeartbeat() from inside the tick
        // when the connection has dropped. If the pulse rescheduled regardless, a stopped heartbeat
        // would fire once more and write presence for a device that is offline.
        final RepeatingPulse[] holder = new RepeatingPulse[1];
        holder[0] = new RepeatingPulse(runner, INTERVAL_MS, () -> {
            ticks++;
            holder[0].stop();
        });

        holder[0].start();

        assertEquals(1, ticks);
        assertEquals("a self-stopping tick must not leave anything queued", 0, runner.scheduledCount());
        assertFalse(holder[0].isRunning());

        runner.advance();
        assertEquals(1, ticks);
    }

    @Test
    public void startingAnAlreadyRunningPulseIsANoOpRatherThanASecondTicker() {
        // The old code guarded on `runnable != null` for this reason: startHostHeartbeatIfNeeded
        // gets called on every connection-state change, and two live tickers would double the
        // write rate.
        RepeatingPulse pulse = new RepeatingPulse(runner, INTERVAL_MS, () -> ticks++);
        pulse.start();
        pulse.start();

        assertEquals("second start must not fire the work again", 1, ticks);
        assertEquals("and must not queue a second ticker", 1, runner.scheduledCount());

        runner.advance();
        assertEquals("still exactly one ticker running", 2, ticks);
    }

    @Test
    public void canBeRestartedAfterBeingStopped() {
        RepeatingPulse pulse = new RepeatingPulse(runner, INTERVAL_MS, () -> ticks++);
        pulse.start();
        pulse.stop();

        pulse.start();

        assertEquals(2, ticks);
        assertTrue(pulse.isRunning());
    }

    @Test
    public void stoppingWhenNeverStartedIsHarmless() {
        RepeatingPulse pulse = new RepeatingPulse(runner, INTERVAL_MS, () -> ticks++);

        pulse.stop();

        assertEquals(0, ticks);
        assertFalse(pulse.isRunning());
    }
}
