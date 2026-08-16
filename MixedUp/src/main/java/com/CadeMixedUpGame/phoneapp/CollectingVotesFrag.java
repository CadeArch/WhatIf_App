package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameFlowPolicy;
import com.CadeMixedUpGame.api.GameLogic;
import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.LeaderBoardViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

/**
 * The account-play voting counterpart to CollectingQuestionsFrag/CollectingAnswersFrag: after you
 * submit your vote you wait here, watching who else has voted, until everybody has - then the whole
 * group moves to EndFrag together.
 *
 * <p>Voting used to skip this step and drop each player onto EndFrag the instant their own vote was
 * written, which left the end screen holding a half-finished round. EndFrag had to defend itself by
 * refusing Home and Play Again while any vote was outstanding, so its primary buttons would simply
 * not respond, with a banner as the only clue and no indication of who was being waited on. Making
 * the phase transition the synchronization point - the way the If and Then phases already do it -
 * removes that state entirely rather than guarding against it at each consumer.
 */
public class CollectingVotesFrag extends Fragment {
    private static final long MIN_WAITING_SCREEN_MS = 1000L;

    UserViewModel userViewModel;
    LeaderBoardViewModel leaderBoardViewModel;
    private final ObservableArrayList<User> whoVoted = new ObservableArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean navigationScheduled = false;
    private ObservableList.OnListChangedCallback<ObservableList<String>> votesCallback;

    public CollectingVotesFrag() {
        super(R.layout.fragment_collecting_votes);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AppLog.i(AppLog.GAME_FLOW, "Collecting votes screen opened");
        userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(requireActivity()).get(LeaderBoardViewModel.class);
        userViewModel.gamePhase.setValue(GamePhase.COLLECTING_VOTES);
        observeUserMessages(view);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewCollectVotes);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), Utils.computeSpanCount(getContext())));
        MyRecyclerViewAdapter adapter = new MyRecyclerViewAdapter(requireActivity(), whoVoted);
        recyclerView.setAdapter(adapter);

        votesCallback = new ObservableList.OnListChangedCallback<ObservableList<String>>() {
            @Override
            public void onChanged(ObservableList<String> sender) {
                refreshVoteProgress(adapter);
            }

            @Override
            public void onItemRangeChanged(ObservableList<String> sender, int positionStart, int itemCount) {
                refreshVoteProgress(adapter);
            }

            @Override
            public void onItemRangeInserted(ObservableList<String> sender, int positionStart, int itemCount) {
                refreshVoteProgress(adapter);
            }

            @Override
            public void onItemRangeMoved(ObservableList<String> sender, int fromPosition, int toPosition, int itemCount) {
            }

            @Override
            public void onItemRangeRemoved(ObservableList<String> sender, int positionStart, int itemCount) {
                refreshVoteProgress(adapter);
            }
        };
        leaderBoardViewModel.getVotedPlayerKeys().addOnListChangedCallback(votesCallback);

        // The last vote can land before this screen is even built (a two-player round where the
        // other player voted first), so evaluate immediately rather than waiting for a change.
        refreshVoteProgress(adapter);
    }

    private void observeUserMessages(View view) {
        userViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showSnackbar(view, message);
                userViewModel.databaseMessage.setValue("");
            }
        });
    }

    private void refreshVoteProgress(MyRecyclerViewAdapter adapter) {
        if (navigationScheduled) {
            return;
        }
        whoVoted.clear();
        for (User user : userViewModel.getUsers()) {
            if (user != null && leaderBoardViewModel.getVotedPlayerKeys().contains(GameLogic.playerKey(user))) {
                whoVoted.add(user);
            }
        }
        adapter.notifyDataSetChanged();

        // Active players only. A removed player can never cast a vote, so counting them means the
        // total is unreachable and this screen waits forever - a hard hang at the end of an
        // account-play round, after everyone still playing has already voted.
        int playerCount = GameFlowPolicy.activePlayerCount(userViewModel.getUsers());
        // Counts the same list this screen observes, so the count can never be read a beat behind
        // the notification that triggered this (one key per voter, so it matches the vote count).
        int voteCount = leaderBoardViewModel.getVotedPlayerKeys().size();
        AppLog.d(AppLog.VOTE, "Collecting votes progress: votes=" + voteCount + ", players=" + playerCount);
        if (GameFlowPolicy.allVotesCast(playerCount, voteCount)) {
            navigateToEnd();
        }
    }

    private void navigateToEnd() {
        AppLog.i(AppLog.GAME_FLOW, "CollectingVotesFrag -> EndFrag after waiting screen delay");
        navigationScheduled = true;
        handler.postDelayed(() -> {
            if (!isAdded()) {
                return;
            }
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, EndFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        }, MIN_WAITING_SCREEN_MS);
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        if (votesCallback != null) {
            leaderBoardViewModel.getVotedPlayerKeys().removeOnListChangedCallback(votesCallback);
            votesCallback = null;
        }
        super.onDestroyView();
    }
}
