package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;


public class WaitingForHostFrag extends Fragment {
    UserViewModel userViewModel;

    public WaitingForHostFrag() {
        super(R.layout.fragment_waiting_for_host);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);

        //grabbing the list of users
        ObservableArrayList<User> playerArray = userViewModel.getUsers();

        //if their User ID is 0 show the start button else hide it so only the host can begin game
        for (User n: playerArray) {
            if(n.userID == 1) {

                //giving button functionality
                view.findViewById(R.id.waitingForHost_start).setOnClickListener(v -> {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, WriteIfFrag.class, null)
                            .setReorderingAllowed(true)
                            .addToBackStack(null)
                            .commit();
                });
            }
            else {
                View button = view.findViewById(R.id.waitingForHost_start);
                button.setVisibility(View.GONE);
            }


        }

    }

}