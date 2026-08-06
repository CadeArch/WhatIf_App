package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.CadeMixedUpGame.api.ErrorReportPayload;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PendingCrashReportStoreTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void readAndClearPendingReturnsNullWhenNoFileExists() {
        assertNull(PendingCrashReportStore.readAndClearPending(context));
    }

    @Test
    public void writeThenReadRoundTripsTheSamePayload() {
        Map<String, Object> payload = ErrorReportPayload.build(
                "MU.Crash", "Fatal: main", new RuntimeException("boom"),
                Arrays.asList("I/MU.Room: joined room", "I/MU.UI: Screen shown: EndFrag"),
                "2.0.4", true);

        PendingCrashReportStore.writePending(context, payload);
        Map<String, Object> read = PendingCrashReportStore.readAndClearPending(context);

        assertEquals(payload.get("tag"), read.get("tag"));
        assertEquals(payload.get("message"), read.get("message"));
        assertEquals(payload.get("exceptionClass"), read.get("exceptionClass"));
        assertEquals(payload.get("exceptionMessage"), read.get("exceptionMessage"));
        assertEquals(payload.get("appVersion"), read.get("appVersion"));
        assertEquals(payload.get("fatal"), read.get("fatal"));
        //noinspection unchecked
        List<Object> breadcrumbs = (List<Object>) read.get("breadcrumbs");
        assertEquals(2, breadcrumbs.size());
        assertEquals("I/MU.Room: joined room", breadcrumbs.get(0));
    }

    @Test
    public void readAndClearPendingDeletesTheFileAfterReading() {
        Map<String, Object> payload = ErrorReportPayload.build("MU.Crash", "x", null, null, "1.0", true);
        PendingCrashReportStore.writePending(context, payload);

        File file = new File(context.getFilesDir(), "pending_crash_report.json");
        assertTrue("file must exist right after writing", file.exists());

        PendingCrashReportStore.readAndClearPending(context);

        assertTrue("file must be deleted after being read", !file.exists());
        assertNull("a second read must find nothing pending", PendingCrashReportStore.readAndClearPending(context));
    }

    @Test
    public void writePendingWithNullPayloadDoesNotThrow() {
        PendingCrashReportStore.writePending(context, null);
        assertNull(PendingCrashReportStore.readAndClearPending(context));
    }
}
