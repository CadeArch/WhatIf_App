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
        // dont use view model provider so it loads in leaderboard every time
        leaderBoardViewModel = new LeaderBoardViewModel();
        LinearLayout listOfLeaderBoards = view.findViewById(R.id.listOfLBIs);

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
                TextView ifThen = lbi.findViewById(R.id.if_then);
//                TextView thenPart = lbi.findViewById(R.id.then_part_lbi);
//                TextView ifContrib = lbi.findViewById(R.id.if_contributor_lbi);
                TextView stats = lbi.findViewById(R.id.stats);
//                TextView percentLoved = lbi.findViewById(R.id.loved);


                ifThen.setText(leaderBoardItem.getIfPart() + " " + leaderBoardItem.getThenPart());
                stats.setText("If Contributor: " + leaderBoardItem.getIfContributor() + "      " + "then Contributor: " +
                        leaderBoardItem.getThenContributor() + "      " + "percent vote: " + leaderBoardItem.getPercentLoved() + "%");
//                ifContrib.setText("If Contributor: " + leaderBoardItem.getIfContributor());
//                thenContrib.setText("then Contributor: " + leaderBoardItem.getThenContributor());
//                percentLoved.setText("percent vote: " + leaderBoardItem.getPercentLoved() + "%");
                listOfLeaderBoards.addView(lbi);
            }

            @Override
            public void onItemRangeMoved(ObservableList<LeaderBoardItem> sender, int fromPosition, int toPosition, int itemCount) {

            }

            @Override
            public void onItemRangeRemoved(ObservableList<LeaderBoardItem> sender, int positionStart, int itemCount) {

            }
        });

        view.findViewById(R.id.lbi_back).setOnClickListener(v -> {
            leaderBoardViewModel.setLeaderBoard(null);
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, StartFragment.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

    }

}
