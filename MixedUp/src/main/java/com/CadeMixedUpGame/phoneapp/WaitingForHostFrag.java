package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;
import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.UnlockPolicy;
import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class WaitingForHostFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    boolean onWaitingForHost;
    private ObservableList.OnListChangedCallback<ObservableList<User>> usersCallback;
    private boolean currentRoundBaselineCaptured;
    private boolean lobbySawEmptyRoundId;
    private String lobbyBaselineRoundId = "";


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
        // The lobby is the one in-room screen where leaving is harmless - no round has started, so
        // there is nothing half-collected to corrupt. Without this a player who joined the wrong
        // room was stuck: back was swallowed app-wide, and force-quitting only marked them
        // disconnected, leaving them sitting in the room as a phantom player.
        new RoomExit(this, userViewModel, roomViewModel).wireSystemBack();
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
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null) {
            UiMessenger.showBanner(view, "User is not loaded yet. Go home and try again.", UiMessenger.MessageType.ERROR);
            AppLog.w(AppLog.AUTH, "Waiting screen opened without current user");
            return;
        }

        if (currentUser.accountPlay) {
            userViewModel.unlockEarnedVoices(userViewModel.getUser());
            // Announce whatever this game's count actually earned, instead of hard-coding the one
            // "5 games -> backwords" milestone. That literal both duplicated the threshold living
            // in UnlockPolicy and stayed silent for every other games-played voice.
            String justEarned = UnlockPolicy.voiceEarnedAtExactly(currentUser.gamesPlayed);
            if (justEarned != null) {
                UiMessenger.showSnackbar(view, "Unlocked " + justEarned + " google voice!");
            }
        }

        TextView gameCode = view.findViewById(R.id.gameCode);
        gameCode.setText(currentUser.gameRoom);
        updateLobbyCopy(view, currentUser);
        observeRoomClose(currentUser);


        if (currentUser.host) {
            roomViewModel.gameInProgressFalse(currentUser.gameRoom);
        }
        // set up the RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), Utils.computeSpanCount(getContext())));
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
                    findAndSetHostFromUsers();
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
        findAndSetHostFromUsers();

        // if the current persons device is the host set the host value since the host wont have gone through the join game frag
        if (currentUser.host) {
            userViewModel.host = userViewModel.getUser();
//            System.out.println("I am the host and have set the host value: " + userViewModel.host.getValue().userName);
        }

        // giving button functionality
        view.findViewById(R.id.waitingForHost_start).setOnClickListener(v -> {
            startGame(view);
        });

        if(!currentUser.host) {
            // making button invisible since they arent the host
            View button = view.findViewById(R.id.waitingForHost_start);
            button.setVisibility(View.GONE);
        }
    }

    private void updateLobbyCopy(View view, User currentUser) {
        TextView waitingText = view.findViewById(R.id.textView2);
        if (waitingText == null || currentUser == null) {
            return;
        }
        if (currentUser.host) {
            waitingText.setText("Players can join until you press Start.");
        }
        else {
            waitingText.setText("Waiting for host. Players can still join.");
        }
    }

    private void observeRoomClose(User currentUser) {
        if (currentUser == null || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0) {
            return;
        }
        roomViewModel.listenToCurrentRoundId(currentUser.gameRoom);
        roomViewModel.listenToReplayState(currentUser.gameRoom);
        roomViewModel.replayState.observe(getViewLifecycleOwner(), state -> {
            if ("no".equals(state) && onWaitingForHost && !currentUser.host) {
                AppLog.i(AppLog.GAME_FLOW, "WaitingForHostFrag received host home signal; currentFragment="
                        + Utils.currentFragmentName(getActivity()));
                showHostEndedGameOnHome();
                finishHomeNavigation("host replayState=no");
            }
        });
        roomViewModel.currentRoundId.observe(getViewLifecycleOwner(), roundId -> {
            handleLobbyRoundId(currentUser, roundId);
        });
    }

    private void handleLobbyRoundId(User currentUser, String roundId) {
        if (currentUser.host || !onWaitingForHost) {
            return;
        }
        if (!Boolean.TRUE.equals(roomViewModel.currentRoundLoaded.getValue())) {
            return;
        }

        String safeRoundId = roundId == null ? "" : roundId;
        if (!currentRoundBaselineCaptured) {
            currentRoundBaselineCaptured = true;
            lobbyBaselineRoundId = safeRoundId;
            lobbySawEmptyRoundId = safeRoundId.length() == 0;
            AppLog.d(AppLog.GAME_FLOW, "Lobby round baseline captured room=" + currentUser.gameRoom
                    + ", roundId=" + lobbyBaselineRoundId
                    + ", sawEmpty=" + lobbySawEmptyRoundId);
            return;
        }

        if (safeRoundId.length() == 0) {
            lobbySawEmptyRoundId = true;
            return;
        }
        if (!lobbySawEmptyRoundId || safeRoundId.equals(lobbyBaselineRoundId) || "no".equals(roomViewModel.replayState.getValue())) {
            AppLog.d(AppLog.GAME_FLOW, "Ignoring non-fresh lobby round signal room=" + currentUser.gameRoom
                    + ", roundId=" + safeRoundId
                    + ", baseline=" + lobbyBaselineRoundId
                    + ", sawEmpty=" + lobbySawEmptyRoundId
                    + ", replayState=" + roomViewModel.replayState.getValue());
            return;
        }

        AppLog.i(AppLog.GAME_FLOW, "Starting guest from fresh currentRoundId room=" + currentUser.gameRoom + ", roundId=" + safeRoundId);
        navigateToWriteIf();
    }

    private void findAndSetHostFromUsers() {
        for (User player : userViewModel.getUsers()) {
            if (player != null && player.host) {
                userViewModel.host.setValue(player);
                AppLog.d(AppLog.ROOM, "Host found on waiting screen: " + player.userName);
                return;
            }
        }
    }

    private void startGame(View view) {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0) {
            UiMessenger.showBanner(view, "Game room is missing. Go home and create the room again.", UiMessenger.MessageType.ERROR);
            AppLog.w(AppLog.ROOM, "Start game blocked: current user or game room missing");
            return;
        }
        if (userViewModel.getUsers().size() < 2) {
            UiMessenger.showSnackbar(view, "Wait for at least one more player.");
            AppLog.w(AppLog.ROOM, "Start game blocked: fewer than two players room=" + currentUser.gameRoom);
            return;
        }

        AppLog.i(AppLog.GAME_FLOW, "Host starting game room=" + currentUser.gameRoom);
        roomViewModel.setReplayState(currentUser.gameRoom, "");
        roomViewModel.gameInProgressTrue(currentUser.gameRoom, () ->
                roomViewModel.createRoundAssignments(currentUser.gameRoom, userViewModel.getUsers(), () -> {
                    userViewModel.playing = true;
                    userViewModel.gamePhase.setValue(GamePhase.WRITING_IF);
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

    private void finishHomeNavigation(String reason) {
        if (!isAdded()) {
            return;
        }
        AppLog.i(AppLog.GAME_FLOW, "WaitingForHostFrag finishing home navigation reason=" + reason
                + ", currentFragment=" + Utils.currentFragmentName(getActivity())
                + ", backStack=" + requireActivity().getSupportFragmentManager().getBackStackEntryCount());
        onWaitingForHost = false;
        userViewModel.removeListenerOnDB();
        roomViewModel.removeReplayStateListener();
        roomViewModel.removeCurrentRoundListener();
        roomViewModel.clearLocalRoundState();
        Utils.navigateHomeReplacingCurrent(getActivity());
        AppLog.i(AppLog.GAME_FLOW, "WaitingForHostFrag -> StartFragment via home reason=" + reason);
    }

    private void showHostEndedGameOnHome() {
        userViewModel.pendingHomeSnackbar = "The host ended the game room.";
    }

    @Override
    public void onDestroyView() {
        if (usersCallback != null) {
            userViewModel.getUsers().removeOnListChangedCallback(usersCallback);
            usersCallback = null;
        }
        roomViewModel.removeReplayStateListener();
        roomViewModel.removeCurrentRoundListener();
        super.onDestroyView();
    }
}
