package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
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
            userViewModel.host = new MutableLiveData<User>();
            userViewModel.removeUserFromGameRoom(userViewModel.getUser());

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, StartFragment.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        //giving again button functionality
        view.findViewById(R.id.again_ending).setOnClickListener(v -> {

            // here I will need to reset players values and database values to how they would be
            // at the start of a match
            userViewModel.reset();
            leaderBoardViewModel.reset();
            userViewModel.reInitUserVals(userViewModel.getUser());
            userViewModel.getUsers().remove(userViewModel.getUser().getValue());
            userViewModel.getUsers().add(userViewModel.getUser().getValue());
            for (User user: userViewModel.getUsers()) {
                System.out.println(" " + user.ifFinished + user.thenFinished + user.ifSentence + user.thenSentence);
            }
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, WaitingForHostFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });
    }
}
