package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

import java.util.ArrayList;


public class JoinGameFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;

    public JoinGameFrag() {
        super(R.layout.fragment_join_game);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //giving back button functionality
        view.findViewById(R.id.joinGame_back).setOnClickListener(v -> {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, StartFragment.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);

        EditText roomToJoin = view.findViewById(R.id.enterGameCode);
        //giving joinGame start button funcitonality
        view.findViewById(R.id.joinGame_start).setOnClickListener(v -> {

            ArrayList<String> allrooms = roomViewModel.roomNames;
            String myRoom = roomToJoin.getText().toString();

            //if the room they want to join exists out there it will add them to the room and push their
            // data to firebase, else it will let the user know it doesnt exist
            if (allrooms.contains(myRoom)) {
                // USE VIEWMODEL HERE
                // check whats going on?
//                MutableLiveData<User> newUser = new MutableLiveData<User>(new User(userViewModel.localName));
                for (String room:allrooms) {
                    System.out.println(room);
                }
                //storing the room to join locally and loading in the users and pushing the user to the database
//                System.out.println("userName--joinGame ---" + userViewModel.localName);
                userViewModel.loadUsers(myRoom, userViewModel.localName );
                userViewModel.myRoom = myRoom;

                //storing users gameroom locally
                userViewModel.getUser().getValue().gameRoom = myRoom;

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
                        System.out.println("num in user array JOIN FRAG----" + userViewModel.getUsers().size());
//                        for (User person:userViewModel.getUsers()) {
//                            System.out.println("PERSON------" + person.userName);
//                        }
                        // could figure out how to use the contains array function?
                        for (User player:userViewModel.getUsers()) {
                            // if the local name matches the players name move to waiting for host.
                            // and if host started = false fixes issue when moving to collecting ifs frag
                            if (userViewModel.localName.equals(player.userName) && !player.hostStarted) {
                                getActivity().getSupportFragmentManager().beginTransaction()
                                        .replace(R.id.fragment_container, WaitingForHostFrag.class, null)
                                        .setReorderingAllowed(true)
                                        .addToBackStack(null)
                                        .commit();
                            }
                            if (player.host) {
//                                System.out.println(player.userName + ": THIS GUY IS HOST");
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
                //letting the user know that that gameroom doesnt exist
                Toast.makeText(
                        getActivity(),
                        "That Game Room Doesnt Exist!",
                        Toast.LENGTH_LONG
                ).show();
            }
        });

    }
}