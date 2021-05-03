package com.CadeMixedUpGame.api.models;

import androidx.annotation.Nullable;

public class User {
    public int userID;
    public String userName;
    public String gameRoom;
    public Boolean ifFinished;
    public String ifSentence;
    public Boolean thenFinished;
    public String thenSentence;

    public User(int userID, String userName) {
        this.userID = userID;
        this.userName = userName;
        this.gameRoom = "";
        this.ifFinished = false;
        this.thenFinished = false;
        ifSentence = "";
        thenSentence = "";
    }

//    @Override
//    public boolean equals(@Nullable Object obj) {
//        if (obj instanceof User) {
//            User user = (User) obj;
//            return user.userID.equals(userID);
//        }
//        return false;
//    }

}
