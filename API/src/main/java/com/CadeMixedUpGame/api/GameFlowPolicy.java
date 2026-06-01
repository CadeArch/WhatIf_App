package com.CadeMixedUpGame.api;

import com.CadeMixedUpGame.api.models.User;

import java.util.List;

public final class GameFlowPolicy {
    public static final long CONNECTION_GRACE_MS = 20000L;
    public static final long HOST_HEARTBEAT_INTERVAL_MS = 1000L;
    public static final long CLIENT_HOME_AFTER_HOST_EXPIRE_DELAY_MS = 4000L;
    public static final long EXPIRED_ROOM_TOMBSTONE_TTL_MS = 24L * 60L * 60L * 1000L;

    private GameFlowPolicy() {
    }

    public static int countFinishedIfs(List<User> players) {
        int count = 0;
        if (players == null) {
            return count;
        }
        for (User player : players) {
            if (player != null && Boolean.TRUE.equals(player.ifFinished)) {
                count += 1;
            }
        }
        return count;
    }

    public static int countFinishedThens(List<User> players) {
        int count = 0;
        if (players == null) {
            return count;
        }
        for (User player : players) {
            if (player != null && Boolean.TRUE.equals(player.thenFinished)) {
                count += 1;
            }
        }
        return count;
    }

    public static boolean allPlayersFinishedIfs(List<User> players) {
        return players != null && players.size() > 0 && allPlayersConnected(players) && countFinishedIfs(players) == players.size();
    }

    public static boolean allPlayersFinishedThens(List<User> players) {
        return players != null && players.size() > 0 && allPlayersConnected(players) && countFinishedThens(players) == players.size();
    }

    public static boolean allPlayersConnected(List<User> players) {
        if (players == null || players.size() == 0) {
            return false;
        }
        for (User player : players) {
            if (player == null || Boolean.FALSE.equals(player.connected)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allPlayersHaveAccounts(List<User> players) {
        if (players == null || players.size() == 0) {
            return false;
        }
        for (User player : players) {
            if (player == null || !Boolean.TRUE.equals(player.accountPlay)) {
                return false;
            }
        }
        return true;
    }

    public static boolean finalReaderPassed(int nextReaderIndex, int readOrderSize) {
        return readOrderSize > 0 && nextReaderIndex >= readOrderSize;
    }

    public static long millisUntilHostHeartbeatExpires(long nowMs, long lastSeenAtMs) {
        if (lastSeenAtMs <= 0L) {
            return CONNECTION_GRACE_MS;
        }
        return Math.max(0L, CONNECTION_GRACE_MS - Math.max(0L, nowMs - lastSeenAtMs));
    }

    public static boolean hostHeartbeatExpired(long nowMs, long lastSeenAtMs) {
        return millisUntilHostHeartbeatExpires(nowMs, lastSeenAtMs) == 0L;
    }

    public static String normalizeRoomCodeInput(String roomCode) {
        return roomCode == null ? "" : roomCode.trim();
    }
}
