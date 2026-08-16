package com.CadeMixedUpGame.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UnlockPolicyTest {

    /** The bug this whole policy exists to fix: four of the seven voices had no rule at all, so
     * they were seeded onto every account and could never be earned by anything. */
    @Test
    public void everyVoiceInTheCatalogCanActuallyBeEarned() {
        List<String> unreachable = new ArrayList<String>();
        for (UnlockPolicy.Voice voice : UnlockPolicy.catalog()) {
            // Maxed-out stats: if a voice isn't earned here, nothing in the app can ever earn it.
            if (!UnlockPolicy.isEarned(voice.getVoiceType(), Integer.MAX_VALUE, true, true)) {
                unreachable.add(voice.getVoiceType());
            }
        }
        assertEquals("every catalog voice needs an unlock rule; unreachable: " + unreachable,
                0, unreachable.size());
    }

    @Test
    public void catalogHasNoDuplicateTypesOrCodes() {
        Set<String> types = new HashSet<String>();
        Set<String> codes = new HashSet<String>();
        for (UnlockPolicy.Voice voice : UnlockPolicy.catalog()) {
            assertTrue("duplicate voice type: " + voice.getVoiceType(), types.add(voice.getVoiceType()));
            // Codes are what the spinner matches on, so a collision would silently pick the wrong voice.
            assertTrue("duplicate voice code: " + voice.getVoiceCode(), codes.add(voice.getVoiceCode()));
            assertNotNull("every voice needs a player-facing requirement", voice.getRequirement());
        }
        assertEquals(7, UnlockPolicy.catalog().size());
        // "0" is the built-in regular voice and must never be an unlockable's code.
        assertFalse("\"0\" is reserved for the regular voice", codes.contains("0"));
    }

    @Test
    public void theThreeOriginalRulesAreUnchanged() {
        // These already existed, so retuning the ladder must not silently take a voice away from
        // an account that had earned it.
        assertTrue(UnlockPolicy.isEarned(UnlockPolicy.VOICE_BACKWORDS, 5, false, false));
        assertFalse(UnlockPolicy.isEarned(UnlockPolicy.VOICE_BACKWORDS, 4, false, false));
        assertTrue(UnlockPolicy.isEarned(UnlockPolicy.VOICE_FUDDIFY, 0, true, false));
        assertFalse(UnlockPolicy.isEarned(UnlockPolicy.VOICE_FUDDIFY, 0, false, false));
        assertTrue(UnlockPolicy.isEarned(UnlockPolicy.VOICE_PIG_LATIN, 0, false, true));
        assertFalse(UnlockPolicy.isEarned(UnlockPolicy.VOICE_PIG_LATIN, 0, false, false));
    }

    @Test
    public void gamesPlayedLadderUnlocksInOrderAndStaysUnlocked() {
        assertFalse(UnlockPolicy.isEarned(UnlockPolicy.VOICE_JOKESTER, 0, false, false));
        assertTrue(UnlockPolicy.isEarned(UnlockPolicy.VOICE_JOKESTER, 1, false, false));
        assertFalse(UnlockPolicy.isEarned(UnlockPolicy.VOICE_FORGETFUL, 9, false, false));
        assertTrue(UnlockPolicy.isEarned(UnlockPolicy.VOICE_FORGETFUL, 10, false, false));
        assertFalse(UnlockPolicy.isEarned(UnlockPolicy.VOICE_SHAGGY, 19, false, false));
        assertTrue(UnlockPolicy.isEarned(UnlockPolicy.VOICE_SHAGGY, 20, false, false));
        assertFalse(UnlockPolicy.isEarned(UnlockPolicy.VOICE_DISOBEDIENT, 39, false, false));
        // Well past the threshold - an unlock never expires.
        assertTrue(UnlockPolicy.isEarned(UnlockPolicy.VOICE_DISOBEDIENT, 500, false, false));
    }

    @Test
    public void earnedVoicesGrowsWithProgressAndIsEmptyForABrandNewAccount() {
        assertEquals(0, UnlockPolicy.earnedVoices(0, false, false).size());
        // First game: the ladder's first rung only.
        assertEquals(1, UnlockPolicy.earnedVoices(1, false, false).size());
        // 5 games: jokester + backwords.
        assertEquals(2, UnlockPolicy.earnedVoices(5, false, false).size());
        // Everything.
        assertEquals(UnlockPolicy.catalog().size(),
                UnlockPolicy.earnedVoices(UnlockPolicy.DISOBEDIENT_GAMES, true, true).size());
    }

    @Test
    public void earnedVoicesReturnsTheWholeSetSoAMissedUnlockSelfRepairs() {
        // A player who was offline when they crossed 5 games still gets backwords re-asserted the
        // next time this runs - the reason unlocking re-sends everything rather than a delta.
        List<String> types = new ArrayList<String>();
        for (UnlockPolicy.Voice voice : UnlockPolicy.earnedVoices(20, false, false)) {
            types.add(voice.getVoiceType());
        }
        assertTrue(types.contains(UnlockPolicy.VOICE_JOKESTER));
        assertTrue(types.contains(UnlockPolicy.VOICE_BACKWORDS));
        assertTrue(types.contains(UnlockPolicy.VOICE_FORGETFUL));
        assertTrue(types.contains(UnlockPolicy.VOICE_SHAGGY));
        assertFalse(types.contains(UnlockPolicy.VOICE_DISOBEDIENT));
    }

    @Test
    public void milestoneAnnouncementFiresOnlyOnTheGameThatCrossesTheLine() {
        // Drives the one-off "Unlocked X!" snackbar, so it must be exact, not >=.
        assertEquals(UnlockPolicy.VOICE_JOKESTER, UnlockPolicy.voiceEarnedAtExactly(1));
        assertEquals(UnlockPolicy.VOICE_BACKWORDS, UnlockPolicy.voiceEarnedAtExactly(5));
        assertEquals(UnlockPolicy.VOICE_FORGETFUL, UnlockPolicy.voiceEarnedAtExactly(10));
        assertEquals(UnlockPolicy.VOICE_SHAGGY, UnlockPolicy.voiceEarnedAtExactly(20));
        assertEquals(UnlockPolicy.VOICE_DISOBEDIENT, UnlockPolicy.voiceEarnedAtExactly(40));
        // Non-milestone games stay quiet, including the one right after a milestone.
        assertNull(UnlockPolicy.voiceEarnedAtExactly(0));
        assertNull(UnlockPolicy.voiceEarnedAtExactly(6));
        assertNull(UnlockPolicy.voiceEarnedAtExactly(21));
        assertNull(UnlockPolicy.voiceEarnedAtExactly(41));
    }

    @Test
    public void unknownOrNullVoiceIsNeverEarnedRatherThanThrowing() {
        // A stale row for a voice that no longer exists shouldn't crash the profile screen.
        assertFalse(UnlockPolicy.isEarned("no-such-voice", Integer.MAX_VALUE, true, true));
        assertFalse(UnlockPolicy.isEarned(null, Integer.MAX_VALUE, true, true));
    }

    @Test
    public void seededUnlockableMatchesItsCatalogEntryAndStartsLocked() {
        for (UnlockPolicy.Voice voice : UnlockPolicy.catalog()) {
            assertEquals(voice.getVoiceType(), voice.toLockedUnlockable().getVoiceType());
            assertEquals(voice.getVoiceCode(), voice.toLockedUnlockable().getVoiceCode());
            assertFalse("a new account starts with everything locked", voice.toLockedUnlockable().isUnlocked());
        }
    }
}
