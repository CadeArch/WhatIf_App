package com.CadeMixedUpGame.api;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Points the app at the local Firebase Emulator Suite (see firebase.json at the repo root)
 * instead of the live production project. Opt-in only — see MixedUp/build.gradle's
 * USE_FIREBASE_EMULATOR flag. Must be called before any other Firebase Database/Auth usage.
 */
public final class FirebaseEmulatorConfig {
    private static final String EMULATOR_HOST = "10.0.2.2"; // host loopback, as seen from an Android emulator
    private static final int DATABASE_EMULATOR_PORT = 9000;
    private static final int AUTH_EMULATOR_PORT = 9099;

    private FirebaseEmulatorConfig() {
    }

    public static void configureIfEnabled(boolean enabled) {
        if (!enabled) {
            return;
        }
        FirebaseDatabase.getInstance().useEmulator(EMULATOR_HOST, DATABASE_EMULATOR_PORT);
        FirebaseAuth.getInstance().useEmulator(EMULATOR_HOST, AUTH_EMULATOR_PORT);
        AppLog.i(AppLog.FIREBASE, "Using local Firebase Emulator Suite at " + EMULATOR_HOST
                + " (database:" + DATABASE_EMULATOR_PORT + ", auth:" + AUTH_EMULATOR_PORT + ")");
    }
}
