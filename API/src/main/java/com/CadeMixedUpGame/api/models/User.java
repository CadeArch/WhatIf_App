package com.CadeMixedUpGame.api.models;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;

public class User {
    public int userID;
    public Boolean isAccountPlay = false;
    public String userName;
    public String gameRoom;
    public Boolean ifFinished;
    public String ifSentence;
    public Boolean thenFinished;
    public String thenSentence;
    public boolean host;
    public boolean hostStarted;
    // if i have this do i need the top userID
    public String uid;
    public String email;

    public User(FirebaseUser user, String userName) {
        this.uid = user.getUid();
        this.email = user.getEmail();
        this.userID = 0;
        this.userName = userName;
        this.gameRoom = "";
        this.ifFinished = false;
        this.thenFinished = false;
        ifSentence = "";
        thenSentence = "";
        this.host = false;
        this.hostStarted = false;
    }

    public User(String userName) {
        this.userID = 0;
        this.userName = userName;
        this.gameRoom = "";
        this.ifFinished = false;
        this.thenFinished = false;
        ifSentence = "";
        thenSentence = "";
        this.host = false;
        this.hostStarted = false;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof User) {
            User user = (User) obj;
            return user.userID == userID;
        }
        return false;
    }

}
