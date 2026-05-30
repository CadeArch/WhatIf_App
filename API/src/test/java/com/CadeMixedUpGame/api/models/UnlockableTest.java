package com.CadeMixedUpGame.api.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnlockableTest {
    @Test
    public void constructorStoresVoiceTypeCodeAndUnlockedState() {
        Unlockable unlockable = new Unlockable("pig latin", "2", false);

        assertEquals("pig latin", unlockable.getVoiceType());
        assertEquals("2", unlockable.getVoiceCode());
        assertFalse(unlockable.isUnlocked());
    }

    @Test
    public void settersUpdateVoiceTypeCodeAndUnlockedState() {
        Unlockable unlockable = new Unlockable();

        unlockable.setVoiceType("backwords");
        unlockable.setVoiceCode("3");
        unlockable.setUnlocked(true);

        assertEquals("backwords", unlockable.getVoiceType());
        assertEquals("3", unlockable.getVoiceCode());
        assertTrue(unlockable.isUnlocked());
    }
}
