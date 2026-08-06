package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.test.core.app.ApplicationProvider;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.FirebaseEmulatorConfig;
import com.CadeMixedUpGame.api.FirebaseErrorReporter;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Proves the auto error-log pipeline actually delivers end to end against a real Firebase
 * connection, not just that each piece (AppLog's breadcrumb/reporter hook, ErrorReportPayload,
 * FirebaseErrorReporter's write) works in isolation - a real AppLog.e(tag, msg, throwable) call
 * anywhere in the app now lands a genuine entry under errorLogs/. Same harness/pattern as
 * MultiplayerEmulatorTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ErrorLogEmulatorTest {
    private static final int WAIT_SECONDS = 10;

    private FirebaseApp app;
    private DatabaseReference db;

    @Before
    public void setUp() throws InterruptedException {
        assumeTrue("Firebase Emulator Suite not running on 127.0.0.1:9000 - start it with "
                        + "`firebase emulators:start --only database,auth` to run this test",
                emulatorReachable());

        FirebaseOptions options = new FirebaseOptions.Builder()
                .setApiKey("fake-api-key")
                .setApplicationId("1:0:android:0")
                .setProjectId("demo-mixedupgame")
                .setDatabaseUrl("https://demo-mixedupgame-default-rtdb.firebaseio.com")
                .build();

        app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), options, "errlog-" + System.nanoTime());
        FirebaseEmulatorConfig.configureIfEnabled(app, "localhost", true);
        db = FirebaseDatabase.getInstance(app).getReference();

        AppLog.resetForTest();

        // errorLogs is a global (non-room-scoped) node like leaderBoard, and the local emulator's
        // data persists across separate test methods within a session - wipe it first so the
        // session-cap test isn't polluted by entries the other test method already wrote.
        CountDownLatch cleared = new CountDownLatch(1);
        db.child("errorLogs").removeValue().addOnCompleteListener(t -> cleared.countDown());
        awaitLatchQuietly(cleared);
    }

    @After
    public void tearDown() {
        AppLog.resetForTest();
        if (app != null) {
            app.delete();
        }
    }

    private boolean emulatorReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 9000), 1000);
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    @Test
    public void loggingARealExceptionDeliversAnEntryUnderErrorLogs() throws InterruptedException {
        AppLog.setErrorReporter(new FirebaseErrorReporter(db, "test-version", 25));
        AppLog.i(AppLog.ROOM, "breadcrumb before the failure");

        AppLog.e(AppLog.FIREBASE, "simulated write failure", new RuntimeException("boom"));

        AtomicReference<DataSnapshot> found = new AtomicReference<>();
        waitUntil(() -> {
            CountDownLatch read = new CountDownLatch(1);
            db.child("errorLogs").limitToLast(1).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null && task.getResult().getChildrenCount() > 0) {
                    found.set(task.getResult().getChildren().iterator().next());
                }
                read.countDown();
            });
            awaitLatchQuietly(read);
            return found.get() != null;
        });

        assertTrue("an entry must have landed under errorLogs/", found.get() != null);
        DataSnapshot entry = found.get();
        assertEquals(AppLog.FIREBASE, entry.child("tag").getValue(String.class));
        assertEquals("simulated write failure", entry.child("message").getValue(String.class));
        assertEquals("java.lang.RuntimeException", entry.child("exceptionClass").getValue(String.class));
        assertEquals(false, entry.child("fatal").getValue(Boolean.class));
        assertTrue("breadcrumbs must include the log line before the failure",
                entry.child("breadcrumbs").getChildren().iterator().next().getValue(String.class).contains("breadcrumb before the failure"));
    }

    @Test
    public void reachingTheSessionCapStopsFurtherWrites() throws InterruptedException {
        AppLog.setErrorReporter(new FirebaseErrorReporter(db, "test-version", 2));

        AppLog.e(AppLog.FIREBASE, "error 1", new RuntimeException("1"));
        AppLog.e(AppLog.FIREBASE, "error 2", new RuntimeException("2"));
        AppLog.e(AppLog.FIREBASE, "error 3 - should be dropped", new RuntimeException("3"));

        waitUntil(() -> countErrorLogs() >= 2);
        // Give the (correctly non-firing) third write every chance to wrongly land before asserting.
        Thread.sleep(500);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("writes past the session cap must be dropped", 2, countErrorLogs());
    }

    private int countErrorLogs() {
        AtomicReference<Integer> count = new AtomicReference<>(-1);
        CountDownLatch read = new CountDownLatch(1);
        db.child("errorLogs").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                count.set((int) task.getResult().getChildrenCount());
            }
            read.countDown();
        });
        awaitLatchQuietly(read);
        return count.get();
    }

    private void awaitLatchQuietly(CountDownLatch latch) {
        try {
            long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
            while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                Thread.sleep(50);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private interface Condition {
        boolean isMet();
    }

    private void waitUntil(Condition condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
        while (!condition.isMet() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(50);
        }
    }
}
