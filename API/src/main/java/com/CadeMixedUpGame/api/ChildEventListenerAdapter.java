package com.CadeMixedUpGame.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;

/**
 * No-op default for all five ChildEventListener callbacks, so a listener that only cares about
 * one or two of them (the common case in this codebase) overrides just those, instead of
 * repeating empty method bodies for the rest. Also centralizes the "listener cancelled" log line.
 */
public abstract class ChildEventListenerAdapter implements ChildEventListener {
    private final String logTag;
    private final String cancelledMessage;

    protected ChildEventListenerAdapter(String logTag, String cancelledMessage) {
        this.logTag = logTag;
        this.cancelledMessage = cancelledMessage;
    }

    @Override
    public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
    }

    @Override
    public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
    }

    @Override
    public void onChildRemoved(@NonNull DataSnapshot snapshot) {
    }

    @Override
    public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
    }

    @Override
    public void onCancelled(@NonNull DatabaseError error) {
        AppLog.e(logTag, cancelledMessage + ": " + error.getMessage());
    }
}
