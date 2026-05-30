package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import android.view.View;

import android.widget.TextView;

import com.CadeMixedUpGame.api.AppLog;
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
        if (userViewModel.myRoom == null || userViewModel.myRoom.length() == 0) {
            UiMessenger.showSnackbar(view, "Game room was not created. Go back and try again.");
            AppLog.w(AppLog.ROOM, "Create game screen opened without room id");
        }
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
            if (userViewModel.myRoom == null || userViewModel.myRoom.length() == 0) {
                UiMessenger.showSnackbar(view, "Game room was not created. Go back and try again.");
                AppLog.w(AppLog.ROOM, "Create game start blocked: missing room id");
                return;
            }


            userViewModel.loadUsers(userViewModel.myRoom);


            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, WaitingForHostFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });
    }
}
