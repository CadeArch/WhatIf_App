package com.CadeMixedUpGame.api.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public class RoomTest {
    @Test
    public void constructorCreatesRoomThatIsNotInProgress() {
        Room room = new Room("AB12");

        assertEquals("AB12", room.roomID);
        assertFalse(room.gameInProgress);
    }

    @Test
    public void equalsUsesRoomIdOnly() {
        Room first = new Room("AB12");
        Room sameId = new Room("AB12");
        Room differentId = new Room("CD34");

        first.gameInProgress = true;

        assertEquals(first, sameId);
        assertNotEquals(first, differentId);
        assertNotEquals(first, "AB12");
    }
}
