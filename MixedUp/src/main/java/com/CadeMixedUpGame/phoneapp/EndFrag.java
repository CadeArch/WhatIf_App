package com.CadeMixedUpGame.phoneapp;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.LeaderBoardViewModel;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;
import java.util.Objects;

public class EndFrag extends Fragment {
    private static final long HOST_HOME_ROOM_DELETE_DELAY_MS = 3000L;

    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    LeaderBoardViewModel leaderBoardViewModel;
    boolean allAccountPlayers = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public EndFrag() {
        super(R.layout.fragment_end);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(getActivity()).get(LeaderBoardViewModel.class);
        userViewModel.gamePhase.setValue(GamePhase.ENDED);
        AppLog.i(AppLog.GAME_FLOW, "End screen opened: users=" + userViewModel.getUsers().size() + ", castVotes=" + leaderBoardViewModel.getCastvotes().size());
        userViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showBanner(view, message, UiMessenger.MessageType.ERROR);
                userViewModel.databaseMessage.setValue("");
            }
        });
        leaderBoardViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showBanner(view, message, UiMessenger.MessageType.ERROR);
                leaderBoardViewModel.databaseMessage.setValue("");
            }
        });

        // incrementing num of total games played for account player
        if (Objects.requireNonNull(userViewModel.getUser().getValue()).accountPlay) {
            userViewModel.getGamesPlayed(userViewModel.getUser(), true);
            userViewModel.getMadeLeaderBoard(userViewModel.getUser());
            userViewModel.getMadePerfectLeaderBoard(userViewModel.getUser());
        }

        // so database resets temp votes and vote items as well if all are account players
        int counter = 0;
        for (User user:userViewModel.getUsers()){
            if(user.accountPlay) {
                counter += 1;
            }
        }
        if (counter == userViewModel.getUsers().size()) {
            allAccountPlayers = true;
        }

        userViewModel.removePlayersListenerOnDB();
        userViewModel.getUser().getValue().hostPlayedAgain = "";
        roomViewModel.listenToReplayState(userViewModel.myRoom);

        AppLog.d(AppLog.GAME_FLOW, "End screen current user=" + userViewModel.getUser().getValue().userName + ", host=" + userViewModel.getUser().getValue().host);
        userViewModel.onEndFrag = true;
        // only host can say to play again
        if (!userViewModel.getUser().getValue().host) {
            // may not need the background color change with set enabled
            view.findViewById(R.id.again_ending).setBackgroundColor(Color.GRAY);
            view.findViewById(R.id.again_ending).setEnabled(false);

            roomViewModel.replayState.observe(this.getViewLifecycleOwner(), new Observer<String>() {
                @Override
                public void onChanged(String state) {
                    AppLog.d(AppLog.GAME_FLOW, "Room replay state changed to=" + state);
                    if ("yes".equals(state)) {
                        // if host hits again button will be clickable for rest of players
                        view.findViewById(R.id.again_ending).setEnabled(true);
                        view.findViewById(R.id.again_ending).setBackgroundColor(Color.parseColor("#FFEDA6EC"));

                    }
                    else if ("no".equals(state) && userViewModel.onEndFrag) {
                        userViewModel.onEndFrag = false;
                        UiMessenger.showBanner(view, "Host left the game room.", UiMessenger.MessageType.ERROR);
                        finishHomeNavigation();
                    }
                }
            });
        }


        //giving home button functionality
        view.findViewById(R.id.home_ending).setOnClickListener(v -> {

            if (hasPendingRequiredVotes()) {
                AppLog.w(AppLog.VOTE, "Home blocked: not all votes are in");
                UiMessenger.showBanner(view, "Not all votes are in yet.", UiMessenger.MessageType.WARNING);
            } else {
                UiMessenger.hideBanner(view);
                User currentUser = userViewModel.getUser().getValue();
                if (currentUser != null && currentUser.host) {
                    roomViewModel.setReplayState(currentUser.gameRoom, "no");
                    leaderBoardViewModel.removeCastVotesListener(userViewModel.myRoom);
                    scheduleRoomDelete(currentUser);
                    finishHomeNavigation();
                }
                else {
                    userViewModel.removeCurrentPlayerFromRoom(this::finishHomeNavigation);
                }
            }
        });

        //giving again button functionality
        view.findViewById(R.id.again_ending).setOnClickListener(v -> {
            AppLog.i(AppLog.GAME_FLOW, "Play again clicked");

            if (hasPendingRequiredVotes()) {
                AppLog.w(AppLog.VOTE, "Play again blocked: not all votes are in");
                UiMessenger.showBanner(view, "Not all votes are in yet.", UiMessenger.MessageType.WARNING);
            }
            else {
                UiMessenger.hideBanner(view);

                clearLocalStateForReplayOrExit();

                // resetting db gameroom to no one in it, as they play again I will push the person back to it
                if (userViewModel.getUser().getValue().host) {
                    if (allAccountPlayers) {
                        userViewModel.deleteVotesAndVotingItems();
                        leaderBoardViewModel.removeCastVotesListener(userViewModel.myRoom);
                        AppLog.i(AppLog.VOTE, "Cleared voting data for replay");
                    }
                    resetRoomForReplay();
                }
                else {
                    continuePlayAgain();
                }
            }
        });

    }

    private void resetRoomForReplay() {
        String room = userViewModel.myRoom;
        AppLog.i(AppLog.GAME_FLOW, "Resetting room for replay room=" + room);
        roomViewModel.clearRoomRoundStateForReplay(room, () ->
                userViewModel.nurfAllUsers(() -> continuePlayAgain()));
    }

    private void continuePlayAgain() {
        if (!isAdded()) {
            return;
        }
        User currentUser = userViewModel.getUser().getValue();
        boolean host = currentUser != null && currentUser.host;

        userViewModel.pushPerson(userViewModel.getUser(), () -> {
            if (host) {
                roomViewModel.setReplayState(userViewModel.myRoom, "yes");
            }
            roomViewModel.removeReplayStateListener();
            userViewModel.getUser().getValue().hostPlayedAgain = "";
            userViewModel.loadUsers(userViewModel.myRoom);
            if (!isAdded()) {
                return;
            }
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, WaitingForHostFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
            AppLog.i(AppLog.GAME_FLOW, "EndFrag -> WaitingForHostFrag via play again");
        });
    }

    private void scheduleRoomDelete(User hostUser) {
        handler.postDelayed(() -> {
            if (hostUser != null) {
                userViewModel.deleteRoom(hostUser);
            }
        }, HOST_HOME_ROOM_DELETE_DELAY_MS);
    }

    private boolean hasPendingRequiredVotes() {
        return allAccountPlayers && userViewModel.getUsers().size() != leaderBoardViewModel.getCastvotes().size();
    }

    private void clearLocalStateForReplayOrExit() {
        userViewModel.reset();
        leaderBoardViewModel.reset();
        roomViewModel.clearLocalRoundState();
        AppLog.i(AppLog.GAME_FLOW, "Cleared local end-of-round state");
    }

    private void finishHomeNavigation() {
        if (!isAdded()) {
            return;
        }
        userViewModel.removeListenerOnDB();
        roomViewModel.removeReplayStateListener();
        clearLocalStateForReplayOrExit();
        userViewModel.host = new MutableLiveData<User>();
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, StartFragment.class, null)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit();
        AppLog.i(AppLog.GAME_FLOW, "EndFrag -> StartFragment via home");
    }

}
