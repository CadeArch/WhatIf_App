package com.CadeMixedUpGame.api;

public final class RoomCreationPolicy {
    public static final int MAX_CREATE_ATTEMPTS = 8;

    private RoomCreationPolicy() {
    }

    public static boolean shouldRetry(int attemptIndex, boolean committed, boolean hadError) {
        return !committed && attemptIndex < MAX_CREATE_ATTEMPTS - 1;
    }
}
