package com.CadeMixedUpGame.api;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Points the app at the local Firebase Emulator Suite (see firebase.json at the repo root)
 * instead of the live production project. Opt-in only — see MixedUp/build.gradle's
 * USE_FIREBASE_EMULATOR flag. Must be called before any other Firebase Database/Auth usage.
 */
public final class FirebaseEmulatorConfig {
    // Real device/emulator: 10.0.2.2 is the special host-loopback alias. Robolectric JVM tests
    // run directly on the host machine (no virtual device network layer), so "localhost" is
    // correct there instead - see configureIfEnabled(FirebaseApp, String, boolean).
    private static final String EMULATOR_HOST = "10.0.2.2";
    private static final int DATABASE_EMULATOR_PORT = 9000;
    private static final int AUTH_EMULATOR_PORT = 9099;

    private FirebaseEmulatorConfig() {
    }

    public static void configureIfEnabled(boolean enabled) {
        if (!enabled) {
            return;
        }
        // Robolectric bootstraps this Application class for every test in the module, including
        // ones that never initialize a default FirebaseApp themselves - FirebaseApp.getInstance()
        // throws IllegalStateException in that case. Must not take the whole test down with it
        // (matches WhatIfApplication.tryGetDefaultDatabaseReference()'s guard for the same reason).
        try {
            configureIfEnabled(FirebaseApp.getInstance(), EMULATOR_HOST, true);
        }
        catch (IllegalStateException e) {
            AppLog.w(AppLog.FIREBASE, "Default FirebaseApp not ready; skipping emulator config for this session");
        }
    }

    /** For tests driving a specific (possibly non-default, e.g. named per simulated player)
     * FirebaseApp instance directly, where "host" is also caller-controlled (Robolectric JVM
     * tests use "localhost"; a real device/emulator uses the 10.0.2.2 loopback alias). */
    public static void configureIfEnabled(FirebaseApp app, String host, boolean enabled) {
        if (!enabled) {
            return;
        }
        FirebaseDatabase.getInstance(app).useEmulator(host, DATABASE_EMULATOR_PORT);
        FirebaseAuth.getInstance(app).useEmulator(host, AUTH_EMULATOR_PORT);
        AppLog.i(AppLog.FIREBASE, "Using local Firebase Emulator Suite at " + host
                + " (database:" + DATABASE_EMULATOR_PORT + ", auth:" + AUTH_EMULATOR_PORT
                + ", app:" + app.getName() + ")");
    }
}
