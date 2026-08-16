package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.fail;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.CadeMixedUpGame.api.AppLog;

import com.google.firebase.FirebaseApp;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-process room-code handoff for Tier B tests, via a Firebase location both host and guest
 * devices already have a working connection to (the same local Firebase Emulator Suite the app
 * itself uses - WhatIfApplication.onCreate() already points FirebaseDatabase.getInstance() at
 * 10.0.2.2:9000 when built with -PuseFirebaseEmulator=true, so no extra setup is needed here).
 *
 * Supersedes an earlier logcat-tag-based signal: that design forced scripts/run-tier-b.ps1 to wait
 * for the host to generate a room code *before even launching the guest process*, serializing the
 * guest's own app-launch/name-entry/navigate-to-join steps after the host's room-creation steps
 * for no real reason - none of that depends on knowing the code until the very last "type it in
 * and submit" step. This lets the orchestration script launch both roles at the same time; the
 * guest does its own independent navigation up to JoinGameFrag, then waits here.
 */
final class E2ERoomCodeSignal {
    /**
     * Generous on purpose. The first call through this class has to bring up a brand-new websocket
     * for its own FirebaseApp, which took well over 5s on a loaded machine - a tighter budget
     * turned "still connecting" into a hard failure and made the harness look broken when it was
     * merely slow. Once connected, writes complete in ~12ms.
     */
    private static final long FIREBASE_CALL_TIMEOUT_SECONDS = 45L;

    private E2ERoomCodeSignal() {
    }

