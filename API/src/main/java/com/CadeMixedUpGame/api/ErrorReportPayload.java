package com.CadeMixedUpGame.api;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure payload builder for the auto error-log table (see README roadmap: "build an auto error log
 * DB table... log both the code flow and the exception"). Kept Android/Firebase-independent
 * beyond plain java.lang.Throwable so it's testable the same way as GameLogic/GameFlowPolicy -
 * FirebaseErrorReporter (non-fatal, direct write) and PendingCrashReportStore (fatal, local-file
 * round trip before upload) both build the same shape via this class.
 *
 * Uses client-side System.currentTimeMillis() rather than Firebase's ServerValue.TIMESTAMP
 * sentinel deliberately - the fatal-crash path round-trips this payload through a local JSON file
 * before it ever reaches Firebase, and a plain long survives that far more simply/predictably than
 * relying on the sentinel map shape surviving a JSON serialize/deserialize cycle. Relative ordering
 * for manual review doesn't need server-clock precision.
 */
public final class ErrorReportPayload {
    private static final int MAX_STACK_TRACE_LENGTH = 4000;

    private ErrorReportPayload() {
    }

    public static Map<String, Object> build(String tag, String message, Throwable throwable,
                                             List<String> breadcrumbs, String appVersion, boolean fatal) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("tag", tag == null ? "" : tag);
        payload.put("message", message == null ? "" : message);
        payload.put("exceptionClass", throwable == null ? "" : throwable.getClass().getName());
        payload.put("exceptionMessage", throwable == null || throwable.getMessage() == null ? "" : throwable.getMessage());
        payload.put("stackTrace", stackTraceOf(throwable));
        payload.put("breadcrumbs", breadcrumbs == null ? new ArrayList<String>() : new ArrayList<String>(breadcrumbs));
        payload.put("appVersion", appVersion == null ? "" : appVersion);
        payload.put("fatal", fatal);
        payload.put("timestampMs", System.currentTimeMillis());
        return payload;
    }

    private static String stackTraceOf(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        String trace = writer.toString();
        if (trace.length() > MAX_STACK_TRACE_LENGTH) {
            return trace.substring(0, MAX_STACK_TRACE_LENGTH) + "...[truncated]";
        }
        return trace;
    }
}
