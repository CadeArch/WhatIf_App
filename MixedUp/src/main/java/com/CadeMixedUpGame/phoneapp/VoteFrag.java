package com.CadeMixedUpGame.phoneapp;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.models.LeaderBoardItem;
import com.CadeMixedUpGame.api.viewmodels.LeaderBoardViewModel;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class VoteFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    LeaderBoardViewModel leaderBoardViewModel;
    int sentencesSelected = 0;
    static final int SELECTED_COLOR = 2012063468;
    private View submitButton;
    private boolean voteSubmitting = false;

    public VoteFrag() {
        super(R.layout.fragment_vote);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViewModels();
        userViewModel.gamePhase.setValue(GamePhase.VOTING);
        leaderBoardViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showBanner(view, message, UiMessenger.MessageType.ERROR);
                leaderBoardViewModel.databaseMessage.setValue("");
                setVoteSubmitting(false);
            }
        });

        leaderBoardViewModel.loadVotingItems(userViewModel.getUser());
        leaderBoardViewModel.createAndListenToCastVotes(userViewModel.myRoom);

        LinearLayout potentialLBIlist = view.findViewById(R.id.potential_lbiList);

        setupVotingItemList(potentialLBIlist);

        if (userViewModel.getUser().getValue().host) {
            leaderBoardViewModel.castVoteListener(userViewModel.getUsers().size());
        }


        //giving vote button functionality
        submitButton = view.findViewById(R.id.vote_submit);
        submitButton.setOnClickListener(v -> submitVote(view, potentialLBIlist));

    }

    private void bindViewModels() {
        userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(requireActivity()).get(RoomViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(requireActivity()).get(LeaderBoardViewModel.class);
    }

    private void setupVotingItemList(LinearLayout potentialLBIlist) {
        leaderBoardViewModel.getPotentialLeaderBoardItems().addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<LeaderBoardItem>>() {
            @Override
            public void onChanged(ObservableList<LeaderBoardItem> sender) {
            }

            @Override
            public void onItemRangeChanged(ObservableList<LeaderBoardItem> sender, int positionStart, int itemCount) {
            }

            @Override
            public void onItemRangeInserted(ObservableList<LeaderBoardItem> sender, int positionStart, int itemCount) {
                LeaderBoardItem leaderBoardItem = leaderBoardViewModel.getPotentialLeaderBoardItems().get(positionStart);
                View voteItem = createVoteItem(leaderBoardItem, potentialLBIlist);
                potentialLBIlist.addView(voteItem);
                AppLog.d(AppLog.VOTE, "Voting item added to UI id=" + leaderBoardItem.getId());
            }

            @Override
            public void onItemRangeMoved(ObservableList<LeaderBoardItem> sender, int fromPosition, int toPosition, int itemCount) {
            }

            @Override
            public void onItemRangeRemoved(ObservableList<LeaderBoardItem> sender, int positionStart, int itemCount) {
            }
        });
    }

    private View createVoteItem(LeaderBoardItem leaderBoardItem, LinearLayout parent) {
        // inflate(..., parent, false) - not inflate(..., null) - so the item's own
        // match_parent width actually resolves against the real container instead of being
        // dropped, which was leaving the vote-selection highlight short of the screen edge.
        View voteItem = LayoutInflater.from(getContext()).inflate(R.layout.lb_vote_item, parent, false);
        TextView ifPart = voteItem.findViewById(R.id.if_part);
        TextView thenPart = voteItem.findViewById(R.id.then_part);
        TextView sentID = voteItem.findViewById(R.id.sentence_id);
        ifPart.setText(leaderBoardItem.getIfPart());
        thenPart.setText(leaderBoardItem.getThenPart());
        sentID.setText(leaderBoardItem.getId());
        voteItem.setBackgroundColor(Color.parseColor("#0000FF00"));
        voteItem.setOnClickListener(this::toggleVoteItem);
        return voteItem;
    }

    private void toggleVoteItem(View voteItem) {
        int color = ((ColorDrawable) voteItem.getBackground()).getColor();
        if (color == SELECTED_COLOR) {
            voteItem.setBackgroundColor(Color.parseColor("#0000FF00"));
        }
        else {
            voteItem.setBackgroundColor(Color.parseColor("#77EDA6EC"));
        }
    }

    private void submitVote(View root, LinearLayout potentialLBIlist) {
        if (voteSubmitting) {
            return;
        }
        sentencesSelected = countSelectedVotes(potentialLBIlist);
        AppLog.d(AppLog.VOTE, "Vote submit clicked: selected=" + sentencesSelected);
        if (sentencesSelected != 1) {
            AppLog.w(AppLog.VOTE, "Vote blocked: selected count=" + sentencesSelected);
            UiMessenger.showSnackbar(root, "Please select 1 sentence");
            return;
        }

        String selectedVoteId = findSelectedVoteId(potentialLBIlist);
        UiMessenger.hideBanner(root);
        setVoteSubmitting(true);
        leaderBoardViewModel.castVote(userViewModel.getUser(), selectedVoteId, () -> {
            if (!isAdded()) {
                return;
            }
            UiMessenger.showSnackbar(root, "Vote sent");
            navigateToEnd();
        });
    }

    private void setVoteSubmitting(boolean submitting) {
        voteSubmitting = submitting;
        ActionButtonState.setSaving(submitButton, submitting);
    }

    private int countSelectedVotes(LinearLayout potentialLBIlist) {
        int selected = 0;
        for (int i = 0; i < potentialLBIlist.getChildCount(); i++) {
            View pot = potentialLBIlist.getChildAt(i);
            int color = ((ColorDrawable) pot.getBackground()).getColor();
            if (color == SELECTED_COLOR) {
                selected += 1;
            }
        }
        return selected;
    }

    private String findSelectedVoteId(LinearLayout potentialLBIlist) {
        for (int i = 0; i < potentialLBIlist.getChildCount(); i++) {
            View pot = potentialLBIlist.getChildAt(i);
            int color = ((ColorDrawable) pot.getBackground()).getColor();
            if (color == SELECTED_COLOR) {
                TextView selectedVoteID = pot.findViewById(R.id.sentence_id);
                return selectedVoteID.getText().toString();
            }
        }
        return "";
    }

    /** Goes to the collecting-votes waiting screen, not straight to the end screen: submitting your
     * own vote only means *you* are done, and EndFrag is only meaningful once the whole round is
     * in. CollectingVotesFrag holds everyone until the last vote lands and then moves the group on
     * together, the same way the If and Then phases work. */
    private void navigateToEnd() {
        AppLog.i(AppLog.GAME_FLOW, "VoteFrag -> CollectingVotesFrag");
        userViewModel.gamePhase.setValue(GamePhase.COLLECTING_VOTES);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, CollectingVotesFrag.class, null)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDestroyView() {
        if (leaderBoardViewModel != null) {
            leaderBoardViewModel.removeVotingItemsListener();
        }
        super.onDestroyView();
    }
}
