package com.CadeMixedUpGame.phoneapp;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.CadeMixedUpGame.api.models.LeaderBoardItem;
import com.CadeMixedUpGame.api.viewmodels.LeaderBoardViewModel;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

import java.util.ArrayList;

public class VoteFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    LeaderBoardViewModel leaderBoardViewModel;

    public VoteFrag() {
        super(R.layout.fragment_vote);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(getActivity()).get(LeaderBoardViewModel.class);

        leaderBoardViewModel.loadTempLeaderItems(userViewModel.getUser());

        LinearLayout potentialLBIlist = view.findViewById(R.id.potential_lbiList);

        leaderBoardViewModel.getPotentialLeaderBoardItems().addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<LeaderBoardItem>>() {
            @Override
            public void onChanged(ObservableList<LeaderBoardItem> sender) {

            }

            @Override
            public void onItemRangeChanged(ObservableList<LeaderBoardItem> sender, int positionStart, int itemCount) {

            }

            @Override
            public void onItemRangeInserted(ObservableList<LeaderBoardItem> sender, int positionStart, int itemCount) {
                LeaderBoardItem leaderBoardItem = leaderBoardViewModel.getPotentialLeaderBoardItems().get(positionStart);
                View voteItem = LayoutInflater.from(getContext()).inflate(R.layout.lb_vote_item, null);
                TextView ifPart = voteItem.findViewById(R.id.if_part);
                TextView thenPart = voteItem.findViewById(R.id.then_part);
                ifPart.setText(leaderBoardItem.getIfPart());
                thenPart.setText(leaderBoardItem.getThenPart());

                potentialLBIlist.addView(voteItem);

            }

            @Override
            public void onItemRangeMoved(ObservableList<LeaderBoardItem> sender, int fromPosition, int toPosition, int itemCount) {

            }

            @Override
            public void onItemRangeRemoved(ObservableList<LeaderBoardItem> sender, int positionStart, int itemCount) {

            }
        });

        //giving home button functionality
        view.findViewById(R.id.vote_submit).setOnClickListener(v -> {
            // TODO push vote to database

            // creating toast and switching text in viewmodel
            Toast.makeText(
                    getActivity(),
                    "vote sent",
                    Toast.LENGTH_SHORT
            ).show();

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, EndFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        //giving next button functionality DONT NEED THIS BUTTON
//        view.findViewById(R.id.vote_next).setOnClickListener(v -> {
//            getActivity().getSupportFragmentManager().beginTransaction()
//                    .replace(R.id.fragment_container, EndFrag.class, null)
//                    .setReorderingAllowed(true)
//                    .addToBackStack(null)
//                    .commit();
//        });
    }
}
