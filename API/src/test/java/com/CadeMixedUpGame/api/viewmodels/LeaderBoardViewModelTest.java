package com.CadeMixedUpGame.api.viewmodels;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LeaderBoardViewModelTest {
    @Test
    public void injectedConstructorInitializesCollectionsWithoutLoadingFirebase() {
        LeaderBoardViewModel viewModel = new LeaderBoardViewModel(null);

        assertEquals(0, viewModel.getLeaderBoard().size());
        assertEquals(0, viewModel.getPotentialLeaderBoardItems().size());
        assertEquals(0, viewModel.getCastvotes().size());
    }
}
