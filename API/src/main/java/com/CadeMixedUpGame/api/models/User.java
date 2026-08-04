package com.CadeMixedUpGame.api.models;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;

public class User implements Comparable<User>{
    public int userID;
    public String uid;
    public String email;
    public String userName;
    public String gameRoom;
    public Boolean ifFinished;
    public Boolean thenFinished;
    public Boolean accountPlay;
    public String ifSentence;
    public String thenSentence;
    public boolean host;
    public boolean playAgain;
    public String hostPlayedAgain;
    public Boolean connected;
    public Long disconnectedAt;
    public int gamesPlayed;
    public boolean madeLeaderBoard;
    public boolean perfectLeaderBoard;

    public User(FirebaseUser user, String userName) {
        this.userID = 0;
        this.uid = user.getUid();
        this.email = user.getEmail();
        this.userName = userName;
        this.gameRoom = "";
        this.ifFinished = false;
        this.thenFinished = false;
        this.accountPlay = true;
        this.ifSentence = "";
        this.thenSentence = "";
        this.host = false;
        this.playAgain = false;
        this.hostPlayedAgain = "";
        this.connected = true;
        this.disconnectedAt = 0L;
        this.madeLeaderBoard = true;
        this.perfectLeaderBoard = true;
    }

    public User(String userName) {
        this.userID = 0;
        this.uid = "";
        this.email = "";
        this.userName = userName;
        this.gameRoom = "";
        this.ifFinished = false;
        this.thenFinished = false;
        this.accountPlay = false;
        this.ifSentence = "";
        this.thenSentence = "";
        this.host = false;
        this.playAgain = false;
        this.hostPlayedAgain = "";
        this.connected = true;
        this.disconnectedAt = 0L;
        this.madeLeaderBoard = false;
        this.perfectLeaderBoard = false;
    }

    public User() {}

    // Only the accessors actually called anywhere in the app are kept below - grep confirmed
    // every other getter/setter that used to live here had zero callers (all real code reads/
    // writes the public fields above directly), so they were dead API surface rather than the
    // real encapsulation boundary. See CLAUDE.md Part 2 §6 for the guidance this follows: new
    // model classes should use private fields + getters/setters only, but a repo-wide
    // privatization pass on existing models is a separate, deliberate, higher-risk change (every
    // direct field access site would need to change), not bundled into a dead-code cleanup.

    public String getUid() {
        return uid;
    }

    public void setHostPlayedAgain(String hostPlayedAgain) {
        this.hostPlayedAgain = hostPlayedAgain;
    }

    public void setIfFinished(Boolean ifFinished) {
        this.ifFinished = ifFinished;
    }

    public void setIfSentence(String ifSentence) {
        this.ifSentence = ifSentence;
    }

    public void setThenFinished(Boolean thenFinished) {
        this.thenFinished = thenFinished;
    }

    public void setThenSentence(String thenSentence) {
        this.thenSentence = thenSentence;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof User) {
            User user = (User) obj;
            return user.userID == userID;
        }
        return false;
    }

    @Override
    public int compareTo(User user) {
        return this.userName.compareTo(String.valueOf(user.userID));
    }

}
