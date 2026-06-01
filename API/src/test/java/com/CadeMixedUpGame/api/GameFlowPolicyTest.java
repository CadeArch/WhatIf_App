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
