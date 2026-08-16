package com.CadeMixedUpGame.api;

/**
 * A self-rescheduling repeating tick: runs the work immediately, then again every interval until
 * stopped.
 *
 * <p>{@code MainActivity} had three hand-rolled copies of this exact shape — the host heartbeat,
 * the presence pulse and the host-disconnect countdown — each a self-posting anonymous
 * {@code Runnable} plus a nullable field, an "already running?" guard, an immediate first
 * {@code run()}, and a stop method doing {@code removeCallbacks} and nulling the field. Three
 * near-identical implementations of a fiddly pattern is exactly the "find the shared shape" case,
 * and none of them could be tested where they sat.
 *
 * <p>Takes the same {@link HostDisconnectScheduler.DelayedRunner} seam as the grace-timer
 * scheduler, so the Activity keeps supplying the real {@code Handler} while tests supply a fake
 * clock. Deliberately preserves the original semantics rather than tidying them:
 * <ul>
 *   <li>the first tick runs <em>synchronously on {@link #start()}</em>, not after one interval;</li>
 *   <li>starting an already-running pulse is a no-op, not a restart;</li>
 *   <li>work that calls {@link #stop()} on itself stops cleanly and does not reschedule.</li>
 * </ul>
 */
public class RepeatingPulse {
    private final HostDisconnectScheduler.DelayedRunner delayedRunner;
    private final long intervalMs;
    private final Runnable work;
    private Runnable tick;

    public RepeatingPulse(HostDisconnectScheduler.DelayedRunner delayedRunner, long intervalMs, Runnable work) {
        this.delayedRunner = delayedRunner;
        this.intervalMs = intervalMs;
        this.work = work;
    }

    public boolean isRunning() {
        return tick != null;
    }

    /** Runs the work now and schedules it to repeat. No-op if already running. */
    public void start() {
        if (tick != null) {
            return;
        }
        tick = new Runnable() {
            @Override
            public void run() {
                work.run();
                // The work may have stopped this pulse (the heartbeat does exactly that when the
                // connection drops). Re-check rather than blindly rescheduling, or a stopped pulse
                // would come back to life for one more interval.
                if (tick != null) {
                    delayedRunner.postDelayed(tick, intervalMs);
                }
            }
        };
        tick.run();
    }

    public void stop() {
        if (tick == null) {
            return;
        }
        delayedRunner.cancel(tick);
        tick = null;
    }
}
