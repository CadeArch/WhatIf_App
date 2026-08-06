package com.CadeMixedUpGame.phoneapp;

import static org.junit.Assert.fail;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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
    private static final long POLL_INTERVAL_MS = 300L;
    private static final long FIREBASE_CALL_TIMEOUT_SECONDS = 5L;

    private E2ERoomCodeSignal() {
    }

    static void publish(String correlationId, String roomCode) {
        CountDownLatch latch = new CountDownLatch(1);
        signalRef(correlationId).setValue(roomCode).addOnCompleteListener(task -> latch.countDown());
        awaitQuietly(latch, FIREBASE_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    static String awaitRoomCode(String correlationId, long timeoutMs) {
        DatabaseReference ref = signalRef(correlationId);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String value = readOnce(ref);
            if (value != null && value.length() > 0) {
                return value;
            }
            sleepQuietly();
        }
        fail("Timed out after " + timeoutMs + "ms waiting for a roomCode signal at e2eSignals/" + correlationId);
        return null; // unreachable - fail() throws
    }

    private static String readOnce(DatabaseReference ref) {
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        ref.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                result.set(task.getResult().getValue(String.class));
            }
            latch.countDown();
        });
        awaitQuietly(latch, FIREBASE_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return result.get();
    }

    private static DatabaseReference signalRef(String correlationId) {
        return FirebaseDatabase.getInstance().getReference()
                .child("e2eSignals").child(correlationId).child("roomCode");
    }

    private static void awaitQuietly(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            latch.await(timeout, unit);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
