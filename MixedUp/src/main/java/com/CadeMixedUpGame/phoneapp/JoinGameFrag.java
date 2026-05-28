package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;
import java.util.ArrayList;

public class JoinGameFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    boolean loadedUsers = false;

    public JoinGameFrag() {
        super(R.layout.fragment_join_game);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //giving back button functionality
        view.findViewById(R.id.joinGame_back).setOnClickListener(v -> {
            Utils.navigateToFragment(getActivity(), StartFragment.class);
        });

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);

        EditText roomToJoin = view.findViewById(R.id.enterGameCode);

        // giving joinGame start button functionality
        view.findViewById(R.id.joinGame_start).setOnClickListener(v -> {
            ArrayList<String> allrooms = roomViewModel.roomNames;
            String myRoom = roomToJoin.getText().toString();

            // if the room they want to join exists out there it will add them to the room and push their
            // data to firebase, else it will let the user know it doesn't exist
            // TODO: functionalize a bit more for readability
            if (allrooms.contains(myRoom)) {
                roomViewModel.checkIfInProgress(myRoom);
                roomViewModel.inProgress.observe(this.getViewLifecycleOwner(), new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean aBoolean) {
                        if (!aBoolean) {
                            // storing the room to join locally and loading in the users and pushing the user to the database
                            if (!loadedUsers) {
                                userViewModel.loadUsers(myRoom);
                                loadedUsers = true;
                            }
                            userViewModel.myRoom = myRoom;

                            //storing users game-room locally
                            userViewModel.getUser().getValue().gameRoom = myRoom;

                            // todo check to see if this will assure that someone who has been a host isn't anymore when they join a match
                            userViewModel.getUser().getValue().host = false;
                            userViewModel.getUser().getValue().hostStarted = false;
                            userViewModel.pushPerson(userViewModel.getUser());

                            //moving to the waiting for host fragment when firebase recieves new user and puts it into observable arraylist
                            userViewModel.getUsers().addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<User>>() {
                                @Override
                                public void onChanged(ObservableList<User> sender) {

                                }

                                @Override
                                public void onItemRangeChanged(ObservableList<User> sender, int positionStart, int itemCount) {

                                }

                                @Override
                                public void onItemRangeInserted(ObservableList<User> sender, int positionStart, int itemCount) {
                                    // if the user exists in the observable array then we know that its gotten pushed to firebase and added to my observable array
//                                    System.out.println("num in user array JOIN FRAG----" + userViewModel.getUsers().size());
//                                    for (User person:userViewModel.getUsers()) {
//                                        System.out.println("PERSON------" + person.userName);
//                                    }
                                    // TODO this gets run A BUNCH
                                    // could figure out how to use the contains array function?
                                    for (User player : userViewModel.getUsers()) {
                                        if (userViewModel.localName.equals(player.userName) && !player.hostStarted && !userViewModel.onWaitingForHost) {
                                            Utils.navigateToFragment(getActivity(), WaitingForHostFrag.class);
                                            userViewModel.onWaitingForHost = true;
                                        }
                                        if (player.host) {
                                            System.out.println(player.userName + ": THIS GUY IS HOST");
                                            userViewModel.host.setValue(player);
                                        }
                                    }
                                }

                                @Override
                                public void onItemRangeMoved(ObservableList<User> sender, int fromPosition, int toPosition, int itemCount) {

                                }

                                @Override
                                public void onItemRangeRemoved(ObservableList<User> sender, int positionStart, int itemCount) {

                                }
                            });
                        }
                        else {
                            //letting the user know that that game-room doesn't exist
                            Toast.makeText(
                                getActivity(),
                                "Game in progress!",
                                Toast.LENGTH_LONG
                            ).show();
                        }
                    }
                });
            }
            else {
                //letting the user know that that game-room doesn't exist
                Toast.makeText(
                    getActivity(),
                    "That Game Room Doesn't Exist!",
                    Toast.LENGTH_LONG
                ).show();
            }
        });
    }
}