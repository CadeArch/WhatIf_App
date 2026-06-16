package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
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

        userViewModel.signInMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showSnackbar(view, message);
            }
        });

        userViewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user == null) {
                Utils.navigateToFragment(getActivity(), FirstFrag.class);
            }
        });

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

        view.findViewById(R.id.delete_account).setOnClickListener(v -> confirmAccountDeletion());

    }

    private void confirmAccountDeletion() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete account?")
                .setMessage("This removes your account, profile progress, and unlockables. This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> userViewModel.deleteAccount())
                .show();
    }

}
