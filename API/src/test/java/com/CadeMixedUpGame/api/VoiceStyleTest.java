package com.CadeMixedUpGame.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VoiceStyleTest {

    @Test
    public void everyUnlockableVoiceSoundsDifferentFromTheNarrator() {
        // The counterpart to GameLogicTest.everyUnlockableVoiceActuallyChangesTheText: that one
        // guards the words, this one guards the delivery. A voice with no style entry comes out at
        // the narrator's own pitch and speed, which is half a reward.
        List<String> plain = new ArrayList<>();
        for (UnlockPolicy.Voice voice : UnlockPolicy.catalog()) {
            if (VoiceStyle.forCode(voice.getVoiceCode()).isNormal()) {
                plain.add(voice.getVoiceType() + " (code " + voice.getVoiceCode() + ")");
            }
        }
        assertTrue("these voices are delivered exactly like the plain narrator: " + plain,
                plain.isEmpty());
    }

    @Test
    public void everyUnlockableVoiceSoundsDifferentFromEveryOther() {
        // Two voices sharing a pitch/rate pair are distinguishable only by their wordplay, which is
        // exactly the situation this class was added to fix.
        Set<String> seen = new HashSet<>();
        for (UnlockPolicy.Voice voice : UnlockPolicy.catalog()) {
            String style = VoiceStyle.forCode(voice.getVoiceCode()).toString();
            assertTrue("duplicate delivery " + style + " on " + voice.getVoiceType(),
                    seen.add(style));
        }
    }

    @Test
    public void everyStyleStaysInsideTheIntelligibleRange() {
        // Android accepts wider values, but they stop being understandable well before the extremes
        // and an unintelligible reward is not a reward.
        for (UnlockPolicy.Voice voice : UnlockPolicy.catalog()) {
            VoiceStyle style = VoiceStyle.forCode(voice.getVoiceCode());
            assertInRange(voice.getVoiceType() + " pitch", style.getPitch());
            assertInRange(voice.getVoiceType() + " rate", style.getSpeechRate());
        }
    }

    @Test
    public void theRegularVoiceAndUnknownCodesAreLeftAlone() {
        assertTrue("regular must not be restyled",
                VoiceStyle.forCode(VoiceStyle.REGULAR_VOICE_CODE).isNormal());
        // A stale database row for a voice that no longer exists must read plainly, not crash.
        assertTrue(VoiceStyle.forCode("not-a-voice").isNormal());
        assertTrue(VoiceStyle.forCode(null).isNormal());
    }

    @Test
    public void styleLookupIsStable() {
        // Same code, same delivery - the picker would feel broken if a voice sounded different each
        // time it was chosen.
        assertEquals(VoiceStyle.forCode(UnlockPolicy.VOICE_SHAGGY_CODE).toString(),
                VoiceStyle.forCode(UnlockPolicy.VOICE_SHAGGY_CODE).toString());
    }

    @Test
    public void shaggyIsTheLowestAndForgetfulTheSlowest() {
        // Not arbitrary: these two are the characters whose whole joke is in the delivery, so if a
        // retune ever flattens them the rest of the set is probably flat too.
        VoiceStyle shaggy = VoiceStyle.forCode(UnlockPolicy.VOICE_SHAGGY_CODE);
        VoiceStyle forgetful = VoiceStyle.forCode(UnlockPolicy.VOICE_FORGETFUL_CODE);
        for (UnlockPolicy.Voice voice : UnlockPolicy.catalog()) {
            VoiceStyle other = VoiceStyle.forCode(voice.getVoiceCode());
            assertTrue("shaggy should be the lowest-pitched voice", shaggy.getPitch() <= other.getPitch());
            assertTrue("forgetful should be the slowest voice",
                    forgetful.getSpeechRate() <= other.getSpeechRate());
        }
        assertFalse("shaggy must still differ from the narrator", shaggy.isNormal());
    }

    private void assertInRange(String what, float value) {
        assertTrue(what + " is below the intelligible range: " + value,
                value >= VoiceStyle.MIN_MULTIPLIER);
        assertTrue(what + " is above the intelligible range: " + value,
                value <= VoiceStyle.MAX_MULTIPLIER);
    }
}
