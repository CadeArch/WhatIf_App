package com.CadeMixedUpGame.api.models;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LeaderBoardItemTest {
    @Test
    public void constructorStoresSentenceContributorAndIdFields() {
        LeaderBoardItem item = new LeaderBoardItem(
                "if part",
                "then part",
                "If Writer",
                "Then Writer",
                "if-uid",
                "then-uid",
                "item-1");

        assertEquals("if part", item.getIfPart());
        assertEquals("then part", item.getThenPart());
        assertEquals("If Writer", item.getIfContributor());
        assertEquals("Then Writer", item.getThenContributor());
        assertEquals("if-uid", item.getIfContributorID());
        assertEquals("then-uid", item.getThenContributorID());
        assertEquals("item-1", item.getId());
        assertEquals(0, item.getLoadedToLeaderBoard());
        assertEquals(0.0, item.getPercentLoved(), 0.0);
    }

    @Test
    public void settersUpdateLeaderboardMetadataAndSentenceFields() {
        LeaderBoardItem item = new LeaderBoardItem();

        item.setIfPart("new if");
        item.setThenPart("new then");
        item.setIfContributor("Ada");
        item.setThenContributor("Grace");
        item.setIfContributorID("ada-id");
        item.setThenContributorID("grace-id");
        item.setId("winner");
        item.setLoadedToLeaderBoard(1234L);
        item.setPercentLoved(75.5);

        assertEquals("new if", item.getIfPart());
        assertEquals("new then", item.getThenPart());
        assertEquals("Ada", item.getIfContributor());
        assertEquals("Grace", item.getThenContributor());
        assertEquals("ada-id", item.getIfContributorID());
        assertEquals("grace-id", item.getThenContributorID());
        assertEquals("winner", item.getId());
        assertEquals(1234L, item.getLoadedToLeaderBoard());
        assertEquals(75.5, item.getPercentLoved(), 0.0);
    }
}
