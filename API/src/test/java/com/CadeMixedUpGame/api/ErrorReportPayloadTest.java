package com.CadeMixedUpGame.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ErrorReportPayloadTest {

    @Test
    public void buildIncludesAllExpectedFieldsForANonFatalError() {
        List<String> breadcrumbs = Arrays.asList("I/MU.Room: joined room", "E/MU.Firebase: write failed");
        RuntimeException thrown = new RuntimeException("boom");

        Map<String, Object> payload = ErrorReportPayload.build(
                AppLog.FIREBASE, "write failed", thrown, breadcrumbs, "2.0.4", false);

        assertEquals(AppLog.FIREBASE, payload.get("tag"));
        assertEquals("write failed", payload.get("message"));
        assertEquals(RuntimeException.class.getName(), payload.get("exceptionClass"));
        assertEquals("boom", payload.get("exceptionMessage"));
        assertTrue(((String) payload.get("stackTrace")).contains("RuntimeException"));
        assertEquals(breadcrumbs, payload.get("breadcrumbs"));
        assertEquals("2.0.4", payload.get("appVersion"));
        assertEquals(false, payload.get("fatal"));
        assertTrue((Long) payload.get("timestampMs") > 0L);
    }

    @Test
    public void fatalFlagRoundTrips() {
        Map<String, Object> fatal = ErrorReportPayload.build(AppLog.CRASH, "crash", new RuntimeException(), null, "1.0", true);
        Map<String, Object> nonFatal = ErrorReportPayload.build(AppLog.CRASH, "crash", new RuntimeException(), null, "1.0", false);

        assertEquals(true, fatal.get("fatal"));
        assertEquals(false, nonFatal.get("fatal"));
    }

    @Test
    public void handlesNullThrowableAndNullBreadcrumbsWithoutThrowing() {
        Map<String, Object> payload = ErrorReportPayload.build(AppLog.UI, "no exception here", null, null, "2.0.4", false);

        assertEquals("", payload.get("exceptionClass"));
        assertEquals("", payload.get("exceptionMessage"));
        assertEquals("", payload.get("stackTrace"));
        assertTrue(((List<?>) payload.get("breadcrumbs")).isEmpty());
    }

    @Test
    public void stackTraceIsTrimmedPastTheLengthCap() {
        Throwable deep = deeplyRecursiveException(200);

        Map<String, Object> payload = ErrorReportPayload.build(AppLog.CRASH, "deep", deep, null, "2.0.4", true);

        String stackTrace = (String) payload.get("stackTrace");
        assertTrue("must be capped, not the full (much longer) trace",
                stackTrace.length() <= 4000 + "...[truncated]".length());
        assertTrue(stackTrace.endsWith("...[truncated]"));
    }

    private Throwable deeplyRecursiveException(int depth) {
        Throwable throwable = new RuntimeException("root cause");
        for (int i = 0; i < depth; i++) {
            throwable = new RuntimeException("wrapper-" + i, throwable);
        }
        return throwable;
    }
}
