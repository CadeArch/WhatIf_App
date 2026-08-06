package com.CadeMixedUpGame.phoneapp;

import android.app.Application;
import android.util.Log;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.ErrorReportPayload;
import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;
import com.CadeMixedUpGame.api.FirebaseErrorReporter;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Map;

public class WhatIfApplication extends Application {
    private static final String LOG_TAG = "MU.App";
    private static final int MAX_ERROR_REPORTS_PER_SESSION = 25;

    @Override
    public void onCreate() {
        super.onCreate();
        // Must run before any other Firebase Database/Auth call. No-op unless the app was built
        // with -PuseFirebaseEmulator=true (see MixedUp/build.gradle and firebase.json).
        FirebaseEmulatorConfig.configureIfEnabled(BuildConfig.USE_FIREBASE_EMULATOR);

        // Registered unconditionally - a crashing thread's local write doesn't need Firebase to
        // be ready at all, so this must not depend on the (possibly not-yet-initialized) database
        // reference below.
        registerCrashHandler();

        DatabaseReference db = tryGetDefaultDatabaseReference();
        if (db != null) {
            uploadPendingCrashReportIfAny(db);
            AppLog.setErrorReporter(new FirebaseErrorReporter(db, BuildConfig.VERSION_NAME, MAX_ERROR_REPORTS_PER_SESSION));
        }
    }

    /** In real app usage the google-services Gradle plugin's FirebaseInitProvider guarantees the
     * default FirebaseApp is ready before Application.onCreate() runs. It is deliberately NOT
     * guaranteed in every environment this Application class gets bootstrapped in (Robolectric
     * instantiates and calls onCreate() on the manifest's real Application class for every test
     * in this module, including ones that never initialize a default FirebaseApp themselves) - so
     * this must never throw and take the whole app/test down with it. */
    private DatabaseReference tryGetDefaultDatabaseReference() {
        try {
            return FirebaseDatabase.getInstance().getReference();
        }
        catch (RuntimeException e) {
            Log.w(LOG_TAG, "Default FirebaseApp not ready; error-log reporting disabled for this session", e);
            return null;
        }
    }

    private void registerCrashHandler() {
        Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Map<String, Object> payload = ErrorReportPayload.build(
                    AppLog.CRASH, "Fatal: " + thread.getName(), throwable,
                    AppLog.snapshotBreadcrumbs(), BuildConfig.VERSION_NAME, true);
            PendingCrashReportStore.writePending(getApplicationContext(), payload);
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
            }
        });
    }

    /** Auto error-log table (README roadmap): a crash report left over from a previous run that
     * didn't survive long enough to upload gets delivered now, before the app does anything else
     * observable. */
    private void uploadPendingCrashReportIfAny(DatabaseReference db) {
        try {
            Map<String, Object> pending = PendingCrashReportStore.readAndClearPending(getApplicationContext());
            if (pending != null) {
                db.child("errorLogs").push().setValue(pending);
            }
        }
        catch (RuntimeException e) {
            // Must never block app startup.
            Log.e(LOG_TAG, "Failed uploading pending crash report", e);
        }
    }
}
