package com.CadeMixedUpGame.api;

import android.util.Log;

public class AppLog {
    public static final String AUTH = "MU.Auth";
    public static final String ROOM = "MU.Room";
    public static final String GAME_FLOW = "MU.GameFlow";
    public static final String FIREBASE = "MU.Firebase";
    public static final String VOTE = "MU.Vote";
    public static final String UI = "MU.UI";
    public static final String TTS = "MU.TTS";
    public static final String PUSH = "MU.Push";

    private AppLog() {
    }

    public static void d(String tag, String message) {
        Log.d(tag, message);
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
    }

    public static void e(String tag, String message) {
        Log.e(tag, message);
    }

    public static void e(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
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
