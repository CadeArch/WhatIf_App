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

    public String getStatus() {
        return voice;
    }

    public void setStatus(String voice) {
        this.voice = voice;
    }

    public String getStatusCode() {
        return voiceCode;
    }

    public void setStatusCode(String voiceCode) {
        this.voiceCode = voiceCode;
    }
}

