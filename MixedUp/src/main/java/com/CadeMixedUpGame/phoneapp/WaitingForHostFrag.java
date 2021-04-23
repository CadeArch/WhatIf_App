package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
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
    ObservableArrayList<User> playerArray;
    UserViewModel userViewModel;

    public WaitingForHostFrag() {
        super(R.layout.fragment_waiting_for_host);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        userViewModel = new ViewModelProvider(super.getActivity()).get(UserViewModel.class);

        playerArray = userViewModel.getUsers();

        for (User n: playerArray) {
            if(n.userID.equals("0")) {
                //giving button functionality

                view.findViewById(R.id.waitingForHost_start).setOnClickListener(v -> {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, WaitingForHostFrag.class, null)
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