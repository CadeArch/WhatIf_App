package com.CadeMixedUpGame.phoneapp;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Local store-and-forward for fatal crash reports (see WhatIfApplication's uncaught-exception
 * handler). A crashing process can die before an async Firebase write completes, so the payload
 * is written synchronously to a small local file first - fast and local, unlike a network write -
 * then uploaded on the *next* app launch and deleted. This is what makes fatal crash reporting
 * reliable rather than best-effort, unlike the non-fatal path (FirebaseErrorReporter), which can
 * just write directly since the app process is still alive and running afterward.
 */
final class PendingCrashReportStore {
    private static final String LOG_TAG = "MU.CrashStore";
    private static final String FILE_NAME = "pending_crash_report.json";

    private PendingCrashReportStore() {
    }

    static void writePending(Context context, Map<String, Object> payload) {
        if (context == null || payload == null) {
            return;
        }
        File file = new File(context.getFilesDir(), FILE_NAME);
        try {
            JSONObject json = new JSONObject(payload);
            FileOutputStream out = new FileOutputStream(file);
            try {
                out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }
            finally {
                out.close();
            }
        }
        catch (Exception e) {
            // Deliberately raw Log.e - AppLog isn't safe to call from inside a crash handler
            // that's already mid-teardown, and this must never itself throw.
            Log.e(LOG_TAG, "Failed writing pending crash report", e);
        }
    }

    static Map<String, Object> readAndClearPending(Context context) {
        if (context == null) {
            return null;
        }
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(new String(readAllBytes(file), StandardCharsets.UTF_8));
            return toMap(json);
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "Failed reading pending crash report", e);
            return null;
        }
        finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static byte[] readAllBytes(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[(int) file.length()];
            int offset = 0;
            int read;
            while (offset < buffer.length && (read = in.read(buffer, offset, buffer.length - offset)) >= 0) {
                offset += read;
            }
            return buffer;
        }
        finally {
            in.close();
        }
    }

    private static Map<String, Object> toMap(JSONObject json) throws JSONException {
        Map<String, Object> map = new HashMap<String, Object>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);
            if (value instanceof JSONArray) {
                map.put(key, toList((JSONArray) value));
            }
            else if (value instanceof JSONObject) {
                map.put(key, toMap((JSONObject) value));
            }
            else {
                map.put(key, value);
            }
        }
        return map;
    }

    private static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<Object>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                list.add(toList((JSONArray) value));
            }
            else if (value instanceof JSONObject) {
                list.add(toMap((JSONObject) value));
            }
            else {
                list.add(value);
            }
        }
        return list;
    }
}
