package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
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

                //storing the room to join locally and loading in the users and pushing the user to the database
                userViewModel.loadUsers(myRoom);
                userViewModel.myRoom = myRoom;

                roomViewModel.incrementNumInRoom(myRoom);
                int newUserID = roomViewModel.getNumInRoom(myRoom);

                User newUser = new User(newUserID, userViewModel.localName);
                newUser.gameRoom = myRoom;
                roomViewModel.updateNumInRoom(newUser);
                userViewModel.pushPerson(newUser);

                //moving to the waiting for host fragment
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, WaitingForHostFrag.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack(null)
                        .commit();
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