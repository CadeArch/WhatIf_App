package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import com.CadeMixedUpGame.api.models.LeaderBoardItem;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.LeaderBoardViewModel;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class StartFragment extends Fragment {
    RoomViewModel roomViewModel;
    UserViewModel userViewModel;
    LeaderBoardViewModel leaderBoardViewModel;

    public StartFragment() {
        super(R.layout.fragment_start);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(getActivity()).get(LeaderBoardViewModel.class);

        // checking to see if user can unlock any voices based on whether or not they are on the leaderboards
        // assuring leaderboards isnt empty so it wont break, if they are on the leaderboards they can unlock it
        // but it wont notify or try to unlock it again if the player has already unlocked that value
        System.out.println(userViewModel.getUser().getValue().accountPlay);
        if (userViewModel.getUser().getValue().accountPlay) {
            System.out.println("Leaderboard Size: " + leaderBoardViewModel.getLeaderBoard().size());
            if (userViewModel.getUser().getValue().gamesPlayed > 0) {
                userViewModel.getMadeLeaderBoard(userViewModel.getUser());
                userViewModel.getMadePerfectLeaderBoard(userViewModel.getUser());
            }
            if (leaderBoardViewModel.getLeaderBoard().size() > 0) {
                for (LeaderBoardItem lbi : leaderBoardViewModel.getLeaderBoard()) {
                    if (lbi.getIfContributorID().equals(userViewModel.getUser().getValue().getUid()) &&
                            lbi.getThenContributorID().equals(userViewModel.getUser().getValue().getUid())) {
                        if (!userViewModel.getUser().getValue().perfectLeaderBoard) {
                            userViewModel.getUser().getValue().perfectLeaderBoard = true;
                            userViewModel.unlockVoice(userViewModel.getUser(), "leaderBoards");
                            Toast.makeText(
                                    getActivity(),
                                    "unlocked pig latin google voice!",
                                    Toast.LENGTH_LONG
                            ).show();
                            System.out.println("perfect Leader Board");
                        }
                    } else if (lbi.getIfContributorID().equals(userViewModel.getUser().getValue().getUid()) ||
                            lbi.getThenContributorID().equals(userViewModel.getUser().getValue().getUid())) {
                        if (!userViewModel.getUser().getValue().madeLeaderBoard) {
                            userViewModel.getUser().getValue().madeLeaderBoard = true;
                            userViewModel.unlockVoice(userViewModel.getUser(), "leaderBoards");
                            Toast.makeText(
                                    getActivity(),
                                    "unlocked fuddify google voice!",
                                    Toast.LENGTH_LONG
                            ).show();
//                        System.out.println("on Leader Board");
                        }
                    }
                }
            }
        }

        EditText enterName = view.findViewById(R.id.enterName);
        // if user has set name autofil to what user had it set to when on this fragment
        if (!userViewModel.localName.equals("guest-")) {
            enterName.setText(userViewModel.localName.replace("guest-", ""));
        }
        TextView userName = view.findViewById(R.id.displayName);

        userViewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
//                System.out.println("Frag" + user.userName + user.accountPlay);
                if (user.accountPlay) {
                    view.findViewById(R.id.back).setVisibility(View.GONE);
                    userName.setText(user.userName);
                    enterName.setVisibility(View.GONE);
                    view.findViewById(R.id.signOut).setVisibility(View.VISIBLE);
                    view.findViewById(R.id.profile_button).setVisibility(View.VISIBLE);

                }

                // if non account play take away log out button and show back button
                else {
                    view.findViewById(R.id.signOut).setVisibility(View.GONE);
                    view.findViewById(R.id.back).setVisibility(View.VISIBLE);
                    view.findViewById(R.id.profile_button).setVisibility(View.GONE);
                }
            }
        });

        //giving create game button functionality MAYBE MAKE THIS ONLY AVAILABLE TO ACCOUNT PLAY
        view.findViewById(R.id.create_game).setOnClickListener(v -> {

            // storing the name locally to push up to firebase in the join game fragment for non account play
            if (!userViewModel.getUser().getValue().accountPlay) {
                userViewModel.localName = "guest-" + enterName.getText().toString();
                //building user for first time if in freeplay
                userViewModel.getUser().getValue().userName = userViewModel.localName;
                System.out.println(userViewModel.getUser().getValue().userName);
            }

            //creating a new roomID to make a room and storing info locally
            String roomID = roomViewModel.makeRoomID();
            userViewModel.myRoom = roomID;

            //pushing the data to firebase
            roomViewModel.pushRoom(roomID);

            MutableLiveData<User> newUser = userViewModel.getUser();
            newUser.getValue().userName = userViewModel.localName;
            newUser.getValue().host = true;
            newUser.getValue().hostStarted = false;
            newUser.getValue().gameRoom = userViewModel.myRoom;

            userViewModel.pushPerson(newUser);

            //moving to the fragment where they can share their room code
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, CreateGameFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        //giving the join game button functionality
        view.findViewById(R.id.joinGame).setOnClickListener(v -> {
            // storing the name locally to push up to firebase in the join game fragment
            if (!userViewModel.getUser().getValue().accountPlay) {
                userViewModel.localName = "guest-" + enterName.getText().toString();
                userViewModel.getUser().getValue().userName = userViewModel.localName;
                System.out.println(userViewModel.getUser().getValue().userName);
            }
            //moving to the join game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, JoinGameFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        //giving the signout button functionality
        view.findViewById(R.id.signOut).setOnClickListener(v -> {
            userViewModel.signOut();
            userViewModel.getUser().setValue(null);
            //moving to the first game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, FirstFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        //giving the back button functionality
        view.findViewById(R.id.back).setOnClickListener(v -> {
            userViewModel.getUser().setValue(null);
            //moving to the first game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, FirstFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        //giving the leaderBoards button functionality
        view.findViewById(R.id.leaderboards_button).setOnClickListener(v -> {
            //moving to the leaderboards fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, LeaderBoardFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        //giving the profile button functionality
        view.findViewById(R.id.profile_button).setOnClickListener(v -> {

            //moving to the leaderboards fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, ProfileFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

    }
}