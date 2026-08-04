package com.CadeMixedUpGame.phoneapp;

import android.app.Application;

import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;

public class WhatIfApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Must run before any other Firebase Database/Auth call. No-op unless the app was built
        // with -PuseFirebaseEmulator=true (see MixedUp/build.gradle and firebase.json).
        FirebaseEmulatorConfig.configureIfEnabled(BuildConfig.USE_FIREBASE_EMULATOR);
    }
}
