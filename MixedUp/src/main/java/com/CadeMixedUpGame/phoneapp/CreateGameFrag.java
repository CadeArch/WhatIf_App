package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.view.View;

import android.widget.TextView;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class CreateGameFrag extends Fragment {

    UserViewModel userViewModel;
    private boolean roomCleanupRequested = false;

    public CreateGameFrag() {
        super(R.layout.fragment_create_game);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);

        TextView replace = view.findViewById(R.id.replace);
        if (userViewModel.myRoom == null || userViewModel.myRoom.length() == 0) {
            UiMessenger.showSnackbar(view, "Game room was not created. Go back and try again.");
            AppLog.w(AppLog.ROOM, "Create game screen opened without room id");
        }
        replace.setText(userViewModel.myRoom);

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                cleanupRoomAndReturnHome(view, "android back");
            }
        });

        //giving back button functionality
        view.findViewById(R.id.createGame_back).setOnClickListener(v -> {
            cleanupRoomAndReturnHome(view, "back button");
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

    private void cleanupRoomAndReturnHome(View view, String reason) {
        if (roomCleanupRequested) {
            return;
        }
        roomCleanupRequested = true;
        AppLog.i(AppLog.ROOM, "Cleaning up unstarted room from CreateGameFrag reason=" + reason + ", room=" + userViewModel.myRoom);
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0) {
            navigateHome();
            return;
        }
        userViewModel.deleteRoom(currentUser, this::navigateHome);
    }

    private void navigateHome() {
        if (!isAdded()) {
            return;
        }
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, StartFragment.class, null)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit();
    }
}
