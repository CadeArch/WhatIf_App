package com.CadeMixedUpGame.phoneapp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DiffGoogleVoiceTest {
    @Test
    public void constructorStoresVoiceAndCode() {
        DiffGoogleVoice voice = new DiffGoogleVoice("pig latin", "2");

        assertEquals("pig latin", voice.getVoice());
        assertEquals("2", voice.getVoiceCode());
    }

    @Test
    public void defaultConstructorStartsEmptyAndSettersUpdateValues() {
        DiffGoogleVoice voice = new DiffGoogleVoice();

        assertNull(voice.getVoice());
        assertNull(voice.getVoiceCode());

        voice.setStatus("backwords");
        voice.setStatusCode("3");

        assertEquals("backwords", voice.getVoice());
        assertEquals("3", voice.getVoiceCode());
    }
}
