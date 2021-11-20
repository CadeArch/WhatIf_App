package com.CadeMixedUpGame.api.viewmodels;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.models.LeaderBoardItem;
import com.CadeMixedUpGame.api.models.User;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class LeaderBoardViewModel extends ViewModel {
    ObservableArrayList<LeaderBoardItem> leaderBoard;
    DatabaseReference db;
    ObservableArrayList<LeaderBoardItem> potentialLeaderBoardItems;
    ObservableArrayList<String> castvotes = new ObservableArrayList<>();

    public LeaderBoardViewModel() {
        db = FirebaseDatabase.getInstance().getReference();
        if (leaderBoard == null) {
            leaderBoard = new ObservableArrayList<>();
        }
        if (potentialLeaderBoardItems == null) {
            potentialLeaderBoardItems = new ObservableArrayList<>();
        }
    }

    public void loadLeaderBoardItems() {
        db.child("leaderBoard").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String child = snapshot.getKey();
                System.out.println(child);

                for(DataSnapshot ds : snapshot.getChildren()) {
                    LeaderBoardItem lbItem = ds.getValue(LeaderBoardItem.class);
//                    System.out.println("DB-NEW L-B-Item ADDED---------- " + lbItem.ifPart + lbItem.thenPart);
                    leaderBoard.add(lbItem);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    public void loadVotingItems(MutableLiveData<User> user) {
        db.child("rooms").child(user.getValue().gameRoom).child("votingItems").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                System.out.println(snapshot);
                LeaderBoardItem lbItem = snapshot.getValue(LeaderBoardItem.class);
                potentialLeaderBoardItems.add(lbItem);

                for(DataSnapshot ds : snapshot.getChildren()) {
//                    System.out.println("DB-NEW L-B-Item ADDED---------- " + lbItem.ifPart + lbItem.thenPart);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    public void pushVoteItem(MutableLiveData<User> user, LeaderBoardItem lbi) {
        db.child("rooms").child(user.getValue().gameRoom).child("votingItems").child(lbi.getId()).setValue(lbi);
        System.out.println("Pushed Potential LBI to temp voting in database");
    }

    public void castVote(MutableLiveData<User> user, String vote) {
        db.child("rooms").child(user.getValue().gameRoom).child("votes").child(user.getValue().userName).setValue(vote);
        System.out.println("VOTE SENT TO DB");
    }

    public void createAndListenToCastVotes(String gameroom) {
        db.child("rooms").child(gameroom).child("votes").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                System.out.println(snapshot);
                String vote = snapshot.getValue(String.class);
                System.out.println(vote);
                castvotes.add(vote);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    public ObservableArrayList<LeaderBoardItem> getLeaderBoard() {
        return leaderBoard;
    }

    public ObservableArrayList<LeaderBoardItem> getPotentialLeaderBoardItems() {
        return potentialLeaderBoardItems;
    }

    public ObservableArrayList<String> getCastvotes() {
        return castvotes;
    }

    public void castVoteListener(int numOfUsers) {
        castvotes.addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<String>>() {
            @Override
            public void onChanged(ObservableList<String> sender) {

            }

            @Override
            public void onItemRangeChanged(ObservableList<String> sender, int positionStart, int itemCount) {

            }

            @Override
            public void onItemRangeInserted(ObservableList<String> sender, int positionStart, int itemCount) {
                System.out.println("users: " + numOfUsers + " castVotes: " + getCastvotes().size());
                if (numOfUsers == castvotes.size()) {
                    findBestSentence();
                }
            }

            @Override
            public void onItemRangeMoved(ObservableList<String> sender, int fromPosition, int toPosition, int itemCount) {

            }

            @Override
            public void onItemRangeRemoved(ObservableList<String> sender, int positionStart, int itemCount) {

            }
        });
    }

    public boolean isLeaderBoardFull () {
        if(getLeaderBoard().size() < 20) {
            return false;
        }
        else {
            return true;
        }
    }

    // todo: finish finding best sentence and if it beats what is on the leaderboard push it to the leaderboard
    public void findBestSentence() {
        for (String vote:castvotes) {
            System.out.println(vote);
        };

    }

    public LeaderBoardItem removeWhichItem(LeaderBoardItem newlbi) {

        LeaderBoardItem removeThis = newlbi;
        for (LeaderBoardItem lbi: getLeaderBoard()) {
            if (lbi.getPercentLoved() < newlbi.getPercentLoved()) {
                removeThis = lbi;
                break;
            }
        }
        return removeThis;
    }

}
