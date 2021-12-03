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
import android.widget.TextView;
import android.widget.Toast;

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

        // this will unlock voices based on number of games played unlocking here in case players hit the play again button
        if (userViewModel.getUser().getValue().accountPlay) {
            userViewModel.unlockVoice(userViewModel.getUser(), "numGames");
            if (userViewModel.getUser().getValue().gamesPlayed == 5) {
                Toast.makeText(
                        getActivity(),
                        "unlocked backwords google voice!",
                        Toast.LENGTH_LONG
                ).show();
            }
        }

        TextView gameCode = view.findViewById(R.id.gameCode);
        gameCode.setText(userViewModel.getUser().getValue().gameRoom);


        roomViewModel.gameInProgressFalse(userViewModel.getUser().getValue().gameRoom);
        // set up the RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        MyRecyclerViewAdapter adapter = new MyRecyclerViewAdapter(getContext(), userViewModel.getUsers());
        recyclerView.setAdapter(adapter);

        System.out.println("User array size upon entry to WFHF: " + userViewModel.getUsers().size());

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
                System.out.println("waitingForHost: inserting name");
                System.out.println("users array size: WFHF: " + userViewModel.getUsers().size());
//                MyRecyclerViewAdapter adapter = new MyRecyclerViewAdapter(getContext(), userViewModel.getUsers());
                    recyclerView.setAdapter(adapter);

            }

            @Override
            public void onItemRangeMoved(ObservableList<User> sender, int fromPosition, int toPosition, int itemCount) {

            }

            @Override
            public void onItemRangeRemoved(ObservableList<User> sender, int positionStart, int itemCount) {
//                MyRecyclerViewAdapter adapter = new MyRecyclerViewAdapter(getActivity(), userViewModel.getUsers());
//                recyclerView.setAdapter(adapter);
            }
        });

        // if the current persons device is the host set the host value since the host wont have gone through the join game frag
        if (userViewModel.getUser().getValue().host) {
            userViewModel.host = userViewModel.getUser();
            System.out.println("I am the host and have set the host value: " + userViewModel.host.getValue().userName);
        }

        // giving button functionality
        view.findViewById(R.id.waitingForHost_start).setOnClickListener(v -> {
            // host started game and setting the value in firebase to be true
            System.out.println("HOST CLICKED BUTTON ------------------");
            roomViewModel.gameInProgressTrue();
            userViewModel.getUser().getValue().hostStarted = true;
            userViewModel.playing = true;
            userViewModel.hostStarted(userViewModel.host.getValue());
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, WriteIfFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        if(!userViewModel.getUser().getValue().host) {
            // making button invisible since they arent the host
            View button = view.findViewById(R.id.waitingForHost_start);
            button.setVisibility(View.GONE);

            // setting listener on host location in database if hostStarted changes to true
            // hostStarted will be changed to true on this user
            userViewModel.listenToHost(userViewModel.host);
            System.out.println("This user isnt host: HOST IS: " + userViewModel.host.getValue().userName);
            // when observer notices change on user data move to next frag
            userViewModel.getUser().observe(this.getViewLifecycleOwner(), new Observer<User>() {
                @Override
                public void onChanged(User user) {
//                    System.out.println("MY USERNAME: " + user.userName);
//                    System.out.println("Host started: " + user.hostStarted);
                    //if host has clicked the button move to next screen
                    userViewModel.hostStarted(userViewModel.getUser().getValue());

                    if (user.hostStarted && !user.ifFinished && !userViewModel.onWriteIf) {
                        System.out.println("-------------" + user.hostStarted + user.ifFinished);
                        System.out.println("-------------- SWITCHED TO WRITE IF FRAG");
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, WriteIfFrag.class, null)
                                .setReorderingAllowed(true)
                                .addToBackStack(null)
                                .commit();
                        System.out.println("MY VALUE GOT CHANGED FROM HOST LISTENER: " + userViewModel.getUser().getValue().hostStarted);
                        userViewModel.onWriteIf = true;
                        userViewModel.playing = true;
                    }
                }
            });
        }
    }
}