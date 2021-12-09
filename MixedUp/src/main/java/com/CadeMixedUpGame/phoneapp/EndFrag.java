package com.CadeMixedUpGame.phoneapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

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

    public EndFrag() {
        super(R.layout.fragment_end);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(getActivity()).get(LeaderBoardViewModel.class);
        System.out.println("switching to end frag");
        System.out.println("END FRAG: users and cast votes: " + userViewModel.getUsers().size() + " " + leaderBoardViewModel.getCastvotes().size());

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

        userViewModel.removeListenerOnDB();
        userViewModel.getUser().getValue().hostPlayedAgain = "";

        System.out.println("endFrag: " + userViewModel.getUser().getValue().userName + userViewModel.getUser().getValue().host);
        userViewModel.onEndFrag = true;
        // only host can say to play again
        if (!userViewModel.getUser().getValue().host) {
            // may not need the background color change with set enabled
            view.findViewById(R.id.again_ending).setBackgroundColor(Color.GRAY);
            view.findViewById(R.id.again_ending).setEnabled(false);

            userViewModel.getUser().observe(this.getViewLifecycleOwner(), new Observer<User>() {
                @Override
                public void onChanged(User user) {
                    System.out.println("END FRAG: noticed Change on host played again value: " + userViewModel.getUser().getValue().hostPlayedAgain);
                    if (userViewModel.getUser().getValue().hostPlayedAgain.equals("yes")) {
                        // if host hits again button will be clickable for rest of players
                        view.findViewById(R.id.again_ending).setEnabled(true);
                        view.findViewById(R.id.again_ending).setBackgroundColor(Color.parseColor("#FFEDA6EC"));

                        System.out.println("MY VALUE GOT CHANGED FROM HOST LISTENER: " + userViewModel.getUser().getValue().hostPlayedAgain);

                    }
                    else if (userViewModel.getUser().getValue().hostPlayedAgain.equals("no") && userViewModel.onEndFrag) {
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
                        System.out.println("MY VALUE GOT CHANGED FROM HOST LISTENER: " + userViewModel.getUser().getValue().hostPlayedAgain);
                        //letting the user know that host quit game
                        Toast.makeText(
                                getActivity(),
                                "host left gameroom!",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
            });
        }


        //giving home button functionality
        view.findViewById(R.id.home_ending).setOnClickListener(v -> {

            if (userViewModel.getUsers().size() != leaderBoardViewModel.getCastvotes().size() && allAccountPlayers) {
                Toast.makeText(
                        getActivity(),
                        "not all votes sent",
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                userViewModel.reset();
                leaderBoardViewModel.reset();
                userViewModel.host = new MutableLiveData<User>();
                

                if (userViewModel.getUser().getValue().host) {
                    userViewModel.getUser().getValue().hostPlayedAgain = "no";
                    userViewModel.hostPlayedAgain(userViewModel.getUser().getValue());
                    userViewModel.deleteRoom(userViewModel.getUser().getValue());
                }

                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, StartFragment.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack(null)
                        .commit();
            }
        });

        //giving again button functionality
        view.findViewById(R.id.again_ending).setOnClickListener(v -> {
            System.out.println("END FRAG: hit again");

            if (userViewModel.getUsers().size() != leaderBoardViewModel.getCastvotes().size() && allAccountPlayers) {
                Toast.makeText(
                        getActivity(),
                        "not all votes sent",
                        Toast.LENGTH_SHORT
                ).show();
            }
            else {

                // resetting viewModel attributes
                userViewModel.reset();
                leaderBoardViewModel.reset();

                // resetting db gameroom to no one in it, as they play again I will push the person back to it
                if (userViewModel.getUser().getValue().host) {
                    userViewModel.nurfAllUsers();
                    if (allAccountPlayers) {
                        userViewModel.deleteVotesAndVotingItems();
                        leaderBoardViewModel.removeCastVotesListener(userViewModel.myRoom);
                        System.out.println("End frag - All are account players");
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
            }
        });

    }
}
