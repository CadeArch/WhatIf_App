package com.CadeMixedUpGame.api.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class UserTest {
    @Test
    public void freePlayConstructorSetsExpectedDefaults() {
        User user = new User("Cade");

        assertEquals(0, user.userID);
        assertEquals("", user.uid);
        assertEquals("", user.email);
        assertEquals("Cade", user.userName);
        assertEquals("", user.gameRoom);
        assertFalse(user.ifFinished);
        assertFalse(user.thenFinished);
        assertFalse(user.accountPlay);
        assertEquals("", user.ifSentence);
        assertEquals("", user.thenSentence);
        assertFalse(user.host);
        assertFalse(user.playAgain);
        assertEquals("", user.hostPlayedAgain);
        assertTrue(user.connected);
        assertEquals(Long.valueOf(0L), user.disconnectedAt);
        assertFalse(user.madeLeaderBoard);
        assertFalse(user.perfectLeaderBoard);
    }

    @Test
    public void fieldsAreDirectlyMutable() {
        User user = new User("Cade");

        user.userID = 42;
        user.uid = "uid-1";
        user.email = "cade@example.com";
        user.userName = "New Name";
        user.gameRoom = "ROOM";
        user.ifFinished = true;
        user.thenFinished = true;
        user.accountPlay = true;
        user.ifSentence = "if this";
        user.thenSentence = "then that";
        user.host = true;
        user.playAgain = true;
        user.hostPlayedAgain = "yes";
        user.connected = false;
        user.disconnectedAt = 1234L;

        assertEquals(42, user.userID);
        assertEquals("uid-1", user.getUid());
        assertEquals("cade@example.com", user.email);
        assertEquals("New Name", user.userName);
        assertEquals("ROOM", user.gameRoom);
        assertTrue(user.ifFinished);
        assertTrue(user.thenFinished);
        assertTrue(user.accountPlay);
        assertEquals("if this", user.ifSentence);
        assertEquals("then that", user.thenSentence);
        assertTrue(user.host);
        assertTrue(user.playAgain);
        assertEquals("yes", user.hostPlayedAgain);
        assertFalse(user.connected);
        assertEquals(Long.valueOf(1234L), user.disconnectedAt);
    }

    @Test
    public void equalsUsesUserIdOnly() {
        User first = new User("First");
        User sameId = new User("Second");
        User differentId = new User("Third");

        first.userID = 7;
        sameId.userID = 7;
        differentId.userID = 8;

        assertEquals(first, sameId);
        assertNotEquals(first, differentId);
        assertNotEquals(first, "7");
    }

    @Test
    public void compareToUsesUserNameComparedToOtherUserIdString() {
        User user = new User("20");
        User other = new User("ignored");
        other.userID = 3;

        assertTrue(user.compareTo(other) < 0);
    }
}
