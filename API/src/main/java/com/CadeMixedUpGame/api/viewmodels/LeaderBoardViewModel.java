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

import java.sql.Time;
import java.util.ArrayList;
import java.util.Collections;

public class LeaderBoardViewModel extends ViewModel {
    ObservableArrayList<LeaderBoardItem> leaderBoard;
    DatabaseReference db;
    ObservableArrayList<LeaderBoardItem> potentialLeaderBoardItems;
    ObservableArrayList<String> castvotes = new ObservableArrayList<String>();
    ChildEventListener votesListener;
    int mostVotes = 0;
    String mostVotedID = "";
    LeaderBoardItem plbi;

    public LeaderBoardViewModel() {
        db = FirebaseDatabase.getInstance().getReference();
        if (leaderBoard == null) {
            leaderBoard = new ObservableArrayList<>();
            loadLeaderBoardItems();
        }
        if (potentialLeaderBoardItems == null) {
            potentialLeaderBoardItems = new ObservableArrayList<>();
        }
    }

    public void reset() {
        potentialLeaderBoardItems = new ObservableArrayList<LeaderBoardItem>();
        castvotes = new ObservableArrayList<String>();
        mostVotedID = "";
        mostVotes = 0;
        plbi = null;
        System.out.println("SIZE of cast votes after reset: " + castvotes.size());
    }

    public void setLeaderBoard(ObservableArrayList<LeaderBoardItem> leaderBoard) {
        this.leaderBoard = leaderBoard;
    }

    public void loadLeaderBoardItems() {
        db.child("leaderBoard").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                System.out.println(snapshot);
//                System.out.println(snapshot.getValue());

                LeaderBoardItem lbItem = snapshot.getValue(LeaderBoardItem.class);
                // todo make sure this isnt breaking anything, why would leaderboard be null?
                if (leaderBoard != null || lbItem != null) {
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

//                System.out.println(snapshot);
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

    //changed to userName and userID instead of just username in case users have same name todo MAKE SURE IT DIDNT BREAK ANYTHING
    public void castVote(MutableLiveData<User> user, String vote) {
        db.child("rooms").child(user.getValue().gameRoom).child("votes").child(user.getValue().userName + "-" + user.getValue().userID).setValue(vote);
        System.out.println("VOTE SENT TO DB");
    }

    public void removeCastVotesListener(String gameroom) {
        db.child("rooms").child(gameroom).child("votes").removeEventListener(votesListener);
    }

    public void createAndListenToCastVotes(String gameroom) {
        votesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                System.out.println(snapshot);
                String vote = snapshot.getValue(String.class);
                System.out.println("Adding a cast vote: " + vote);
                castvotes.add(vote);
                System.out.println("ADDING VOTE TO CAST VOTES: castvotes size after adding: " + castvotes.size());
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
        };

        db.child("rooms").child(gameroom).child("votes").addChildEventListener(votesListener);
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
            System.out.println("LEADERBOARD NOT FULL");
            return false;
        }
        else {
            System.out.println("LEADERBOARD FULL");
            return true;
        }
    }

    // todo: finish finding best sentence and if it beats what is on the leaderboard push it to the leaderboard
    // TEST THIS FUNCTION
    public void findBestSentence() {

        for (LeaderBoardItem lbi:potentialLeaderBoardItems) {
            int numVotes = Collections.frequency(castvotes, lbi.getId());

            if (mostVotes < numVotes) {
                mostVotes = numVotes;
                mostVotedID = lbi.getId();
                plbi = lbi;
            }
        }
        System.out.println("num votes " + mostVotes + " voteItem: " + mostVotedID + " percentLoved: " + mostVotes/(double)castvotes.size() * 100);
        plbi.setPercentLoved(mostVotes/(double)castvotes.size() * 100);
        System.out.println("Items on leaderboard: " + leaderBoard.size());
        if (isLeaderBoardFull()) {
            LeaderBoardItem toRemove = removeWhichItem(plbi);
            if (!toRemove.getId().equals(mostVotedID)) {
                System.out.println("NEW LBI beat one currently on the leaderboards");
                removeLBI(toRemove);
                pushToLeaderBoards(plbi);
            }
            else {
                System.out.println("NEW LBI did not beat what is currently on leaderboard");
            }
        }
        // leaderboard not full
        else {
            pushToLeaderBoards(plbi);
        }

    }

    public void removeLBI(LeaderBoardItem lbi) {
        System.out.println("removing: " + lbi.getId() + lbi.getPercentLoved());
        db.child("leaderBoard").child(Long.toString(lbi.getLoadedToLeaderBoard())).removeValue();
    }

    public void pushToLeaderBoards(LeaderBoardItem lbi) {
        // Todo fill in all values of lbi
        System.out.println(mostVotes/(double)castvotes.size() * 100);
        lbi.setPercentLoved(mostVotes/(double)castvotes.size() * 100);
        lbi.setLoadedToLeaderBoard(System.currentTimeMillis());
        db.child("leaderBoard").child(Long.toString(lbi.getLoadedToLeaderBoard())).setValue(lbi);
    }

    public LeaderBoardItem removeWhichItem(LeaderBoardItem newlbi) {

        LeaderBoardItem removeThis = newlbi;
        for (LeaderBoardItem lbi: getLeaderBoard()) {
            System.out.println(lbi.getLoadedToLeaderBoard());
        }
        for (LeaderBoardItem lbi: getLeaderBoard()) {
//            System.out.println(lbi.getPercentLoved());
            if (lbi.getPercentLoved() < newlbi.getPercentLoved()) {
                removeThis = lbi;
                System.out.println("to remove: " + removeThis.getId() + removeThis.getPercentLoved());
                break;
            }
        }
        return removeThis;
    }


}
