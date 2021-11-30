package com.CadeMixedUpGame.api.models;

public class LeaderBoardItem {
    String ifPart;
    String thenPart;
    String ifContributor;
    String thenContributor;
    String ifContributorID;
    String thenContributorID;
    String id;
    long loadedToLeaderBoard;
    double percentLoved;



    public LeaderBoardItem(String ifPart, String thenPart, String ifContributor, String thenContributor, String ifContributorID, String thenContributorID, String id) {
        this.ifPart = ifPart;
        this.thenPart = thenPart;
        this.ifContributor = ifContributor;
        this.thenContributor = thenContributor;
        this.ifContributorID = ifContributorID;
        this.thenContributorID = thenContributorID;
        this.id = id;
    }

    public LeaderBoardItem() {}

    public String getIfContributorID() {
        return ifContributorID;
    }

    public String getThenContributorID() {
        return thenContributorID;
    }

    public String getId() {
        return id;
    }

    public long getLoadedToLeaderBoard() {
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

    public void setId(String id) {
        this.id = id;
    }

    public void setIfContributor(String ifContributor) {
        this.ifContributor = ifContributor;
    }

    public void setIfPart(String ifPart) {
        this.ifPart = ifPart;
    }

    public void setLoadedToLeaderBoard(long loadedToLeaderBoard) {
        this.loadedToLeaderBoard = loadedToLeaderBoard;
    }

    public void setPercentLoved(double percentLoved) {
        this.percentLoved = percentLoved;
    }

    public void setThenContributor(String thenContributor) {
        this.thenContributor = thenContributor;
    }

    public void setThenPart(String thenPart) {
        this.thenPart = thenPart;
    }


    public void setIfContributorID(String ifContributorID) {
        this.ifContributorID = ifContributorID;
    }

    public void setThenContributorID(String thenContributorID) {
        this.thenContributorID = thenContributorID;
    }

}
