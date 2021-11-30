package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.CadeMixedUpGame.api.viewmodels.LeaderBoardViewModel;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

public class ProfileFrag extends Fragment {

    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    LeaderBoardViewModel leaderBoardViewModel;
    boolean allAccountPlayers = false;

    public ProfileFrag() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(getActivity()).get(LeaderBoardViewModel.class);

        TextView name = view.findViewById(R.id.player_name_profile);
        TextView gamesPlayed = view.findViewById(R.id.games_played);
        TextView email = view.findViewById(R.id.user_email);
        TextView madeLeader = view.findViewById(R.id.made_leaderboard);
        TextView perfectLeader = view.findViewById(R.id.perfect_leaderboard);

        name.setText(name.getText() + userViewModel.getUser().getValue().userName);
        gamesPlayed.setText(gamesPlayed.getText() + Integer.toString(userViewModel.getUser().getValue().gamesPlayed));
        email.setText(email.getText() + userViewModel.getUser().getValue().email);
        madeLeader.setText(madeLeader.getText() + Boolean.toString(userViewModel.getUser().getValue().madeLeaderBoard));
        perfectLeader.setText(perfectLeader.getText() + Boolean.toString(userViewModel.getUser().getValue().perfectLeaderBoard));

        //giving the profile back button functionality
        view.findViewById(R.id.back_profile).setOnClickListener(v -> {
            //moving to the leaderboards fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, StartFragment.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });


    }

}
