package com.CadeMixedUpGame.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class AppLogTest {

    private static class RecordingReporter implements AppLog.ErrorReporter {
        String tag;
        String message;
        Throwable throwable;
        List<String> breadcrumbs;
        int callCount = 0;

        @Override
        public void onError(String tag, String message, Throwable throwable, List<String> breadcrumbs) {
            this.tag = tag;
            this.message = message;
            this.throwable = throwable;
            this.breadcrumbs = breadcrumbs;
            callCount++;
        }
    }

    @Before
    public void setUp() {
        AppLog.resetForTest();
    }

    @After
    public void tearDown() {
        AppLog.resetForTest();
    }

    @Test
    public void breadcrumbsAccumulateAcrossAllLevels() {
        AppLog.d(AppLog.ROOM, "debug line");
        AppLog.i(AppLog.GAME_FLOW, "info line");
        AppLog.w(AppLog.VOTE, "warn line");
        AppLog.e(AppLog.FIREBASE, "error line");

        List<String> breadcrumbs = AppLog.snapshotBreadcrumbs();
        assertEquals(4, breadcrumbs.size());
        assertTrue(breadcrumbs.get(0).contains("debug line"));
        assertTrue(breadcrumbs.get(3).contains("error line"));
    }

    @Test
    public void breadcrumbBufferIsCappedAndDropsOldestFirst() {
        for (int i = 0; i < 50; i++) {
            AppLog.i(AppLog.UI, "line-" + i);
        }
        List<String> breadcrumbs = AppLog.snapshotBreadcrumbs();
        assertEquals(40, breadcrumbs.size());
        assertTrue("oldest lines must have been dropped", breadcrumbs.get(0).contains("line-10"));
        assertTrue("newest line must still be present", breadcrumbs.get(39).contains("line-49"));
    }

    @Test
    public void snapshotBreadcrumbsReturnsADefensiveCopy() {
        AppLog.i(AppLog.UI, "first");
        List<String> snapshot = AppLog.snapshotBreadcrumbs();
        AppLog.i(AppLog.UI, "second");

        assertEquals(1, snapshot.size());
        assertEquals(2, AppLog.snapshotBreadcrumbs().size());
    }

    @Test
    public void errorWithThrowableInvokesTheReporterWithBreadcrumbsAndThrowable() {
        RecordingReporter reporter = new RecordingReporter();
        AppLog.setErrorReporter(reporter);
        AppLog.i(AppLog.ROOM, "leading up to it");
        RuntimeException thrown = new RuntimeException("boom");

        AppLog.e(AppLog.FIREBASE, "write failed", thrown);

        assertEquals(1, reporter.callCount);
        assertEquals(AppLog.FIREBASE, reporter.tag);
        assertEquals("write failed", reporter.message);
        assertEquals(thrown, reporter.throwable);
        assertTrue(reporter.breadcrumbs.stream().anyMatch(line -> line.contains("leading up to it")));
    }

    @Test
    public void errorWithoutAThrowableDoesNotInvokeTheReporter() {
        RecordingReporter reporter = new RecordingReporter();
        AppLog.setErrorReporter(reporter);

        AppLog.e(AppLog.FIREBASE, "no exception here");

        assertEquals(0, reporter.callCount);
    }

    @Test
    public void debugInfoAndWarnNeverInvokeTheReporter() {
        RecordingReporter reporter = new RecordingReporter();
        AppLog.setErrorReporter(reporter);

        AppLog.d(AppLog.ROOM, "d");
        AppLog.i(AppLog.ROOM, "i");
        AppLog.w(AppLog.ROOM, "w");

        assertEquals(0, reporter.callCount);
    }

    @Test
    public void aThrowingReporterDoesNotBreakLogging() {
        AppLog.setErrorReporter((tag, message, throwable, breadcrumbs) -> {
            throw new RuntimeException("reporter itself is broken");
        });

        // Must not throw out of AppLog.e despite the reporter blowing up.
        AppLog.e(AppLog.FIREBASE, "still logs fine", new RuntimeException("real error"));

        assertTrue(AppLog.snapshotBreadcrumbs().get(0).contains("still logs fine"));
    }

    @Test
    public void noReporterSetIsSafe() {
        // resetForTest() already cleared any reporter - just confirm logging with a throwable
        // doesn't NPE when no reporter has been set.
        AppLog.e(AppLog.FIREBASE, "no reporter configured", new RuntimeException("x"));
        assertFalse(AppLog.snapshotBreadcrumbs().isEmpty());
    }
}
