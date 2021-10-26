package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Observable;


public class WaitingForHostFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;

    User me;

    public WaitingForHostFrag() {
        super(R.layout.fragment_waiting_for_host);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);

        //grabbing the list of users
        ObservableArrayList<User> playerArray = userViewModel.getUsers();

        //looping through all the users that have joined
        for (User n: playerArray) {
            //grabbing each individual on their phone
            if (n.userName.equals(userViewModel.localName)) {

                //if they are the host show the button else hide it
                if (n.host) {
                    //giving button functionalityl
                    view.findViewById(R.id.waitingForHost_start).setOnClickListener(v -> {
                        // host started game and setting the value in firebase to be true
                        System.out.println("HOST CLICKED BUTTON ------------------");
                        userViewModel.getUser().getValue().hostStarted = true;
                        userViewModel.hostStarted(n);
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, WriteIfFrag.class, null)
                                .setReorderingAllowed(true)
                                .addToBackStack(null)
                                .commit();
                    });
                }
                else {
                    //making button invisible since they arent the host
                    View button = view.findViewById(R.id.waitingForHost_start);
                    button.setVisibility(View.GONE);

                    //finding the player and the host
                    if (userViewModel.host.getValue() != null) {
                        System.out.println("HOST HAS BEEN SET --------" + userViewModel.host.getValue().userName);
                        userViewModel.host.observe(getViewLifecycleOwner(), theHost -> {

                            System.out.println("HOST JOINED ------------- LISTENING TO HOST");
                            //setting a listener on the host in firebase
                            userViewModel.listenToHost(userViewModel.host);
                        });
                    }

//                    //is this necessary
//                    userViewModel.getUser().removeObservers(getViewLifecycleOwner());
//                    System.out.println("has active observer: " + userViewModel.getUser().hasActiveObservers());
//
//                    // when observer notices change on user data move to next frag LIVE DATA MEDIAATOR?
                    userViewModel.getUser().observe(this.getViewLifecycleOwner(), new Observer<User>() {

                            @Override
                            public void onChanged(User user) {
                                System.out.println("MY USERNAME: " + user.userName);
                                System.out.println("Host started: " + user.hostStarted);
                                //if host has clicked the button move to next screen
                                if (user.hostStarted) {
                                    getActivity().getSupportFragmentManager().beginTransaction()
                                            .replace(R.id.fragment_container, WriteIfFrag.class, null)
                                            .setReorderingAllowed(true)
                                            .addToBackStack(null)
                                            .commit();
                                    System.out.println("MY VALUE GOT CHANGED FROM HOST LISTENER: " + userViewModel.getUser().getValue().hostStarted);
                                }
                            }

                    });

                    //waiting tell the observable arraylist changes to move to the next screen
                    // can I do this even though I have one in the join game fragment?
//                    userViewModel.getUsers().addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<User>>() {
//                        @Override
//                        public void onChanged(ObservableList<User> sender) {
//
//                        }
//
//                        @Override
//                        public void onItemRangeChanged(ObservableList<User> sender, int positionStart, int itemCount) {
//                        //if host has clicked the button move to next screen
//                            System.out.println("NOTICE CHANGE");
//                            System.out.println(userViewModel.getUser().getValue().hostStarted);
//                            if (userViewModel.getUser().getValue().hostStarted) {
//                                getActivity().getSupportFragmentManager().beginTransaction()
//                                        .replace(R.id.fragment_container, WriteIfFrag.class, null)
//                                        .setReorderingAllowed(true)
//                                        .addToBackStack(null)
//                                        .commit();
//                            }
//                            System.out.println("MY VALUE GOT CHANGED FROM HOST LISTENER" + userViewModel.getUser().getValue().hostStarted);
//                        }
//
//                        @Override
//                        public void onItemRangeInserted(ObservableList<User> sender, int positionStart, int itemCount) {
//
//                        }
//
//                        @Override
//                        public void onItemRangeMoved(ObservableList<User> sender, int fromPosition, int toPosition, int itemCount) {
//
//                        }
//
//                        @Override
//                        public void onItemRangeRemoved(ObservableList<User> sender, int positionStart, int itemCount) {
//
//                        }
//                    });

                }
            }
        }
    }
}