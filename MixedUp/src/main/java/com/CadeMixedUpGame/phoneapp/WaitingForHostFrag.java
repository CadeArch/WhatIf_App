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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.GridLayout;

import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Observable;


public class WaitingForHostFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;


    public WaitingForHostFrag() {
        super(R.layout.fragment_waiting_for_host);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);


        // set up the RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 2));
        MyRecyclerViewAdapter adapter = new MyRecyclerViewAdapter(getActivity(), userViewModel.getUsers());
        recyclerView.setAdapter(adapter);

        // when the users array changes reset the adapter to include all people
        userViewModel.getUsers().addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<User>>() {
            @Override
            public void onChanged(ObservableList<User> sender) {

            }

            @Override
            public void onItemRangeChanged(ObservableList<User> sender, int positionStart, int itemCount) {

            }

            @Override
            public void onItemRangeInserted(ObservableList<User> sender, int positionStart, int itemCount) {
                recyclerView.setAdapter(adapter);
            }

            @Override
            public void onItemRangeMoved(ObservableList<User> sender, int fromPosition, int toPosition, int itemCount) {

            }

            @Override
            public void onItemRangeRemoved(ObservableList<User> sender, int positionStart, int itemCount) {

            }
        });

        // if the current persons device is the host set the host value since the host wont have gone through the join game frag
        if (userViewModel.getUser().getValue().host) {
            userViewModel.host = userViewModel.getUser();
            System.out.println("I am the host and have set the host value: " + userViewModel.host.getValue().userName);
        }


        //looping through all the users that have joined
        for (User user: userViewModel.getUsers()) {
            System.out.println("hit Loop");
            //grabbing each individual on their phone
            if (user.userName.equals(userViewModel.localName)) {
                System.out.println("I am " + user.userName + userViewModel.localName);
                //if they are the host show the button else hide it
                System.out.println("i am the host: " + user.host);
                if (user.host) {
                    //giving button functionalityl
                    view.findViewById(R.id.waitingForHost_start).setOnClickListener(v -> {
                        // host started game and setting the value in firebase to be true
                        System.out.println("HOST CLICKED BUTTON ------------------");
                        userViewModel.getUser().getValue().hostStarted = true;
                        userViewModel.hostStarted(user);
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

                    //could take out of mutable live data observer because host will for sure be pushed to database before other players join
                    if (userViewModel.host.getValue() != null) {
//                        System.out.println("HOST HAS BEEN SET --------" + userViewModel.host.getValue().userName);
                        userViewModel.host.observe(getViewLifecycleOwner(), theHost -> {

//                            System.out.println("HOST JOINED ------------- LISTENING TO HOST");
                            //setting a listener on the host in firebase
                            userViewModel.listenToHost(userViewModel.host);
                        });
                    }


                    // when observer notices change on user data move to next frag
                    userViewModel.getUser().observe(this.getViewLifecycleOwner(), new Observer<User>() {

                            @Override
                            public void onChanged(User user) {
//                                System.out.println("MY USERNAME: " + user.userName);
//                                System.out.println("Host started: " + user.hostStarted);
                                //if host has clicked the button move to next screen
                                userViewModel.hostStarted(userViewModel.getUser().getValue());

                                if (user.hostStarted && !user.ifFinished) {
                                    System.out.println("-------------" + user.hostStarted + user.ifFinished);
                                    System.out.println("-------------- SwiTCHED TO WRITE IF FRAG");
                                    getActivity().getSupportFragmentManager().beginTransaction()
                                            .replace(R.id.fragment_container, WriteIfFrag.class, null)
                                            .setReorderingAllowed(true)
                                            .addToBackStack(null)
                                            .commit();
                                    System.out.println("MY VALUE GOT CHANGED FROM HOST LISTENER: " + userViewModel.getUser().getValue().hostStarted);
//                                    userViewModel.getUser().removeObservers(getViewLifecycleOwner());
                                }
                            }
                    });

                }
            }
        }
    }
}