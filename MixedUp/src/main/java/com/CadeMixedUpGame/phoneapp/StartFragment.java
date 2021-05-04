package com.CadeMixedUpGame.phoneapp;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.View;
import android.widget.EditText;

import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

import java.util.ArrayList;


public class StartFragment extends Fragment {
    RoomViewModel roomViewModel;
    UserViewModel userViewModel;


    public StartFragment() {
        super(R.layout.fragment_start);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);

        EditText usersName = view.findViewById(R.id.enterName);

        //giving create game button functionality
        view.findViewById(R.id.start).setOnClickListener(v -> {

            userViewModel.localName = usersName.getText().toString();

            //creating a new roomID to make a room and storing info locally
            String roomID = roomViewModel.makeRoomID();
            userViewModel.myRoom = roomID;

            //pushing the data to firebase
            roomViewModel.pushRoom(roomID);

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
            userViewModel.localName = usersName.getText().toString();

            //moving to the join game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, JoinGameFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });
    }
}