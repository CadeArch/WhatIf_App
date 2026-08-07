package com.CadeMixedUpGame.api;

import com.CadeMixedUpGame.api.models.User;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameFlowPolicyTest {
    @Test
    public void countFinishedIfsCountsOnlyTrueFlags() {
        User done = player("done", true, false, false);
        User waiting = player("waiting", false, false, false);
        User unknown = player("unknown", false, false, false);
        unknown.ifFinished = null;

        assertEquals(1, GameFlowPolicy.countFinishedIfs(Arrays.asList(done, waiting, unknown, null)));
    }

    @Test
    public void countFinishedThensCountsOnlyTrueFlags() {
        User done = player("done", false, true, false);
        User waiting = player("waiting", false, false, false);
        User unknown = player("unknown", false, false, false);
        unknown.thenFinished = null;

        assertEquals(1, GameFlowPolicy.countFinishedThens(Arrays.asList(done, waiting, unknown, null)));
    }

    @Test
    public void allPlayersFinishedIfsRequiresAtLeastOnePlayerAndEveryFlagTrue() {
        assertFalse(GameFlowPolicy.allPlayersFinishedIfs(null));
        assertFalse(GameFlowPolicy.allPlayersFinishedIfs(Collections.<User>emptyList()));
        assertFalse(GameFlowPolicy.allPlayersFinishedIfs(Arrays.asList(
                player("a", true, false, false),
                player("b", false, false, false))));
        assertTrue(GameFlowPolicy.allPlayersFinishedIfs(Arrays.asList(
                player("a", true, false, false),
                player("b", true, false, false))));
    }

    @Test
    public void allPlayersFinishedIfsRequiresStableConnections() {
        User connected = player("connected", true, false, false);
        User disconnected = player("disconnected", true, false, false);
        disconnected.connected = false;

        assertFalse(GameFlowPolicy.allPlayersFinishedIfs(Arrays.asList(connected, disconnected)));
    }

    @Test
    public void allPlayersFinishedThensRequiresAtLeastOnePlayerAndEveryFlagTrue() {
        assertFalse(GameFlowPolicy.allPlayersFinishedThens(null));
        assertFalse(GameFlowPolicy.allPlayersFinishedThens(Collections.<User>emptyList()));
        assertFalse(GameFlowPolicy.allPlayersFinishedThens(Arrays.asList(
                player("a", false, true, false),
                player("b", false, false, false))));
        assertTrue(GameFlowPolicy.allPlayersFinishedThens(Arrays.asList(
                player("a", false, true, false),
                player("b", false, true, false))));
    }

    @Test
    public void allPlayersFinishedThensRequiresStableConnections() {
        User connected = player("connected", false, true, false);
        User disconnected = player("disconnected", false, true, false);
        disconnected.connected = false;

        assertFalse(GameFlowPolicy.allPlayersFinishedThens(Arrays.asList(connected, disconnected)));
    }

    @Test
    public void allPlayersConnectedTreatsNullAsConnectedForOlderData() {
        User connected = player("connected", false, false, false);
        User olderRecord = player("older", false, false, false);
        olderRecord.connected = null;
        User disconnected = player("disconnected", false, false, false);
        disconnected.connected = false;

        assertFalse(GameFlowPolicy.allPlayersConnected(null));
        assertFalse(GameFlowPolicy.allPlayersConnected(Collections.<User>emptyList()));
        assertTrue(GameFlowPolicy.allPlayersConnected(Arrays.asList(connected, olderRecord)));
        assertFalse(GameFlowPolicy.allPlayersConnected(Arrays.asList(connected, disconnected)));
    }

    @Test
    public void allPlayersHaveAccountsRequiresNonEmptyAllAccountGroup() {
        assertFalse(GameFlowPolicy.allPlayersHaveAccounts(null));
        assertFalse(GameFlowPolicy.allPlayersHaveAccounts(Collections.<User>emptyList()));
        assertFalse(GameFlowPolicy.allPlayersHaveAccounts(Arrays.asList(
                player("account", false, false, true),
                player("free", false, false, false))));
        assertTrue(GameFlowPolicy.allPlayersHaveAccounts(Arrays.asList(
                player("account-a", false, false, true),
                player("account-b", false, false, true))));
    }

    @Test
    public void finalReaderPassedOnlyAfterLastReadOrderIndex() {
        assertFalse(GameFlowPolicy.finalReaderPassed(0, 0));
        assertFalse(GameFlowPolicy.finalReaderPassed(1, 2));
        assertTrue(GameFlowPolicy.finalReaderPassed(2, 2));
        assertTrue(GameFlowPolicy.finalReaderPassed(3, 2));
    }

    @Test
    public void hostHeartbeatExpiresAfterGraceWindow() {
        long now = 50000L;

        assertEquals(GameFlowPolicy.CONNECTION_GRACE_MS, GameFlowPolicy.millisUntilHostHeartbeatExpires(now, 0L));
        assertEquals(5000L, GameFlowPolicy.millisUntilHostHeartbeatExpires(now, 35000L));
        assertEquals(0L, GameFlowPolicy.millisUntilHostHeartbeatExpires(now, 30000L));
        assertEquals(0L, GameFlowPolicy.millisUntilHostHeartbeatExpires(now, 25000L));
        assertFalse(GameFlowPolicy.hostHeartbeatExpired(now, 35000L));
        assertTrue(GameFlowPolicy.hostHeartbeatExpired(now, 30000L));
    }

    @Test
    public void connectionTimingConstantsKeepHeartbeatTighterThanGraceWindow() {
        assertTrue(GameFlowPolicy.HOST_HEARTBEAT_INTERVAL_MS > 0L);
        assertTrue(GameFlowPolicy.HOST_HEARTBEAT_INTERVAL_MS < GameFlowPolicy.CONNECTION_GRACE_MS);
        assertTrue(GameFlowPolicy.CLIENT_HOME_AFTER_HOST_EXPIRE_DELAY_MS > 0L);
        assertTrue(GameFlowPolicy.CLIENT_HOME_AFTER_HOST_EXPIRE_DELAY_MS < GameFlowPolicy.CONNECTION_GRACE_MS);
    }

    @Test
    public void normalizeRoomCodeInputHandlesNullAndTrims() {
        assertEquals("", GameFlowPolicy.normalizeRoomCodeInput(null));
        assertEquals("wolf-lake", GameFlowPolicy.normalizeRoomCodeInput("  wolf-lake  "));
    }

    @Test
    public void normalizeRoomCodeInputLowercasesAndReinsertsTheDashRegardlessOfHowItWasTyped() {
        assertEquals("wolf-lake", GameFlowPolicy.normalizeRoomCodeInput("WOLF-LAKE"));
        assertEquals("wolf-lake", GameFlowPolicy.normalizeRoomCodeInput("wolflake"));
        assertEquals("wolf-lake", GameFlowPolicy.normalizeRoomCodeInput("WolfLake"));
        assertEquals("wolf-lake", GameFlowPolicy.normalizeRoomCodeInput("wolf lake"));
        assertEquals("wolf-lake", GameFlowPolicy.normalizeRoomCodeInput("wolf_lake"));
    }

    @Test
    public void normalizeRoomCodeInputLeavesTheWrongLetterCountUnchangedForANaturalNotFound() {
        assertEquals("abc", GameFlowPolicy.normalizeRoomCodeInput("abc"));
        assertEquals("way too long of a room code",
                GameFlowPolicy.normalizeRoomCodeInput("way too long of a room code"));
    }

    @Test
    public void maintenanceSweepIsDueOncePerIntervalAndOnFirstEverRun() {
        long now = 1_000_000_000L;
        // Nothing has ever swept.
        assertTrue(GameFlowPolicy.isMaintenanceSweepDue(null, now));
        assertTrue(GameFlowPolicy.isMaintenanceSweepDue(0L, now));
        assertTrue(GameFlowPolicy.isMaintenanceSweepDue(now - GameFlowPolicy.MAINTENANCE_SWEEP_INTERVAL_MS, now));
        // Someone already swept recently, so every other client that launches today does nothing.
        assertFalse(GameFlowPolicy.isMaintenanceSweepDue(now - 1000L, now));
        assertFalse(GameFlowPolicy.isMaintenanceSweepDue(now - GameFlowPolicy.MAINTENANCE_SWEEP_INTERVAL_MS + 1000L, now));
    }

    @Test
    public void maintenanceSweepRecoversFromAFutureDatedClaim() {
        long now = 1_000_000_000L;
        // A device with a badly skewed clock could otherwise write a timestamp far enough ahead to
        // block every sweep until that time actually arrives.
        assertTrue(GameFlowPolicy.isMaintenanceSweepDue(now + (365L * 24L * 60L * 60L * 1000L), now));
    }

    @Test
    public void votesAreOnlyCompleteOnceEveryPlayerHasVoted() {
        assertFalse(GameFlowPolicy.allVotesCast(3, 2));
        assertFalse(GameFlowPolicy.allVotesCast(2, 0));
        assertTrue(GameFlowPolicy.allVotesCast(2, 2));
        assertTrue(GameFlowPolicy.allVotesCast(4, 4));
    }

    @Test
    public void voteCountIsNeverAllowedToStrandTheCollectingScreen() {
        // An empty room is not "done" (nothing to wait for means nothing to advance to), but a
        // count that has somehow overshot must still release the screen rather than hang on it.
        assertFalse(GameFlowPolicy.allVotesCast(0, 0));
        assertTrue(GameFlowPolicy.allVotesCast(2, 3));
    }

    @Test
    public void roomWithARecentHostHeartbeatIsNeverAbandoned() {
        long now = 1_000_000_000L;
        // Actively heartbeating - the case it would be worst to get wrong, since deleting this
        // room would rip it out from under people mid-game.
        assertFalse(GameFlowPolicy.isRoomAbandoned(now - 500L, now - 500L, now));
        // Host has been quiet a while but not long enough yet.
        assertFalse(GameFlowPolicy.isRoomAbandoned(now - GameFlowPolicy.ABANDONED_ROOM_TTL_MS + 1000L, null, now));
        // A long-running session: created hours ago, but the host is still here.
        assertFalse(GameFlowPolicy.isRoomAbandoned(now - 1000L, now - (48L * 60L * 60L * 1000L), now));
    }

    @Test
    public void roomIsAbandonedOnceEveryTimestampIsOlderThanTheTtl() {
        long now = 1_000_000_000L;
        assertTrue(GameFlowPolicy.isRoomAbandoned(now - GameFlowPolicy.ABANDONED_ROOM_TTL_MS - 1L, null, now));
        // Created and walked away from before the host ever heartbeat.
        assertTrue(GameFlowPolicy.isRoomAbandoned(null, now - GameFlowPolicy.ABANDONED_ROOM_TTL_MS - 1L, now));
    }

    @Test
    public void roomAlreadyMarkedExpiredIsDeletableImmediately() {
        long now = 1_000_000_000L;
        // Host was seen a second ago, but the app has already declared the room dead - there is
        // nothing left to wait out.
        assertTrue(GameFlowPolicy.isRoomAbandoned(now - 1000L, now - 1000L, true, now));
        // Without the marker that same room is very much alive.
        assertFalse(GameFlowPolicy.isRoomAbandoned(now - 1000L, now - 1000L, false, now));
    }

    @Test
    public void aFinishedGamesRoomIsCollectedRatherThanLookingRecentAllDay() {
        long now = 1_000_000_000L;
        // The host heartbeats every second, so a room whose last heartbeat is an hour old has
        // missed thousands of them - it is not "recent", it is over.
        assertTrue(GameFlowPolicy.isRoomAbandoned(now - (60L * 60L * 1000L), null, false, now));
        // Still mid-game: heartbeat from a moment ago.
        assertFalse(GameFlowPolicy.isRoomAbandoned(now - 2000L, now - (60L * 60L * 1000L), false, now));
    }

    @Test
    public void roomWithNoTimestampsAtAllIsTreatedAsALeftover() {
        // createdAt is stamped at creation, so a room carrying neither timestamp pre-dates that
        // and can only be junk from before this cleanup existed.
        assertTrue(GameFlowPolicy.isRoomAbandoned(null, null, 1_000_000_000L));
        assertTrue(GameFlowPolicy.isRoomAbandoned(0L, 0L, 1_000_000_000L));
    }

    private User player(String name, boolean ifFinished, boolean thenFinished, boolean accountPlay) {
        User user = new User(name);
        user.ifFinished = ifFinished;
        user.thenFinished = thenFinished;
        user.accountPlay = accountPlay;
        return user;
    }
}
