package com.CadeMixedUpGame.phoneapp;

import android.graphics.Color;
import android.os.Bundle;
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
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    LeaderBoardViewModel leaderBoardViewModel;
    boolean allAccountPlayers = false;
    private View homeButton;
    private View againButton;
    private boolean endActionInProgress = false;

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
                setEndActionSaving(false);
            }
        });
        roomViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showBanner(view, message, UiMessenger.MessageType.ERROR);
                roomViewModel.databaseMessage.setValue("");
                setEndActionSaving(false);
            }
        });
        leaderBoardViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showBanner(view, message, UiMessenger.MessageType.ERROR);
                leaderBoardViewModel.databaseMessage.setValue("");
                setEndActionSaving(false);
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
        homeButton = view.findViewById(R.id.home_ending);
        againButton = view.findViewById(R.id.again_ending);
        // only host can say to play again
        if (!userViewModel.getUser().getValue().host) {
            // may not need the background color change with set enabled
            againButton.setBackgroundColor(Color.GRAY);
            againButton.setEnabled(false);
            setAgainButtonText("waiting");

            roomViewModel.replayState.observe(this.getViewLifecycleOwner(), new Observer<String>() {
                @Override
                public void onChanged(String state) {
                    AppLog.d(AppLog.GAME_FLOW, "Room replay state changed to=" + state);
                    if ("yes".equals(state)) {
                        // if host hits again button will be clickable for rest of players
                        againButton.setEnabled(true);
                        againButton.setBackgroundColor(Color.parseColor("#FFEDA6EC"));
                        setAgainButtonText("again!");

                    }
                    else if ("no".equals(state) && userViewModel.onEndFrag) {
                        userViewModel.onEndFrag = false;
                        AppLog.i(AppLog.GAME_FLOW, "EndFrag received host home signal; currentFragment="
                                + Utils.currentFragmentName(getActivity()));
                        finishHomeNavigation("host replayState=no");
                    }
                }
            });
        }


        //giving home button functionality
        homeButton.setOnClickListener(v -> {
            if (endActionInProgress) {
                return;
            }

            if (hasPendingRequiredVotes()) {
                AppLog.w(AppLog.VOTE, "Home blocked: not all votes are in");
                UiMessenger.showBanner(view, "Not all votes are in yet.", UiMessenger.MessageType.WARNING);
            } else {
                UiMessenger.hideBanner(view);
                User currentUser = userViewModel.getUser().getValue();
                if (currentUser != null && currentUser.host) {
                    setEndActionSaving(true);
                    roomViewModel.setReplayState(currentUser.gameRoom, "no", () -> {
                        leaderBoardViewModel.removeCastVotesListener(userViewModel.myRoom);
                        userViewModel.deleteRoom(currentUser, () -> finishHomeNavigation("host home deleted room"));
                    });
                }
                else {
                    setEndActionSaving(true);
                    userViewModel.removeCurrentPlayerFromRoom(() -> finishHomeNavigation("guest home removed self"));
                }
            }
        });

        //giving again button functionality
        againButton.setOnClickListener(v -> {
            if (endActionInProgress) {
                return;
            }
            AppLog.i(AppLog.GAME_FLOW, "Play again clicked");

            if (hasPendingRequiredVotes()) {
                AppLog.w(AppLog.VOTE, "Play again blocked: not all votes are in");
                UiMessenger.showBanner(view, "Not all votes are in yet.", UiMessenger.MessageType.WARNING);
            }
            else {
                UiMessenger.hideBanner(view);
                setEndActionSaving(true);

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
                roomViewModel.setReplayState(userViewModel.myRoom, "yes", () ->
                        roomViewModel.gameInProgressFalse(userViewModel.myRoom, this::finishPlayAgainNavigation));
            }
            else {
                finishPlayAgainNavigation();
            }
        });
    }

    private void finishPlayAgainNavigation() {
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
    }

    private void setEndActionSaving(boolean saving) {
        endActionInProgress = saving;
        if (homeButton != null) {
            ActionButtonState.setSaving(homeButton, saving);
        }
        if (againButton != null && userViewModel != null && userViewModel.getUser().getValue() != null) {
            boolean host = userViewModel.getUser().getValue().host;
            boolean replayAllowed = host || "yes".equals(roomViewModel.replayState.getValue());
            ActionButtonState.setSaving(againButton, saving, replayAllowed);
        }
    }

    private void setAgainButtonText(String text) {
        if (againButton instanceof android.widget.TextView) {
            ((android.widget.TextView) againButton).setText(text);
        }
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

    private void finishHomeNavigation(String reason) {
        if (!isAdded()) {
            return;
        }
        AppLog.i(AppLog.GAME_FLOW, "EndFrag finishing home navigation reason=" + reason
                + ", currentFragment=" + Utils.currentFragmentName(getActivity())
                + ", backStack=" + getActivity().getSupportFragmentManager().getBackStackEntryCount());
        userViewModel.removeListenerOnDB();
        roomViewModel.removeReplayStateListener();
        clearLocalStateForReplayOrExit();
        userViewModel.host = new MutableLiveData<User>();
        Utils.navigateHomeReplacingCurrent(getActivity());
        AppLog.i(AppLog.GAME_FLOW, "EndFrag -> StartFragment via home reason=" + reason);
    }

}
