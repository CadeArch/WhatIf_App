package com.CadeMixedUpGame.phoneapp;



public class DiffGoogleVoice {

    private String voice;
    private String voiceCode;

    public DiffGoogleVoice() {
    }

    public DiffGoogleVoice(String voice,
                           String statusCode) {
        this.voice = voice;
        this.voiceCode = statusCode;
    }

    public String getVoice() {
        return voice;
    }

    public void setStatus(String voice) {
        this.voice = voice;
    }

    public String getVoiceCode() {
        return voiceCode;
    }

    public void setStatusCode(String voiceCode) {
        this.voiceCode = voiceCode;
    }
}

