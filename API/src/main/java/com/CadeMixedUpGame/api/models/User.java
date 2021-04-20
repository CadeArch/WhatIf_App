package com.CadeMixedUpGame.api.models;

public class User {
    public String userID;
    public String userName;
    public String gameRoom;

    public User(String userID, String userName, String gameRoom) {
        this.userID = userID;
        this.userName = userName;
        this.gameRoom = gameRoom;
    }


}
