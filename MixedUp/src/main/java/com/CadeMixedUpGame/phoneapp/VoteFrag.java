package com.CadeMixedUpGame.phoneapp;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
    int sentencesSelected = 0;

    public VoteFrag() {
        super(R.layout.fragment_vote);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(getActivity()).get(LeaderBoardViewModel.class);

        leaderBoardViewModel.loadVotingItems(userViewModel.getUser());
        leaderBoardViewModel.createAndListenToCastVotes(userViewModel.myRoom);

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
                TextView sentID = voteItem.findViewById(R.id.sentence_id);

                ifPart.setText(leaderBoardItem.getIfPart());
                thenPart.setText(leaderBoardItem.getThenPart());
                sentID.setText(leaderBoardItem.getId());


                voteItem.setBackgroundColor(Color.parseColor("#0000FF00"));

                voteItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int color = ((ColorDrawable) v.getBackground()).getColor();
                        System.out.println(color);
                        if (color == 1107361536) {
                            v.setBackgroundColor(Color.parseColor("#0000FF00")); // 65280

                        }
                        else {
                            v.setBackgroundColor(Color.parseColor("#4200FF00")); // 1107361536
                        }

                    }
                });
                System.out.println("New Potential leaderboard sentence -- adding it to view");
                potentialLBIlist.addView(voteItem);

            }

            @Override
            public void onItemRangeMoved(ObservableList<LeaderBoardItem> sender, int fromPosition, int toPosition, int itemCount) {

            }

            @Override
            public void onItemRangeRemoved(ObservableList<LeaderBoardItem> sender, int positionStart, int itemCount) {

            }
        });

        if (userViewModel.getUser().getValue().host) {
            leaderBoardViewModel.castVoteListener(userViewModel.getUsers().size());
        }


        //giving vote button functionality
        view.findViewById(R.id.vote_submit).setOnClickListener(v -> {
            // checking to see how many sentences are selected
            sentencesSelected = 0;
            for (int i = 0; i < potentialLBIlist.getChildCount(); i++) {
                View pot = potentialLBIlist.getChildAt(i);
                int color = ((ColorDrawable) pot.getBackground()).getColor();
                if (color == 1107361536) {
                    sentencesSelected += 1;
                }
            }
            System.out.println("Sentences selected: " + sentencesSelected);
            // if more than one guide user
            if (sentencesSelected > 1 || sentencesSelected == 0) {
                // creating toast and switching text in viewmodel
                Toast.makeText(
                        getActivity(),
                        "Please select 1 sentence",
                        Toast.LENGTH_SHORT
                ).show();
            }
            // if only one, make the vote add it to list of cast votes
            else {

                // saved vote in castVotes array
                for (int i = 0; i < potentialLBIlist.getChildCount(); i++) {
                    View pot = potentialLBIlist.getChildAt(i);
                    int color = ((ColorDrawable) pot.getBackground()).getColor();
                    if (color == 1107361536) {
                        TextView selectedVoteID = pot.findViewById(R.id.sentence_id);
                        String id = selectedVoteID.getText().toString();
                        leaderBoardViewModel.castVote(userViewModel.getUser(), id);
                        System.out.println("submitted vote");
                        break;
                    }
                }
                // telling user vote was sent
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

            }

        });

    }
}