    /**
     * Fails the host's own test if the handoff write did not land. This used to ignore the task
     * result, which meant a rejected write (the realistic case: the Emulator Suite was started with
     * the production rules, where e2eSignals is deliberately not allow-listed) produced no error at
     * all on the host - the only symptom was the *guest* timing out 20s later waiting for a code
     * that was never stored, on a different device, pointing at nothing in particular.
     */
    static void publish(String correlationId, String roomCode) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        DatabaseReference ref = signalRef(correlationId);
        AppLog.i(AppLog.FIREBASE, "E2E host publishing roomCode=" + roomCode + " ref=" + ref);
        ref.setValue(roomCode).addOnCompleteListener(task -> {
            AppLog.i(AppLog.FIREBASE, "E2E host publish complete success=" + task.isSuccessful());
            if (!task.isSuccessful()) {
                failure.set(task.getException());
            }
            latch.countDown();
        });
        boolean completed = awaitQuietly(latch, FIREBASE_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            fail("Timed out writing the roomCode signal to e2eSignals/" + correlationId
                    + " - the database emulator did not answer within " + FIREBASE_CALL_TIMEOUT_SECONDS + "s.");
        }
        if (failure.get() != null) {
            fail(handoffFailureMessage("Failed writing the roomCode signal to e2eSignals/"
                    + correlationId, failure.get()));
        }
    }

    /**
     * Waits for the host's room code using a value listener rather than polling {@code get()}.
     *
     * <p>This used to poll {@code ref.get()} every 300ms, which fails in a way that looks exactly
     * like the host never writing: {@code get()} is documented to serve a *cached* value instead of
     * always round-tripping, so once the first poll cached "nothing here" - and the guest reaches
     * this point a second or two before the host finishes reserving its room, so the first poll
     * always misses - every later poll could return that same empty snapshot while the real value
     * sat in the database the whole time. Verified exactly that: the host wrote
     * {@code roomCode=mall-mule} at 16:52:11.2 and the guest, polling from 16:52:09 to 16:52:29,
     * never saw it, while a REST read showed it present.
     *
     * <p>A listener has no such hazard - the server pushes the value when it lands, and it fires
     * immediately with whatever is already there if the host got in first.
     */
    static String awaitRoomCode(String correlationId, long timeoutMs) {
        DatabaseReference ref = signalRef(correlationId);
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<DatabaseError> readError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String value = snapshot.getValue(String.class);
                AppLog.i(AppLog.FIREBASE, "E2E guest onDataChange exists=" + snapshot.exists() + " value=" + value);
                if (value != null && value.length() > 0) {
                    result.set(value);
                    latch.countDown();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                AppLog.e(AppLog.FIREBASE, "E2E guest listener cancelled: " + error.getMessage(), error.toException());
                readError.set(error);
                latch.countDown();
            }
        };
        // An initial onDataChange proves nothing about connectivity - Firebase fires it straight
        // away from cache, so an unconnected client reports exists=false exactly like a connected
        // one looking at an empty path. Watch this client's own .info/connected to tell them apart.
        ValueEventListener connectionProbe = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                AppLog.i(AppLog.FIREBASE, "E2E guest signal-client connected=" + snapshot.getValue(Boolean.class));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                AppLog.e(AppLog.FIREBASE, "E2E guest .info/connected cancelled: " + error.getMessage(), error.toException());
            }
        };
        DatabaseReference infoRef = signalDatabase().getReference(".info/connected");
        infoRef.addValueEventListener(connectionProbe);

        AppLog.i(AppLog.FIREBASE, "E2E guest attaching roomCode listener ref=" + ref);
        ref.addValueEventListener(listener);
        try {
            awaitQuietly(latch, timeoutMs, TimeUnit.MILLISECONDS);
        }
        finally {
            ref.removeEventListener(listener);
            infoRef.removeEventListener(connectionProbe);
        }

        if (readError.get() != null) {
            fail(handoffFailureMessage("Reading the roomCode signal at e2eSignals/" + correlationId
                    + " was cancelled", readError.get().toException()));
        }
        String value = result.get();
        if (value == null || value.length() == 0) {
            fail("Timed out after " + timeoutMs + "ms waiting for a roomCode signal at e2eSignals/"
                    + correlationId + ". The listener was attached and never cancelled, so the host "
                    + "never wrote it - check the host's result, which fails directly if its write "
                    + "was rejected.");
        }
        return value;
    }

    /** Names the overwhelmingly likely cause, since a bare Firebase message rarely does. */
    private static String handoffFailureMessage(String context, Exception cause) {
        return context + ": " + cause
                + "\nIf this is a permission error, the Firebase Emulator Suite is running with the"
                + " production rules (database.rules.json), which deliberately do not allow-list"
                + " e2eSignals. Restart it with:"
                + "\n  firebase emulators:start --config firebase.emulator.json --only database,auth";
    }


    /**
     * The one root reference this class ever creates. Every call to {@code getReference()} on the
     * same FirebaseApp hands back a *different* root object, and only the first one reliably
     * delivers callbacks - a write made through a later one still reaches the database, but its
     * completion listener never fires, and a listener attached to it never receives the value.
     * That is exactly how this handoff failed: the room code was sitting in the emulator (confirmed
     * by REST) while the host's write "hung" and the guest's listener stayed silent.
     *
     * <p>The repo's testing notes already recorded this hazard for the Robolectric suites; it is
     * not Robolectric-specific, it bites on-device too. Create the root once, derive every path
     * from it with {@code child(...)}.
     */
    private static final String SIGNAL_APP_NAME = "e2e-room-code-signal";
    private static DatabaseReference root;

    /**
     * Uses a FirebaseApp of its own rather than the app's default one, and creates exactly one root
     * reference on it.
     *
     * <p>Both halves matter, and both were learned from this handoff failing. Every
     * {@code getReference()} call on a FirebaseApp returns a *different* root object, and only the
     * first behaves: on the app's default FirebaseApp the UI has already taken that first root, so
     * a root created here is a later one - its writes reach the database but their completion
     * listener never fires, and a listener attached to it receives its initial snapshot and then
     * never a single server push. Both symptoms were observed directly: the guest sat on
     * {@code exists=false} for 20s while REST showed the host's value present the whole time.
     */
    private static synchronized DatabaseReference root() {
        if (root == null) {
            FirebaseApp defaultApp = FirebaseApp.getInstance();
            FirebaseApp signalApp;
            try {
                signalApp = FirebaseApp.getInstance(SIGNAL_APP_NAME);
            }
            catch (IllegalStateException notCreatedYet) {
                signalApp = FirebaseApp.initializeApp(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        defaultApp.getOptions(),
                        SIGNAL_APP_NAME);
            }
            signalDatabase = FirebaseDatabase.getInstance(signalApp);
            root = signalDatabase.getReference();
        }
        return root;
    }

    private static FirebaseDatabase signalDatabase;

    private static synchronized FirebaseDatabase signalDatabase() {
        root();
        return signalDatabase;
    }

    private static DatabaseReference signalRef(String correlationId) {
        return root().child("e2eSignals").child(correlationId).child("roomCode");
    }

    /** @return true if the latch actually counted down, false if the wait timed out. */
    private static boolean awaitQuietly(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            return latch.await(timeout, unit);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
