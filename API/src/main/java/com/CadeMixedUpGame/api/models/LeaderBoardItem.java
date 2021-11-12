package com.CadeMixedUpGame.api.models;

public class LeaderBoardItem {
    String ifPart;
    String thenPart;
    String ifContributor;
    String thenContributor;
    String id;
    int loadedToLeaderBoard;
    double percentLoved;



    public LeaderBoardItem(String ifPart, String thenPart, String ifContributor, String thenContributor, String id) {
        this.ifPart = ifPart;
        this.thenPart = thenPart;
        this.ifContributor = ifContributor;
        this.thenContributor = thenContributor;
        this.id = id;
    }

    public LeaderBoardItem() {}

    public String getId() {
        return id;
    }

    public int getLoadedToLeaderBoard() {
        return loadedToLeaderBoard;
    }

    public String getIfContributor() {
        return ifContributor;
    }

    public String getIfPart() {
        return ifPart;
    }

    public String getThenContributor() {
        return thenContributor;
    }

    public String getThenPart() {
        return thenPart;
    }

    public double getPercentLoved() {
        return percentLoved;
    }
}
