package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameFlowPolicy;
import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class CollectingAnswersFrag extends Fragment {
    private static final long MIN_WAITING_SCREEN_MS = 1000L;

    UserViewModel userViewModel;
    Boolean allThensFinished = false;
    ObservableArrayList<User> whoSubmittedThen = new ObservableArrayList<>();
    Boolean onCollectingAnswers;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean navigationScheduled = false;
    private ObservableList.OnListChangedCallback<ObservableList<User>> usersCallback;

    public CollectingAnswersFrag() {
        super(R.layout.fragment_collecting_answers);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AppLog.i(AppLog.GAME_FLOW, "Collecting Then sentences screen opened");
        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        userViewModel.gamePhase.setValue(GamePhase.COLLECTING_THENS);
        observeUserMessages(view);

        userViewModel.onCollectingAnswers = true;
        onCollectingAnswers = true;
        // set up the RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewCollectA);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        MyRecyclerViewAdapter adapter = new MyRecyclerViewAdapter(getActivity(), whoSubmittedThen);

        // seeing who has submitted
        for (User user:userViewModel.getUsers()) {
            addThenSubmittedUser(user);
        }

        // populating view with those who have submitted there if
        recyclerView.setAdapter(adapter);

        // when the users array changes reset the adapter to include all people
        usersCallback = new ObservableList.OnListChangedCallback<ObservableList<User>>() {
            @Override
            public void onChanged(ObservableList<User> sender) {
                AppLog.d(AppLog.GAME_FLOW, "Collecting Then user list changed");
                refreshThenProgress(adapter);
            }

            @Override
            public void onItemRangeChanged(ObservableList<User> sender, int positionStart, int itemCount) {
                if (onCollectingAnswers) {
                    refreshThenProgress(adapter);
                }
            }

            @Override
            public void onItemRangeInserted(ObservableList<User> sender, int positionStart, int itemCount) {
                if (onCollectingAnswers) {
                    handleThenUserInserted(positionStart, adapter);
                }
            }

            @Override
            public void onItemRangeMoved(ObservableList<User> sender, int fromPosition, int toPosition, int itemCount) {

            }

            @Override
            public void onItemRangeRemoved(ObservableList<User> sender, int positionStart, int itemCount) {

            }
        };
        userViewModel.getUsers().addOnListChangedCallback(usersCallback);

        refreshThenProgress(adapter);



    }

    private void observeUserMessages(View view) {
        userViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showSnackbar(view, message);
                userViewModel.databaseMessage.setValue("");
            }
        });
    }

    private void addThenSubmittedUser(User user) {
        if (user != null && Boolean.TRUE.equals(user.thenFinished) && !whoSubmittedThen.contains(user)) {
            whoSubmittedThen.add(user);
        }
    }

    private void handleThenUserInserted(int positionStart, MyRecyclerViewAdapter adapter) {
        addThenSubmittedUser(userViewModel.getUsers().get(positionStart));
        refreshThenProgress(adapter);
    }

    private void refreshThenProgress(MyRecyclerViewAdapter adapter) {
        if (!onCollectingAnswers) {
            return;
        }
        whoSubmittedThen.clear();
        for (User user : userViewModel.getUsers()) {
            addThenSubmittedUser(user);
        }
        adapter.notifyDataSetChanged();
        int finishedCount = GameFlowPolicy.countFinishedThens(userViewModel.getUsers());
        AppLog.d(AppLog.GAME_FLOW, "Collecting Then progress: finished=" + finishedCount + ", total=" + userViewModel.getUsers().size());
        allThensFinished = GameFlowPolicy.allPlayersFinishedThens(userViewModel.getUsers());
        if (allThensFinished && userViewModel.onCollectingAnswers && !navigationScheduled) {
            navigateToReadSentence();
        }
    }

    private void navigateToReadSentence() {
        AppLog.i(AppLog.GAME_FLOW, "CollectingAnswersFrag -> ReadSentenceFrag after waiting screen delay");
        navigationScheduled = true;
        onCollectingAnswers = false;
        handler.postDelayed(() -> {
            if (!isAdded()) {
                return;
            }
            userViewModel.gamePhase.setValue(GamePhase.READING);
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, ReadSentenceFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
            userViewModel.onCollectingAnswers = false;
        }, MIN_WAITING_SCREEN_MS);
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        if (usersCallback != null) {
            userViewModel.getUsers().removeOnListChangedCallback(usersCallback);
            usersCallback = null;
        }
        super.onDestroyView();
    }
}

