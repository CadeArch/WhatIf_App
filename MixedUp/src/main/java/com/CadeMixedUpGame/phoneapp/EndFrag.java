package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.LeaderBoardViewModel;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

public class EndFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    LeaderBoardViewModel leaderBoardViewModel;

    public EndFrag() {
        super(R.layout.fragment_end);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(getActivity()).get(LeaderBoardViewModel.class);

        // only host can say to play again
        if (!userViewModel.getUser().getValue().host) {
            view.findViewById(R.id.again_ending).setVisibility(View.GONE);
        }

        // TODO: listen to host to see if they hit play again, if so allow players to play again if not kick everyone back to start fragment


        //giving home button functionality
        view.findViewById(R.id.home_ending).setOnClickListener(v -> {
            userViewModel.reset();
            leaderBoardViewModel.reset();

//            userViewModel.setUsers(new ObservableArrayList<User>());

            userViewModel.host = new MutableLiveData<User>();

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, StartFragment.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        //giving again button functionality
        view.findViewById(R.id.again_ending).setOnClickListener(v -> {

            userViewModel.getUser().observe(this.getViewLifecycleOwner(), new Observer<User>() {
                @Override
                public void onChanged(User user) {
                    System.out.println("noticed Change on host played again value: " + userViewModel.getUser().getValue().hostPlayedAgain);
                    if (userViewModel.host.getValue().hostPlayedAgain) {
                        view.findViewById(R.id.again_ending).setVisibility(View.VISIBLE);
                    }
                    else {
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, StartFragment.class, null)
                                .setReorderingAllowed(true)
                                .addToBackStack(null)
                                .commit();
                        System.out.println("MY VALUE GOT CHANGED FROM HOST LISTENER: " + userViewModel.getUser().getValue().hostPlayedAgain);
                    }
                }
            });

            // resetting viewModel attributes
            userViewModel.reset();
            leaderBoardViewModel.reset();

            //resetting users array SET IN RESET FUNCTION
//            userViewModel.setUsers(new ObservableArrayList<User>());

            // resetting db gameroom to no one in i, as they play again I will push the person back to it
            userViewModel.nurfAllUsers();
            userViewModel.pushPerson(userViewModel.getUser());


//            for (User user: userViewModel.getUsers()) {
//                System.out.println(" " + user.ifFinished + user.thenFinished + user.ifSentence + user.thenSentence);
//            }
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, WaitingForHostFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });
    }
}
