package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.CadeMixedUpGame.api.models.LeaderBoardItem;
import com.CadeMixedUpGame.api.viewmodels.LeaderBoardViewModel;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

public class LeaderBoardFrag extends Fragment {
    RoomViewModel roomViewModel;
    UserViewModel userViewModel;
    LeaderBoardViewModel leaderBoardViewModel;

    public LeaderBoardFrag() {
        super(R.layout.fragment_leaderboard);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(getActivity()).get(LeaderBoardViewModel.class);

        LinearLayout listOfLeaderBoards = view.findViewById(R.id.listOfLBIs);

//        leaderBoardViewModel.loadLeaderBoardItems();
        leaderBoardViewModel.getLeaderBoard().addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<LeaderBoardItem>>() {
            @Override
            public void onChanged(ObservableList<LeaderBoardItem> sender) {

            }

            @Override
            public void onItemRangeChanged(ObservableList<LeaderBoardItem> sender, int positionStart, int itemCount) {

            }

            @Override
            public void onItemRangeInserted(ObservableList<LeaderBoardItem> sender, int positionStart, int itemCount) {
                LeaderBoardItem leaderBoardItem = leaderBoardViewModel.getLeaderBoard().get(positionStart);
                View lbi = LayoutInflater.from(getContext()).inflate(R.layout.lb_item, null);
                TextView ifPart = lbi.findViewById(R.id.if_part_lbi);
                TextView thenPart = lbi.findViewById(R.id.then_part_lbi);
                TextView ifContrib = lbi.findViewById(R.id.if_contributor_lbi);
                TextView thenContrib = lbi.findViewById(R.id.then_contributor_lbi);


                ifPart.setText(leaderBoardItem.getIfPart());
                thenPart.setText(leaderBoardItem.getThenPart());
                ifContrib.setText("If Contributor: " + leaderBoardItem.getIfContributor());
                thenContrib.setText("then Contributor: " + leaderBoardItem.getThenContributor());

                listOfLeaderBoards.addView(lbi);
            }

            @Override
            public void onItemRangeMoved(ObservableList<LeaderBoardItem> sender, int fromPosition, int toPosition, int itemCount) {

            }

            @Override
            public void onItemRangeRemoved(ObservableList<LeaderBoardItem> sender, int positionStart, int itemCount) {

            }
        });


    }

}
