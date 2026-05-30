package com.CadeMixedUpGame.api.models;

public class RoundAssignment {
    public String playerKey;
    public String ifOwnerKey;
    public String thenOwnerKey;
    public String ifContributor;
    public String thenContributor;
    public String ifContributorID;
    public String thenContributorID;
    public long seed;

    public RoundAssignment() {
    }

    public RoundAssignment(String playerKey,
                           String ifOwnerKey,
                           String thenOwnerKey,
                           String ifContributor,
                           String thenContributor,
                           String ifContributorID,
                           String thenContributorID,
                           long seed) {
        this.playerKey = playerKey;
        this.ifOwnerKey = ifOwnerKey;
        this.thenOwnerKey = thenOwnerKey;
        this.ifContributor = ifContributor;
        this.thenContributor = thenContributor;
        this.ifContributorID = ifContributorID;
        this.thenContributorID = thenContributorID;
        this.seed = seed;
    }
}
