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

        AppLog.d(AppLog.GAME_FLOW, "End screen current user=" + userViewModel.getUser().getValue().userName + ", host=" + userViewModel.getUser().getValue().host);
        userViewModel.onEndFrag = true;
        // only host can say to play again
        if (!userViewModel.getUser().getValue().host) {
            // may not need the background color change with set enabled
            view.findViewById(R.id.again_ending).setBackgroundColor(Color.GRAY);
            view.findViewById(R.id.again_ending).setEnabled(false);

            userViewModel.getUser().observe(this.getViewLifecycleOwner(), new Observer<User>() {
                @Override
                public void onChanged(User user) {
                    AppLog.d(AppLog.GAME_FLOW, "Host play-again changed to=" + userViewModel.getUser().getValue().hostPlayedAgain);
                    if ("yes".equals(userViewModel.getUser().getValue().hostPlayedAgain)) {
                        // if host hits again button will be clickable for rest of players
                        view.findViewById(R.id.again_ending).setEnabled(true);
                        view.findViewById(R.id.again_ending).setBackgroundColor(Color.parseColor("#FFEDA6EC"));

//                        System.out.println("MY VALUE GOT CHANGED FROM HOST LISTENER: " + userViewModel.getUser().getValue().hostPlayedAgain);

                    }
                    else if ("no".equals(userViewModel.getUser().getValue().hostPlayedAgain) && userViewModel.onEndFrag) {
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, StartFragment.class, null)
                                .setReorderingAllowed(true)
                                .addToBackStack(null)
                                .commit();
                        userViewModel.onEndFrag = false;

                        //resetting the same as if they hit the home button
                        userViewModel.reset();
                        leaderBoardViewModel.reset();
                        userViewModel.host = new MutableLiveData<User>();
//                        System.out.println("MY VALUE GOT CHANGED FROM HOST LISTENER: " + userViewModel.getUser().getValue().hostPlayedAgain);
                        UiMessenger.showBanner(view, "Host left the game room.", UiMessenger.MessageType.ERROR);
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
                userViewModel.reset();
                leaderBoardViewModel.reset();
                userViewModel.host = new MutableLiveData<User>();
                

                if (userViewModel.getUser().getValue().host) {
                    userViewModel.getUser().getValue().hostPlayedAgain = "no";
                    userViewModel.hostPlayedAgain(userViewModel.getUser().getValue());
                    leaderBoardViewModel.removeCastVotesListener(userViewModel.myRoom);
                    scheduleRoomDelete(userViewModel.getUser().getValue());
                }

                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, StartFragment.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack(null)
                        .commit();
                AppLog.i(AppLog.GAME_FLOW, "EndFrag -> StartFragment via home");
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

                // resetting viewModel attributes
                userViewModel.reset();
                leaderBoardViewModel.reset();

                // resetting db gameroom to no one in it, as they play again I will push the person back to it
                if (userViewModel.getUser().getValue().host) {
                    roomViewModel.deleteRoundAssignments(userViewModel.myRoom);
                    roomViewModel.setActiveReaderIndex(userViewModel.myRoom, 0);
                    userViewModel.nurfAllUsers();
                    if (allAccountPlayers) {
                        userViewModel.deleteVotesAndVotingItems();
                        leaderBoardViewModel.removeCastVotesListener(userViewModel.myRoom);
                        AppLog.i(AppLog.VOTE, "Cleared voting data for replay");
                    }
                    userViewModel.getUser().getValue().hostPlayedAgain = "yes";
                    userViewModel.hostPlayedAgain(userViewModel.getUser().getValue());
                }
                userViewModel.pushPerson(userViewModel.getUser());
                userViewModel.getUser().getValue().hostPlayedAgain = "";

                userViewModel.loadUsers(userViewModel.myRoom);


//            for (User user: userViewModel.getUsers()) {
//                System.out.println(" " + user.ifFinished + user.thenFinished + user.ifSentence + user.thenSentence);
//            }
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, WaitingForHostFrag.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack(null)
                        .commit();
                AppLog.i(AppLog.GAME_FLOW, "EndFrag -> WaitingForHostFrag via play again");
            }
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

}
