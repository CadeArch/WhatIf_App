package com.CadeMixedUpGame.api;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RoomCreationPolicyTest {
    @Test
    public void roomCreationRetriesCollisionsBeforeFinalAttempt() {
        assertTrue(RoomCreationPolicy.shouldRetry(0, false, false));
        assertTrue(RoomCreationPolicy.shouldRetry(RoomCreationPolicy.MAX_CREATE_ATTEMPTS - 2, false, false));
    }

    @Test
    public void roomCreationStopsAfterSuccessOrFinalAttempt() {
        assertFalse(RoomCreationPolicy.shouldRetry(0, true, false));
        assertFalse(RoomCreationPolicy.shouldRetry(RoomCreationPolicy.MAX_CREATE_ATTEMPTS - 1, false, false));
    }

    @Test
    public void roomCreationRetriesFirebaseErrorsBeforeFinalAttempt() {
        assertTrue(RoomCreationPolicy.shouldRetry(0, false, true));
        assertFalse(RoomCreationPolicy.shouldRetry(RoomCreationPolicy.MAX_CREATE_ATTEMPTS - 1, false, true));
    }
}
