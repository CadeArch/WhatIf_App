package com.CadeMixedUpGame.api;

import com.CadeMixedUpGame.api.models.User;

import java.util.List;

public final class GameFlowPolicy {
    public static final long CONNECTION_GRACE_MS = 20000L;
    public static final long HOST_HEARTBEAT_INTERVAL_MS = 1000L;
    public static final long CLIENT_HOME_AFTER_HOST_EXPIRE_DELAY_MS = 4000L;
    public static final long EXPIRED_ROOM_TOMBSTONE_TTL_MS = 24L * 60L * 60L * 1000L;
    /** How long the host waits after writing replayState="no" before deleting the room, so the
     * other players' EndFrag listeners actually observe that value. Deleting immediately in the
     * write's completion callback can destroy the room before the signal propagates - the other
     * clients then only ever see the room vanish, never the "no", and sit on the end screen with
     * no way off it. Seen consistently against the local emulator, where the round trip is fast
     * enough to lose the race almost every time; the same race exists in production, just rarer. */
    public static final long HOST_HOME_ROOM_DELETE_DELAY_MS = 1500L;
    /** How long a room can go with no sign of its host before any client may delete it.
     *
     * <p>Sized against the heartbeat, not against how long a game lasts: the host writes
     * hostConnection/lastSeenAt every HOST_HEARTBEAT_INTERVAL_MS (one second), and the app already
     * ends the game after CONNECTION_GRACE_MS (twenty seconds) without the host, so a room that has
     * missed half an hour of heartbeats is dead many times over - this is ~90x the app's own
     * give-up threshold. An earlier six-hour value was picked as though a live room might
     * legitimately go hours between signs of life, which never happens here; it just meant a day's
     * worth of finished games sat around looking recent. */
    public static final long ABANDONED_ROOM_TTL_MS = 30L * 60L * 1000L;
    /** Minimum gap between maintenance sweeps across the whole app, not per device - see
     * RoomViewModel.runDailyMaintenanceIfDue. Sweeping is a whole-table read, so letting every
     * client do it on every launch would be O(users x rooms) work and have them all racing to
     * delete the same rooms. */
    public static final long MAINTENANCE_SWEEP_INTERVAL_MS = 24L * 60L * 60L * 1000L;

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

    /** Room codes are two 4-letter words joined by a dash (see GameLogic.randomRoomCode) - the
     * dash is purely cosmetic for readability, so a joiner shouldn't have to type it exactly.
     * Strips everything but letters, lowercases, and reinserts the canonical dash once there are
     * exactly 8 letters ("wolflake", "WOLF LAKE", "wolf_lake" all resolve to "wolf-lake", matching
     * whatever the room was actually created/stored as). Anything else (wrong letter count, empty)
     * is passed through lowercased/trimmed and left to fail the room lookup naturally. */
    public static String normalizeRoomCodeInput(String roomCode) {
        if (roomCode == null) {
            return "";
        }
        String lower = roomCode.trim().toLowerCase();
        String lettersOnly = lower.replaceAll("[^a-z]", "");
        if (lettersOnly.length() == 8) {
            return lettersOnly.substring(0, 4) + "-" + lettersOnly.substring(4);
        }
        return lower;
    }

    /** Whether enough time has passed since the last maintenance sweep for the next launching
     * client to claim one. Evaluated inside a transaction against a value shared by every client,
     * so exactly one device per interval wins the claim - whichever happens to launch first. */
    public static boolean isMaintenanceSweepDue(Long lastSweepAt, long nowMs) {
        if (lastSweepAt == null || lastSweepAt <= 0L) {
            return true;
        }
        // A clock-skewed device could otherwise park lastSweepAt far in the future and block
        // sweeping until that time arrives.
        if (lastSweepAt > nowMs) {
            return true;
        }
        return nowMs - lastSweepAt >= MAINTENANCE_SWEEP_INTERVAL_MS;
    }

    /** Whether every player in the room has voted, so the group can leave the collecting-votes
     * waiting screen together.
     *
     * <p>Mirrors {@link #allPlayersFinishedThens}: an empty room is never "done", and a vote count
     * that has somehow overshot the player count still counts as done rather than hanging the
     * screen forever. */
    public static boolean allVotesCast(int playerCount, int castVoteCount) {
        return playerCount > 0 && castVoteCount >= playerCount;
    }

    /** Whether a room is old enough that any client may delete it outright.
     *
     * <p>Liveness is judged on the most recent of the host's heartbeat
     * ({@code rooms/<id>/hostConnection/lastSeenAt}) and the room's {@code createdAt}, so a room
     * that was created but abandoned before the first heartbeat still ages out, and a room with a
     * host actively heartbeating never does.
     *
     * <p>A room carrying neither timestamp is treated as abandoned: {@code createdAt} is written
     * at creation, so the only rooms without it pre-date that and are by definition leftovers. */
    public static boolean isRoomAbandoned(Long hostLastSeenAt, Long createdAt, boolean hasExpiredMarker, long nowMs) {
        if (hasExpiredMarker) {
            // The app itself already declared this room dead (markRoomExpired) - there is nothing
            // to wait out. This is the one job the expiredRooms tombstones can usefully do for
            // cleanup; on their own they only ever flagged the room, never removed it.
            return true;
        }
        return isRoomAbandoned(hostLastSeenAt, createdAt, nowMs);
    }

    public static boolean isRoomAbandoned(Long hostLastSeenAt, Long createdAt, long nowMs) {
        long mostRecentSignOfLife = 0L;
        if (hostLastSeenAt != null && hostLastSeenAt > mostRecentSignOfLife) {
            mostRecentSignOfLife = hostLastSeenAt;
        }
        if (createdAt != null && createdAt > mostRecentSignOfLife) {
            mostRecentSignOfLife = createdAt;
        }
        if (mostRecentSignOfLife <= 0L) {
            return true;
        }
        return nowMs - mostRecentSignOfLife > ABANDONED_ROOM_TTL_MS;
    }
}
