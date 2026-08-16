package com.CadeMixedUpGame.phoneapp;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.ViewAssertion;

/**
 * "The lobby has at least N players in it" - shared by the two-device tests, which all have to wait
 * for the other device to actually arrive before the host may start.
 */
final class TwoDevicePlayerCount {
    private TwoDevicePlayerCount() {
    }

    static ViewAssertion atLeast(int expected) {
        return (view, exception) -> {
            if (exception != null) {
                throw exception;
            }
            RecyclerView recyclerView = (RecyclerView) view;
            int count = recyclerView.getAdapter() == null ? 0 : recyclerView.getAdapter().getItemCount();
            if (count < expected) {
                throw new RuntimeException("player count " + count + " has not yet reached " + expected);
            }
        };
    }
}
