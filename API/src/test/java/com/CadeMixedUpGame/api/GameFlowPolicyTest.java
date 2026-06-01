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
    public void normalizeRoomCodeInputTrimsButPreservesCase() {
        assertEquals("", GameFlowPolicy.normalizeRoomCodeInput(null));
        assertEquals("aB01", GameFlowPolicy.normalizeRoomCodeInput("  aB01  "));
    }

    private User player(String name, boolean ifFinished, boolean thenFinished, boolean accountPlay) {
        User user = new User(name);
        user.ifFinished = ifFinished;
        user.thenFinished = thenFinished;
        user.accountPlay = accountPlay;
        return user;
    }
}
