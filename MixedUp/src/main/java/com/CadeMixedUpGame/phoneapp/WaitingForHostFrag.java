package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
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
    User host;
    User me;

    public WaitingForHostFrag() {
        super(R.layout.fragment_waiting_for_host);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);

        //grabbing the list of users
        ObservableArrayList<User> playerArray = userViewModel.getUsers();

        //looping through all the users that have joined
        for (User n: playerArray) {
            //grabbing each individual on their phone
            if (n.userName.equals(userViewModel.localName)) {

                //if they are the host show the button else hide it
                if (n.host) {
                    //giving button functionality
                    view.findViewById(R.id.waitingForHost_start).setOnClickListener(v -> {
                        // host started game and setting the value in firebase to be true
                        n.hostStarted = true;
                        userViewModel.hostStarted(n);
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, WriteIfFrag.class, null)
                                .setReorderingAllowed(true)
                                .addToBackStack(null)
                                .commit();
                    });
                }
                else {
                    //finding the player and the host
                    for (User p : playerArray) {
                        if (p.host) {
                            host = p;
                        }
                        if(p.userName.equals(userViewModel.localName)) {
                            me = p;
                        }
                    }
                    //making button invisible since they arent the host
                    View button = view.findViewById(R.id.waitingForHost_start);
                    button.setVisibility(View.GONE);
                    //setting a listener on the host in firebase
                    userViewModel.listenToHost(host);

                    //waiting tell the observable arraylist changes to move to the next screen
                    userViewModel.getUsers().addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<User>>() {
                        @Override
                        public void onChanged(ObservableList<User> sender) {

                        }

                        @Override
                        public void onItemRangeChanged(ObservableList<User> sender, int positionStart, int itemCount) {
                        //if host has clicked the button move to next screen
                            if (me.hostStarted) {
                                getActivity().getSupportFragmentManager().beginTransaction()
                                        .replace(R.id.fragment_container, WriteIfFrag.class, null)
                                        .setReorderingAllowed(true)
                                        .addToBackStack(null)
                                        .commit();
                            }
                            System.out.println(me.hostStarted);
                        }

                        @Override
                        public void onItemRangeInserted(ObservableList<User> sender, int positionStart, int itemCount) {

                        }

                        @Override
                        public void onItemRangeMoved(ObservableList<User> sender, int fromPosition, int toPosition, int itemCount) {

                        }

                        @Override
                        public void onItemRangeRemoved(ObservableList<User> sender, int positionStart, int itemCount) {

                        }
                    });

                }
            }
        }
    }
}