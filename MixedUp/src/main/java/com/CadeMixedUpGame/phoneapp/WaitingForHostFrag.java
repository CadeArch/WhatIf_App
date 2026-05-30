package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;
import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class WaitingForHostFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    boolean onWaitingForHost;
    private ObservableList.OnListChangedCallback<ObservableList<User>> usersCallback;


    public WaitingForHostFrag() {
        super(R.layout.fragment_waiting_for_host);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        userViewModel.gamePhase.setValue(GamePhase.LOBBY);
        onWaitingForHost = true;
        roomViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showSnackbar(view, message);
                roomViewModel.databaseMessage.setValue("");
            }
        });
        userViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showSnackbar(view, message);
                userViewModel.databaseMessage.setValue("");
            }
        });
        // this will unlock voices based on number of games played unlocking here in case players hit the play again button
        if (userViewModel.getUser().getValue().accountPlay) {
            userViewModel.unlockVoice(userViewModel.getUser(), "numGames");
            if (userViewModel.getUser().getValue().gamesPlayed == 5) {
                UiMessenger.showSnackbar(view, "Unlocked backwords google voice!");
            }
        }

        TextView gameCode = view.findViewById(R.id.gameCode);
        gameCode.setText(userViewModel.getUser().getValue().gameRoom);


        roomViewModel.gameInProgressFalse(userViewModel.getUser().getValue().gameRoom);
        // set up the RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        MyRecyclerViewAdapter adapter = new MyRecyclerViewAdapter(getContext(), userViewModel.getUsers());
        recyclerView.setAdapter(adapter);

        AppLog.i(AppLog.ROOM, "Waiting screen opened: players=" + userViewModel.getUsers().size());

        // when the users array changes reset the adapter to include all people
        usersCallback = new ObservableList.OnListChangedCallback<ObservableList<User>>() {
            @Override
            public void onChanged(ObservableList<User> sender) {

            }

            @Override
            public void onItemRangeChanged(ObservableList<User> sender, int positionStart, int itemCount) {

            }

            @Override
            public void onItemRangeInserted(ObservableList<User> sender, int positionStart, int itemCount) {
                if (onWaitingForHost) {
//                    System.out.println("waitingForHost: inserting name");
//                    System.out.println("users array size: WFHF: " + userViewModel.getUsers().size());
//                MyRecyclerViewAdapter adapter = new MyRecyclerViewAdapter(getContext(), userViewModel.getUsers());
                    recyclerView.setAdapter(adapter);
                }

            }

            @Override
            public void onItemRangeMoved(ObservableList<User> sender, int fromPosition, int toPosition, int itemCount) {

            }

            @Override
            public void onItemRangeRemoved(ObservableList<User> sender, int positionStart, int itemCount) {
//                MyRecyclerViewAdapter adapter = new MyRecyclerViewAdapter(getActivity(), userViewModel.getUsers());
//                recyclerView.setAdapter(adapter);
            }
        };
        userViewModel.getUsers().addOnListChangedCallback(usersCallback);

        // if the current persons device is the host set the host value since the host wont have gone through the join game frag
        if (userViewModel.getUser().getValue().host) {
            userViewModel.host = userViewModel.getUser();
//            System.out.println("I am the host and have set the host value: " + userViewModel.host.getValue().userName);
        }

        // giving button functionality
        view.findViewById(R.id.waitingForHost_start).setOnClickListener(v -> {
            startGame(view);
        });

        if(!userViewModel.getUser().getValue().host) {
            // making button invisible since they arent the host
            View button = view.findViewById(R.id.waitingForHost_start);
            button.setVisibility(View.GONE);

            // setting listener on host location in database if hostStarted changes to true
            // hostStarted will be changed to true on this user
            // todo may not need to observe the host
            userViewModel.host.observe(this.getViewLifecycleOwner(), new Observer<User>() {
                @Override
                public void onChanged(User user) {
                    userViewModel.listenToHost(userViewModel.host);
                    AppLog.i(AppLog.ROOM, "Guest listening to host=" + userViewModel.host.getValue().userName);

                }
            });
            // when observer notices change on user data move to next frag
            userViewModel.getUser().observe(this.getViewLifecycleOwner(), new Observer<User>() {
                @Override
                public void onChanged(User user) {
//                    System.out.println("MY USERNAME: " + user.userName);
//                    System.out.println("Host started: " + user.hostStarted);
                    //if host has clicked the button move to next screen
                    userViewModel.hostStarted(userViewModel.getUser().getValue());

                    if (user.hostStarted && !user.ifFinished && !userViewModel.onWriteIf) {
//                        System.out.println("WFH frag-----host started-----ifFinished---" + user.hostStarted + " " + user.ifFinished);
                        navigateToWriteIf();
                    }
                }
            });
        }
    }

    private void startGame(View view) {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0) {
            UiMessenger.showBanner(view, "Game room is missing. Go home and create the room again.", UiMessenger.MessageType.ERROR);
            AppLog.w(AppLog.ROOM, "Start game blocked: current user or game room missing");
            return;
        }

        AppLog.i(AppLog.GAME_FLOW, "Host starting game room=" + currentUser.gameRoom);
        roomViewModel.gameInProgressTrue(currentUser.gameRoom, () ->
                roomViewModel.createRoundAssignments(currentUser.gameRoom, userViewModel.getUsers(), () -> {
                    currentUser.hostStarted = true;
                    userViewModel.playing = true;
                    userViewModel.gamePhase.setValue(GamePhase.WRITING_IF);
                    userViewModel.hostStarted(currentUser);
                    navigateToWriteIf();
                }));
    }

    private void navigateToWriteIf() {
        AppLog.i(AppLog.GAME_FLOW, "WaitingForHostFrag -> WriteIfFrag");
        onWaitingForHost = false;
        userViewModel.gamePhase.setValue(GamePhase.WRITING_IF);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, WriteIfFrag.class, null)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit();
        userViewModel.onWriteIf = true;
        userViewModel.playing = true;
    }

    @Override
    public void onDestroyView() {
        if (usersCallback != null) {
            userViewModel.getUsers().removeOnListChangedCallback(usersCallback);
            usersCallback = null;
        }
        super.onDestroyView();
    }
}
