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
import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class CollectingQuestionsFrag extends Fragment {
    private static final long MIN_WAITING_SCREEN_MS = 1000L;

    UserViewModel userViewModel;
    Boolean allIfsFinished = false;
    ObservableArrayList<User> whoSubmitted = new ObservableArrayList<>();
    Boolean onCollectingQuestionsFrag;
    MyRecyclerViewAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean navigationScheduled = false;
    private ObservableList.OnListChangedCallback<ObservableList<User>> usersCallback;


    public CollectingQuestionsFrag() {
        super(R.layout.fragment_collecting_questions);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppLog.i(AppLog.GAME_FLOW, "Collecting If sentences screen opened");
        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        userViewModel.gamePhase.setValue(GamePhase.COLLECTING_IFS);

        onCollectingQuestionsFrag = true;
        // set up the RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewCollectQ);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        // seeing who has submitted
        for (User user:userViewModel.getUsers()) {
            addIfSubmittedUser(user);
        }

        // populating view with those who have submitted there if
        adapter = new MyRecyclerViewAdapter(getContext(), whoSubmitted);
        recyclerView.setAdapter(adapter);

        // when the users array changes reset the adapter to include all people
        usersCallback = new ObservableList.OnListChangedCallback<ObservableList<User>>() {
            @Override
            public void onChanged(ObservableList<User> sender) {
                AppLog.d(AppLog.GAME_FLOW, "Collecting If user list changed");
                refreshIfProgress();
            }

            @Override
            public void onItemRangeChanged(ObservableList<User> sender, int positionStart, int itemCount) {
                if (onCollectingQuestionsFrag) {
                    refreshIfProgress();
                }
            }

            @Override
            public void onItemRangeInserted(ObservableList<User> sender, int positionStart, int itemCount) {

                if (onCollectingQuestionsFrag) {
                    handleIfUserInserted(positionStart);
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

        userViewModel.pushIf(userViewModel.getUser());
        refreshIfProgress();

    }

    private void addIfSubmittedUser(User user) {
        if (user != null && Boolean.TRUE.equals(user.ifFinished) && !whoSubmitted.contains(user)) {
            whoSubmitted.add(user);
        }
    }

    private void handleIfUserInserted(int positionStart) {
        addIfSubmittedUser(userViewModel.getUsers().get(positionStart));
        refreshIfProgress();
    }

    private void refreshIfProgress() {
        if (!onCollectingQuestionsFrag) {
            return;
        }
        whoSubmitted.clear();
        for (User user : userViewModel.getUsers()) {
            addIfSubmittedUser(user);
        }
        adapter.notifyDataSetChanged();
        int finishedCount = countFinishedIfs();
        AppLog.d(AppLog.GAME_FLOW, "Collecting If progress: finished=" + finishedCount + ", total=" + userViewModel.getUsers().size());
        allIfsFinished = finishedCount == userViewModel.getUsers().size();
        if (shouldNavigateToWriteThen()) {
            navigateToWriteThen();
        }
    }

    private int countFinishedIfs() {
        int count = 0;
        for (User user : userViewModel.getUsers()) {
            if (user != null && Boolean.TRUE.equals(user.ifFinished)) {
                count += 1;
            }
        }
        return count;
    }

    private boolean shouldNavigateToWriteThen() {
        User currentUser = userViewModel.getUser().getValue();
        return allIfsFinished
                && currentUser != null
                && !currentUser.thenFinished
                && !userViewModel.onWriteThen
                && !navigationScheduled;
    }

    private void navigateToWriteThen() {
        AppLog.i(AppLog.GAME_FLOW, "CollectingQuestionsFrag -> WriteThenFrag after waiting screen delay");
        navigationScheduled = true;
        onCollectingQuestionsFrag = false;
        handler.postDelayed(() -> {
            if (!isAdded()) {
                return;
            }
            userViewModel.gamePhase.setValue(GamePhase.WRITING_THEN);
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, WriteThenFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
            userViewModel.onWriteThen = true;
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
