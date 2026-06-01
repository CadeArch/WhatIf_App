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


    public String getHostPlayedAgain() {
        return hostPlayedAgain;
    }

    public Boolean getConnected() {
        return connected;
    }

    public Long getDisconnectedAt() {
        return disconnectedAt;
    }

    public Boolean getAccountPlay() {
        return accountPlay;
    }

    public Boolean getIfFinished() {
        return ifFinished;
    }

    public Boolean getThenFinished() {
        return thenFinished;
    }

    public int getUserID() {
        return userID;
    }

    public String getEmail() {
        return email;
    }

    public String getGameRoom() {
        return gameRoom;
    }

    public String getIfSentence() {
        return ifSentence;
    }

    public String getThenSentence() {
        return thenSentence;
    }

    public String getUid() {
        return uid;
    }

    public String getUserName() {
        return userName;
    }

    public boolean getHost() {
        return host;
    }

    public boolean isPlayAgain() {
        return playAgain;
    }

    public void setAccountPlay(Boolean accountPlay) {
        this.accountPlay = accountPlay;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setGameRoom(String gameRoom) {
        this.gameRoom = gameRoom;
    }

    public void setHost(boolean host) {
        this.host = host;
    }

    public void setHostPlayedAgain(String hostPlayedAgain) {
        this.hostPlayedAgain = hostPlayedAgain;
    }

    public void setConnected(Boolean connected) {
        this.connected = connected;
    }

    public void setDisconnectedAt(Long disconnectedAt) {
        this.disconnectedAt = disconnectedAt;
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

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setUserID(int userID) {
        this.userID = userID;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setPlayAgain(boolean playAgain) {
        this.playAgain = playAgain;
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
        return this.userName.compareTo(String.valueOf(user.getUserID()));
    }

}
