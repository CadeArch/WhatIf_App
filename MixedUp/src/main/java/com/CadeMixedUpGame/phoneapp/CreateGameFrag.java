package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.view.View;

import android.widget.TextView;

import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class CreateGameFrag extends Fragment {

    RoomViewModel roomViewModel;
    UserViewModel userViewModel;

    public CreateGameFrag() {
        super(R.layout.fragment_create_game);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);

        TextView replace = view.findViewById(R.id.replace);
        replace.setText(userViewModel.myRoom);


        //giving back button functionality
        view.findViewById(R.id.createGame_back).setOnClickListener(v -> {
            roomViewModel.deleteRoom(userViewModel.myRoom);

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, StartFragment.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });


        //giving start button functionality
        view.findViewById(R.id.createGame_start).setOnClickListener(v -> {

            //making a new user and adding it to the users array
            User newUser = new User(userViewModel.localName);
            newUser.host = true;
            newUser.gameRoom = userViewModel.myRoom;
            userViewModel.loadUsers(userViewModel.myRoom, userViewModel.localName);

            userViewModel.pushPerson(newUser);

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, WaitingForHostFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });
    }
}