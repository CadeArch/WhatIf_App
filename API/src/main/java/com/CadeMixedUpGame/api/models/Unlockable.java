package com.CadeMixedUpGame.api.models;

public class Unlockable {
    String voiceType;
    String voiceCode;
    boolean unlocked;

    public Unlockable(String voiceType, String voiceCode, boolean unlocked) {
        this.voiceCode = voiceCode;
        this.voiceType = voiceType;
        this.unlocked = unlocked;
    }

    public Unlockable() {}

    public String getVoiceCode() {
        return voiceCode;
    }

    public String getVoiceType() {
        return voiceType;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }

    public void setVoiceCode(String voiceCode) {
        this.voiceCode = voiceCode;
    }

    public void setVoiceType(String voiceType) {
        this.voiceType = voiceType;
    }
}
