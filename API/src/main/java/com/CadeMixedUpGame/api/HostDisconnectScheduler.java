package com.CadeMixedUpGame.api;

/**
 * The grace-timer/heartbeat-expiry scheduling decisions previously inlined in
 * {@code MainActivity} (host-disconnect handling) — extracted so the timing math can be unit
 * tested with a fake clock instead of requiring real ~20-24s wall-clock waits. This class owns
 * only "when should the room be considered expired given a disconnect/heartbeat timestamp";
 * {@code MainActivity} still owns every side effect (Firebase writes, navigation, banners).
 *
 * Faithfully preserves one existing quirk from the original {@code MainActivity} code: the
 * heartbeat path schedules its final {@code CLIENT_HOME_AFTER_HOST_EXPIRE_DELAY_MS} stage as a
 * *nested* delayed call once the first stage fires, so {@link #cancel()} can no longer stop it
 * once that first stage has already fired (matching the original's behavior exactly, not
 * "fixing" it as part of this extraction).
 */
public class HostDisconnectScheduler {

    public interface DelayedRunner {
        void postDelayed(Runnable runnable, long delayMs);

        void cancel(Runnable runnable);
    }

    public interface Listener {
        void onExpire(String reason);
    }

    private final DelayedRunner delayedRunner;
    private final Listener listener;
    private Runnable pendingRunnable;
    private long pendingDeadlineMs = 0L;
    private boolean expired = false;

    public HostDisconnectScheduler(DelayedRunner delayedRunner, Listener listener) {
        this.delayedRunner = delayedRunner;
        this.listener = listener;
    }

    /** Mirrors the old scheduleHostDisconnect: the host's own connection/player node was marked
     * disconnected at disconnectedAtMs; fire after the remainder of CONNECTION_GRACE_MS. */
    public void scheduleForDisconnectTimestamp(long disconnectedAtMs, long nowMs) {
        reset();
        long elapsedMs = Math.max(0L, nowMs - disconnectedAtMs);
        long remainingMs = Math.max(0L, GameFlowPolicy.CONNECTION_GRACE_MS - elapsedMs);
        pendingDeadlineMs = nowMs + remainingMs;
        pendingRunnable = () -> fireExpired("timer expired");
        delayedRunner.postDelayed(pendingRunnable, remainingMs);
    }

    /** Mirrors the old scheduleHostHeartbeatExpiration. Caller is responsible for the "only
     * guests schedule this" check (this class has no notion of local host/guest status). */
    public void scheduleForHeartbeat(long lastSeenAtMs, long nowMs) {
        reset();
        long remainingMs = GameFlowPolicy.millisUntilHostHeartbeatExpires(nowMs, lastSeenAtMs);
        pendingDeadlineMs = nowMs + remainingMs;
        pendingRunnable = () -> delayedRunner.postDelayed(
                () -> fireExpired("host heartbeat expired"),
                GameFlowPolicy.CLIENT_HOME_AFTER_HOST_EXPIRE_DELAY_MS);
        delayedRunner.postDelayed(pendingRunnable, remainingMs);
    }

    private void fireExpired(String reason) {
        expired = true;
        listener.onExpire(reason);
    }

    /** Cancels any pending timer and clears the deadline, but leaves {@link #isExpired()}
     * untouched (matches the original cancelPendingHostDisconnect(), which never touched that
     * flag either — only callers that also want it cleared call {@link #reset()}). */
    public void cancel() {
        if (pendingRunnable != null) {
            delayedRunner.cancel(pendingRunnable);
            pendingRunnable = null;
        }
        pendingDeadlineMs = 0L;
    }

    /** Full stop: cancel plus clear the expired flag (matches the original's cleanup in
     * sendPlayerHomeAfterHostDisconnect). */
    public void reset() {
        cancel();
        expired = false;
    }

    public boolean isExpired() {
        return expired;
    }

    public boolean isPastDeadline(long nowMs) {
        return pendingDeadlineMs > 0L && nowMs >= pendingDeadlineMs;
    }
}
