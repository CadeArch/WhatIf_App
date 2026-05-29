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

        assertEquals(0, user.getUserID());
        assertEquals("", user.getUid());
        assertEquals("", user.getEmail());
        assertEquals("Cade", user.getUserName());
        assertEquals("", user.getGameRoom());
        assertFalse(user.getIfFinished());
        assertFalse(user.getThenFinished());
        assertFalse(user.getAccountPlay());
        assertEquals("", user.getIfSentence());
        assertEquals("", user.getThenSentence());
        assertFalse(user.getHost());
        assertFalse(user.getHostStarted());
        assertFalse(user.isPlayAgain());
        assertEquals("", user.getHostPlayedAgain());
        assertFalse(user.madeLeaderBoard);
        assertFalse(user.perfectLeaderBoard);
    }

    @Test
    public void settersUpdateUserState() {
        User user = new User("Cade");

        user.setUserID(42);
        user.setUid("uid-1");
        user.setEmail("cade@example.com");
        user.setUserName("New Name");
        user.setGameRoom("ROOM");
        user.setIfFinished(true);
        user.setThenFinished(true);
        user.setAccountPlay(true);
        user.setIfSentence("if this");
        user.setThenSentence("then that");
        user.setHost(true);
        user.setHostStarted(true);
        user.setPlayAgain(true);
        user.setHostPlayedAgain("yes");

        assertEquals(42, user.getUserID());
        assertEquals("uid-1", user.getUid());
        assertEquals("cade@example.com", user.getEmail());
        assertEquals("New Name", user.getUserName());
        assertEquals("ROOM", user.getGameRoom());
        assertTrue(user.getIfFinished());
        assertTrue(user.getThenFinished());
        assertTrue(user.getAccountPlay());
        assertEquals("if this", user.getIfSentence());
        assertEquals("then that", user.getThenSentence());
        assertTrue(user.getHost());
        assertTrue(user.getHostStarted());
        assertTrue(user.isPlayAgain());
        assertEquals("yes", user.getHostPlayedAgain());
    }

    @Test
    public void equalsUsesUserIdOnly() {
        User first = new User("First");
        User sameId = new User("Second");
        User differentId = new User("Third");

        first.setUserID(7);
        sameId.setUserID(7);
        differentId.setUserID(8);

        assertEquals(first, sameId);
        assertNotEquals(first, differentId);
        assertNotEquals(first, "7");
    }

    @Test
    public void compareToUsesUserNameComparedToOtherUserIdString() {
        User user = new User("20");
        User other = new User("ignored");
        other.setUserID(3);

        assertTrue(user.compareTo(other) < 0);
    }
}
