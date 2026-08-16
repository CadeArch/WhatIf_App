package com.CadeMixedUpGame.api;

import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.models.User;

import java.util.List;

public final class GameFlowPolicy {
    /** How long the host can be unreachable before the room is given up on and everyone is sent
     * home.
     *
     * <p>Was 20s, which measurement showed is unsurvivable: backgrounding the app (a phone call,
     * checking a message) drops the Realtime Database socket after about <b>38 seconds</b> while
     * the process is still alive - Android freezes the cached process and the connection dies
     * underneath it. So a sub-minute interruption by the host reliably deleted the room and ejected
     * everyone mid-game, with nobody having done anything wrong. 90s covers a short call with room
     * to spare while still ending genuinely dead rooms quickly; a room whose host never comes back
     * is cleaned up by the maintenance sweep regardless (ABANDONED_ROOM_TTL_MS). */
    public static final long CONNECTION_GRACE_MS = 90000L;
    /** How long a dropped non-host player must stay gone before the host may kick them.
     *
     * <p>The round does <b>not</b> skip them on its own. Disconnection is assumed to be temporary -
     * measurement showed a host's heartbeat freezes for over two minutes just from a locked phone,
     * so any automatic "they're gone, move on" rule ends real games that were only interrupted.
     * The round waits indefinitely, and removing someone is a deliberate act by the host. */
    public static final long KICK_ELIGIBLE_AFTER_MS = 90000L;
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
        return allPlayersFinishedIfs(players, System.currentTimeMillis());
    }

    public static boolean allPlayersFinishedThens(List<User> players) {
        return allPlayersFinishedThens(players, System.currentTimeMillis());
    }

    public static boolean allPlayersFinishedIfs(List<User> players, long nowMs) {
        return everyPresentPlayerIsDone(players, nowMs, true);
    }

    public static boolean allPlayersFinishedThens(List<User> players, long nowMs) {
        return everyPresentPlayerIsDone(players, nowMs, false);
    }

    /**
     * True once every player in the room has submitted.
     *
     * <p>Waits for disconnected players indefinitely, on purpose. An earlier version skipped a
     * player who had been gone past a grace window, which sounds reasonable and is wrong here:
     * dropping out is overwhelmingly temporary (a locked phone freezes the app within ~2 minutes),
     * and silently continuing without someone throws away the sentence they were writing. If a
     * player really is not coming back, the host removes them deliberately - see
     * {@link #canKickPlayer}.
     */
    private static boolean everyPresentPlayerIsDone(List<User> players, long nowMs, boolean checkIfs) {
        if (players == null || players.size() == 0) {
            return false;
        }
        boolean anyActive = false;
        for (User player : players) {
            if (player == null) {
                return false;
            }
            if (!isActivePlayer(player)) {
                continue;
            }
            anyActive = true;
            boolean finished = checkIfs
                    ? Boolean.TRUE.equals(player.ifFinished)
                    : Boolean.TRUE.equals(player.thenFinished);
            if (!finished) {
                return false;
            }
        }
        return anyActive;
    }

    /**
     * Whether this player still counts - i.e. has not been removed from the round.
     *
     * <p>Null reads as active: rooms predating the {@code removed} field have no value for it, and
     * the safe reading of unknown data is "still playing".
     */
    public static boolean isActivePlayer(User player) {
        return player != null && !Boolean.TRUE.equals(player.removed);
    }

    /**
     * Whether removing a player should rebuild the round's assignments.
     *
     * <p>Before reading starts, rebuild: assignments are only a *plan* (who will read whose If and
     * whose Then), created in the lobby before anyone writes, so regenerating for the remaining
     * players costs nothing they have seen and guarantees the counts line up. Without it, removing
     * someone between the If and Then phases strands an If with no Then - four Ifs written, then
     * only three Thens.
     *
     * <p>Once reading has started, do not: every sentence already exists and players are working
     * through them in order, so re-pairing would reshuffle sentences people have already heard. The
     * departing player's slot simply stays in the reading order and the host covers that turn.
     *
     * <p>An unknown phase is treated as "do not rebuild" - the damage from reshuffling a live
     * reading round is worse and more visible than one sentence missing its Then.
     */
    public static boolean shouldRegenerateAfterRemoval(GamePhase phase) {
        return phase == GamePhase.LOBBY
                || phase == GamePhase.WRITING_IF
                || phase == GamePhase.COLLECTING_IFS
                || phase == GamePhase.WRITING_THEN
                || phase == GamePhase.COLLECTING_THENS;
    }

    /** How many players still count toward progression, votes and the roster. */
    public static int activePlayerCount(List<User> players) {
        if (players == null) {
            return 0;
        }
        int count = 0;
        for (User player : players) {
            if (isActivePlayer(player)) {
                count += 1;
            }
        }
        return count;
    }

    /**
     * A round cannot continue with one player left. Applies only once a round is under way - a lone
     * player sitting in the lobby is just a host waiting for people to join.
     */
    public static boolean roundCannotContinue(List<User> players, boolean roundInProgress) {
        return roundInProgress && activePlayerCount(players) < 2;
    }

    /**
     * Whether the host may take over a reading turn that belongs to someone else.
     *
     * <p>Reading advances by whoever matches the active reader key, so an absent reader stops the
     * round dead - nobody else can move it on. The host covers that turn once the reader has been
     * removed from the round, or has been disconnected long enough to be removable anyway
     * ({@link #KICK_ELIGIBLE_AFTER_MS}). Deliberately not immediate: someone who dropped a few
     * seconds ago is probably about to read, and stealing their turn would be worse than waiting.
     */
    public static boolean hostMayCoverReadingTurn(User viewer, User activeReader, long nowMs) {
        if (viewer == null || !viewer.host || activeReader == null) {
            return false;
        }
        if (!isActivePlayer(activeReader)) {
            return true;
        }
        return canKickPlayer(activeReader, nowMs);
    }

    /**
     * Whether the host may remove this player right now.
     *
     * <p>Never the host themselves (nobody is left to run the room), and only after the player has
     * been visibly gone for {@link #KICK_ELIGIBLE_AFTER_MS}. A player with no {@code disconnectedAt}
     * is not kickable: without a timestamp there is no evidence of how long they have been away, and
     * older room records predate the field.
     *
     * <p>Re-check this at the moment the kick executes, not just when the button is drawn - the
     * player may have reconnected in between.
     */
    public static boolean canKickPlayer(User player, long nowMs) {
        if (player == null || Boolean.TRUE.equals(player.host) || !isActivePlayer(player)) {
            return false;
        }
        if (!Boolean.FALSE.equals(player.connected)) {
            return false;
        }
        Long disconnectedAt = player.disconnectedAt;
        if (disconnectedAt == null || disconnectedAt <= 0L) {
            return false;
        }
        return nowMs - disconnectedAt >= KICK_ELIGIBLE_AFTER_MS;
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
