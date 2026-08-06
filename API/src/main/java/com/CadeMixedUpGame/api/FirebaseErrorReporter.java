package com.CadeMixedUpGame.api;

import android.util.Log;

import com.google.firebase.database.DatabaseReference;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Non-fatal path for the auto error-log table: wired as AppLog's ErrorReporter (see
 * WhatIfApplication), so every existing AppLog.e(tag, message, throwable) call site - all 60 of
 * them, no code changes needed at any of them - now also lands a report under errorLogs/ in
 * Firebase, not just Logcat.
 */
public class FirebaseErrorReporter implements AppLog.ErrorReporter {
    private static final String LOG_TAG = "MU.ErrorReporter";

    private final DatabaseReference db;
    private final String appVersion;
    private final int maxReportsPerSession;
    private final AtomicInteger reportCount = new AtomicInteger(0);

    public FirebaseErrorReporter(DatabaseReference db, String appVersion, int maxReportsPerSession) {
        this.db = db;
        this.appVersion = appVersion;
        this.maxReportsPerSession = maxReportsPerSession;
    }

    @Override
    public void onError(String tag, String message, Throwable throwable, List<String> breadcrumbs) {
        if (db == null || reportCount.incrementAndGet() > maxReportsPerSession) {
            // Once past the cap, stop trying for the rest of this app process - a guard against
            // one repeating bug flooding the table, not a real rate limiter.
            return;
        }
        Map<String, Object> payload = ErrorReportPayload.build(tag, message, throwable, breadcrumbs, appVersion, false);
        db.child("errorLogs").push().setValue(payload)
                .addOnFailureListener(e ->
                        // Deliberately raw Log.e, never AppLog.e - logging this failure through
                        // AppLog.e would re-trigger onError() and recurse.
                        Log.e(LOG_TAG, "Failed writing error report", e));
    }
}
