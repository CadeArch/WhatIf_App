package com.CadeMixedUpGame.api.viewmodels;

import androidx.databinding.ObservableArrayList;

import com.CadeMixedUpGame.api.models.LeaderBoardItem;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LeaderBoardViewModelTest {
    @Test
    public void injectedConstructorInitializesCollectionsWithoutLoadingFirebase() {
        LeaderBoardViewModel viewModel = new LeaderBoardViewModel(null);

        assertEquals(0, viewModel.getLeaderBoard().size());
        assertEquals(0, viewModel.getPotentialLeaderBoardItems().size());
        assertEquals(0, viewModel.getCastvotes().size());
    }

    @Test
    public void resetClearsVotingStateCollections() {
        LeaderBoardViewModel viewModel = new LeaderBoardViewModel(null);
        viewModel.getPotentialLeaderBoardItems().add(item("vote-a", 10));
        viewModel.getCastvotes().add("vote-a");

        viewModel.reset();

        assertEquals(0, viewModel.getPotentialLeaderBoardItems().size());
        assertEquals(0, viewModel.getCastvotes().size());
    }

    @Test
    public void isLeaderBoardFullUsesTwentyItemLimit() {
        LeaderBoardViewModel viewModel = new LeaderBoardViewModel(null);

        assertFalse(viewModel.isLeaderBoardFull());

        for (int index = 0; index < 20; index++) {
            viewModel.getLeaderBoard().add(item("item-" + index, index));
        }

        assertTrue(viewModel.isLeaderBoardFull());
    }

    @Test
    public void removeWhichItemReturnsNewItemWhenItDoesNotBeatExistingBoard() {
        LeaderBoardViewModel viewModel = new LeaderBoardViewModel(null);
        LeaderBoardItem existing = item("existing", 75);
        LeaderBoardItem newItem = item("new", 50);
        viewModel.setLeaderBoard(list(existing));

        assertSame(newItem, viewModel.removeWhichItem(newItem));
    }

    @Test
    public void removeWhichItemReturnsFirstItemBeatenByNewItem() {
        LeaderBoardViewModel viewModel = new LeaderBoardViewModel(null);
        LeaderBoardItem high = item("high", 95);
        LeaderBoardItem beaten = item("beaten", 80);
        LeaderBoardItem newItem = item("new", 85);
        viewModel.setLeaderBoard(list(high, beaten));

        assertSame(beaten, viewModel.removeWhichItem(newItem));
    }

    private ObservableArrayList<LeaderBoardItem> list(LeaderBoardItem... items) {
        ObservableArrayList<LeaderBoardItem> list = new ObservableArrayList<LeaderBoardItem>();
        for (LeaderBoardItem item : items) {
            list.add(item);
        }
        return list;
    }

    private LeaderBoardItem item(String id, double percentLoved) {
        LeaderBoardItem item = new LeaderBoardItem("What if test?", "then test.", "if", "then", "if-id", "then-id", id);
        item.setPercentLoved(percentLoved);
        return item;
    }
}
