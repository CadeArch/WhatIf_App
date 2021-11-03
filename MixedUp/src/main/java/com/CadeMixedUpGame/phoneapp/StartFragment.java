package com.CadeMixedUpGame.phoneapp;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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

        EditText enterName = view.findViewById(R.id.enterName);
        TextView userName = view.findViewById(R.id.displayName);

        userViewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
//                System.out.println("Frag" + user.userName + user.accountPlay);
                if (user.accountPlay) {
                    view.findViewById(R.id.back).setVisibility(View.GONE);
                    userName.setText(user.userName);
                    enterName.setVisibility(View.GONE);
                }

                // if non account play take away log out button and show back button
                else {
                    view.findViewById(R.id.signOut).setVisibility(View.GONE);

                }
            }
        });

        //giving create game button functionality MAYBE MAKE THIS ONLY AVAILABLE TO ACCOUNT PLAY
        view.findViewById(R.id.start).setOnClickListener(v -> {

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
            //moving to the first game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, FirstFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        //giving the back button functionality
        view.findViewById(R.id.back).setOnClickListener(v -> {
            //moving to the first game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, FirstFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

    }
}