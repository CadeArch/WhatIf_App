package com.CadeMixedUpGame.api;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class AppLog {
    public static final String AUTH = "MU.Auth";
    public static final String ROOM = "MU.Room";
    public static final String GAME_FLOW = "MU.GameFlow";
    public static final String FIREBASE = "MU.Firebase";
    public static final String VOTE = "MU.Vote";
    public static final String UI = "MU.UI";
    public static final String TTS = "MU.TTS";
    public static final String PUSH = "MU.Push";
    public static final String CRASH = "MU.Crash";

    private static final int BREADCRUMB_CAPACITY = 40;
    private static final List<String> breadcrumbs = new ArrayList<String>();

    /** Set once at app startup (see WhatIfApplication) to funnel real exceptions logged through
     * e(tag, message, throwable) into a durable error-log table, without touching any of the
     * existing call sites that already pass a Throwable. */
    public interface ErrorReporter {
        void onError(String tag, String message, Throwable throwable, List<String> breadcrumbs);
    }

    private static volatile ErrorReporter errorReporter;

    private AppLog() {
    }

    public static void setErrorReporter(ErrorReporter reporter) {
        errorReporter = reporter;
    }

    public static void d(String tag, String message) {
        recordBreadcrumb("D", tag, message);
        try {
            Log.d(tag, message);
        } catch (RuntimeException ignored) {
        }
    }

    public static void i(String tag, String message) {
        recordBreadcrumb("I", tag, message);
        try {
            Log.i(tag, message);
        } catch (RuntimeException ignored) {
        }
    }

    public static void w(String tag, String message) {
        recordBreadcrumb("W", tag, message);
        try {
            Log.w(tag, message);
        } catch (RuntimeException ignored) {
        }
    }

    public static void e(String tag, String message) {
        recordBreadcrumb("E", tag, message);
        try {
            Log.e(tag, message);
        } catch (RuntimeException ignored) {
        }
    }

    public static void e(String tag, String message, Throwable throwable) {
        recordBreadcrumb("E", tag, message);
        try {
            Log.e(tag, message, throwable);
        } catch (RuntimeException ignored) {
        }
        ErrorReporter reporter = errorReporter;
        if (reporter != null) {
            try {
                reporter.onError(tag, message, throwable, snapshotBreadcrumbs());
            } catch (RuntimeException ignored) {
                // A reporter failure must never break logging itself.
            }
        }
    }

    /** Breadcrumbs are recorded before the Log.x(...) call (not inside its try block) so capture
     * works even in plain JVM tests where android.util.Log isn't mocked and throws immediately -
     * this repo's GameLogicTest/GameFlowPolicyTest already rely on that RuntimeException being
     * swallowed, and breadcrumb capture needs to work independently of whether Log itself does. */
    private static void recordBreadcrumb(String level, String tag, String message) {
        synchronized (breadcrumbs) {
            breadcrumbs.add(level + "/" + tag + ": " + message);
            while (breadcrumbs.size() > BREADCRUMB_CAPACITY) {
                breadcrumbs.remove(0);
            }
        }
    }

    /** Defensive copy - callers (error reporters) may hold onto this while more logging happens
     * concurrently on other threads. */
    public static List<String> snapshotBreadcrumbs() {
        synchronized (breadcrumbs) {
            return new ArrayList<String>(breadcrumbs);
        }
    }

    /** Test-only: AppLog's breadcrumb buffer and reporter are process-static state, so tests that
     * care about either need to reset between methods. */
    public static void resetForTest() {
        synchronized (breadcrumbs) {
            breadcrumbs.clear();
        }
        errorReporter = null;
    }

    public static String user(UserLogInfo user) {
        if (user == null) {
            return "user=null";
        }
        return "user=" + value(user.logName()) + ", room=" + value(user.logRoom()) + ", id=" + value(user.logId());
    }

    private static String value(Object value) {
        if (value == null) {
            return "null";
        }
        String text = String.valueOf(value);
        if (text.length() > 12) {
            return text.substring(0, 12);
        }
        return text;
    }

    public interface UserLogInfo {
        String logName();
        String logRoom();
        String logId();
    }
}
