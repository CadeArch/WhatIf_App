package com.CadeMixedUpGame.api.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.models.LeaderBoardItem;
import com.CadeMixedUpGame.api.models.User;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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
        this(FirebaseDatabase.getInstance().getReference(), true);
    }

    public LeaderBoardViewModel(DatabaseReference db) {
        this(db, false);
    }

    public LeaderBoardViewModel(DatabaseReference db, boolean loadLeaderBoardOnCreate) {
        this.db = db;
        if (leaderBoard == null) {
            leaderBoard = new ObservableArrayList<>();
            if (loadLeaderBoardOnCreate) {
                loadLeaderBoardItems();
            }
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
        AppLog.i(AppLog.VOTE, "Reset vote state");
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

                addLeaderBoardItem(snapshot);

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
                AppLog.e(AppLog.FIREBASE, "Leaderboard listener cancelled: " + error.getMessage());
            }
        });
    }

    private void addLeaderBoardItem(@NonNull DataSnapshot snapshot) {
        LeaderBoardItem lbItem = snapshot.getValue(LeaderBoardItem.class);
        if (leaderBoard == null || lbItem == null) {
            AppLog.w(AppLog.VOTE, "Skipping null leaderboard item key=" + snapshot.getKey());
            return;
        }
        leaderBoard.add(lbItem);
        AppLog.d(AppLog.VOTE, "Loaded leaderboard item id=" + lbItem.getId() + ", total=" + leaderBoard.size());
    }

    public void loadVotingItems(MutableLiveData<User> user) {
        if (user == null || user.getValue() == null || user.getValue().gameRoom == null) {
            AppLog.w(AppLog.VOTE, "loadVotingItems skipped: missing user or room");
            return;
        }
        String room = user.getValue().gameRoom;
        AppLog.i(AppLog.FIREBASE, "Attaching voting items listener room=" + room);
        db.child("rooms").child(user.getValue().gameRoom).child("votingItems").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                addVotingItem(snapshot);
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
                AppLog.e(AppLog.FIREBASE, "Voting items listener cancelled: " + error.getMessage());
            }
        });
    }

    private void addVotingItem(@NonNull DataSnapshot snapshot) {
        LeaderBoardItem lbItem = snapshot.getValue(LeaderBoardItem.class);
        if (lbItem == null) {
            AppLog.w(AppLog.VOTE, "Skipping null voting item key=" + snapshot.getKey());
            return;
        }
        potentialLeaderBoardItems.add(lbItem);
        AppLog.d(AppLog.VOTE, "Loaded voting item id=" + lbItem.getId() + ", total=" + potentialLeaderBoardItems.size());
    }

    public void pushVoteItem(MutableLiveData<User> user, LeaderBoardItem lbi) {
        String room = user.getValue().gameRoom;
        AppLog.i(AppLog.FIREBASE, "Pushing voting item room=" + room + ", id=" + lbi.getId());
        db.child("rooms").child(room).child("votingItems").child(lbi.getId()).setValue(lbi)
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Voting item pushed room=" + room + ", id=" + lbi.getId()))
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed pushing voting item room=" + room, e));
    }

    //changed to userName and userID instead of just username in case users have same name todo MAKE SURE IT DIDNT BREAK ANYTHING
    public void castVote(MutableLiveData<User> user, String vote) {
        String room = user.getValue().gameRoom;
        String playerKey = user.getValue().userName + "-" + user.getValue().userID;
        AppLog.i(AppLog.VOTE, "Casting vote room=" + room + ", player=" + playerKey + ", vote=" + vote);
        db.child("rooms").child(room).child("votes").child(playerKey).setValue(vote)
                .addOnSuccessListener(unused -> AppLog.i(AppLog.FIREBASE, "Vote write succeeded room=" + room))
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Vote write failed room=" + room, e));
    }

    public void removeCastVotesListener(String gameroom) {
        if (votesListener != null && gameroom != null) {
            AppLog.i(AppLog.FIREBASE, "Removing votes listener room=" + gameroom);
            db.child("rooms").child(gameroom).child("votes").removeEventListener(votesListener);
            votesListener = null;
        }
    }

    public void createAndListenToCastVotes(String gameroom) {
        AppLog.i(AppLog.FIREBASE, "Attaching votes listener room=" + gameroom);
        votesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String vote = snapshot.getValue(String.class);
                if (vote != null) {
                    castvotes.add(vote);
                    AppLog.d(AppLog.VOTE, "Vote received room=" + gameroom + ", total=" + castvotes.size());
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
                AppLog.e(AppLog.FIREBASE, "Votes listener cancelled: " + error.getMessage());
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
                AppLog.d(AppLog.VOTE, "Votes changed: users=" + numOfUsers + ", castVotes=" + getCastvotes().size());
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
            AppLog.d(AppLog.VOTE, "Leaderboard not full: size=" + getLeaderBoard().size());
            return false;
        }
        else {
            AppLog.d(AppLog.VOTE, "Leaderboard full");
            return true;
        }
    }

    // todo: finish finding best sentence and if it beats what is on the leaderboard push it to the leaderboard
    // TEST THIS FUNCTION
    public void findBestSentence() {
        if (castvotes.size() == 0 || potentialLeaderBoardItems.size() == 0) {
            AppLog.w(AppLog.VOTE, "No votes or voting items available; skipping leaderboard update");
            return;
        }

        selectWinningVotingItem();
        if (plbi == null) {
            AppLog.w(AppLog.VOTE, "No winning voting item found; skipping leaderboard update");
            return;
        }
        double percentLoved = calculatePercentLoved();
        plbi.setPercentLoved(percentLoved);
        AppLog.i(AppLog.VOTE, "Winning vote item id=" + mostVotedID + ", votes=" + mostVotes + ", percentLoved=" + percentLoved);
        if (isLeaderBoardFull()) {
            LeaderBoardItem toRemove = removeWhichItem(plbi);
            if (!toRemove.getId().equals(mostVotedID)) {
                AppLog.i(AppLog.VOTE, "New item beat leaderboard item id=" + toRemove.getId());
                removeLBI(toRemove);
                pushToLeaderBoards(plbi);
            }
            else {
                AppLog.i(AppLog.VOTE, "Winning item did not beat current leaderboard");
            }
        }
        // leaderboard not full
        else {
            pushToLeaderBoards(plbi);
        }

    }

    private void selectWinningVotingItem() {
        mostVotes = 0;
        mostVotedID = "";
        plbi = null;
        for (LeaderBoardItem lbi:potentialLeaderBoardItems) {
            if (lbi == null || lbi.getId() == null) {
                continue;
            }
            int numVotes = Collections.frequency(castvotes, lbi.getId());
            if (mostVotes < numVotes) {
                mostVotes = numVotes;
                mostVotedID = lbi.getId();
                plbi = lbi;
            }
        }
    }

    private double calculatePercentLoved() {
        if (castvotes.size() == 0) {
            return 0;
        }
        return mostVotes / (double) castvotes.size() * 100;
    }

    public void removeLBI(LeaderBoardItem lbi) {
        AppLog.i(AppLog.VOTE, "Removing leaderboard item id=" + lbi.getId() + ", percentLoved=" + lbi.getPercentLoved());
        db.child("leaderBoard").child(Long.toString(lbi.getLoadedToLeaderBoard())).removeValue()
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed removing leaderboard item id=" + lbi.getId(), e));
    }

    public void pushToLeaderBoards(LeaderBoardItem lbi) {
        // Todo fill in all values of lbi
        lbi.setPercentLoved(calculatePercentLoved());
        lbi.setLoadedToLeaderBoard(System.currentTimeMillis());
        AppLog.i(AppLog.VOTE, "Pushing leaderboard item id=" + lbi.getId() + ", percentLoved=" + lbi.getPercentLoved());
        db.child("leaderBoard").child(Long.toString(lbi.getLoadedToLeaderBoard())).setValue(lbi)
                .addOnFailureListener(e -> AppLog.e(AppLog.FIREBASE, "Failed pushing leaderboard item id=" + lbi.getId(), e));
    }

    public LeaderBoardItem removeWhichItem(LeaderBoardItem newlbi) {

        LeaderBoardItem removeThis = newlbi;
        for (LeaderBoardItem lbi: getLeaderBoard()) {
//            System.out.println(lbi.getPercentLoved());
            if (lbi.getPercentLoved() <= newlbi.getPercentLoved()) {
                removeThis = lbi;
                AppLog.d(AppLog.VOTE, "Leaderboard replacement candidate id=" + removeThis.getId() + ", percentLoved=" + removeThis.getPercentLoved());
                break;
            }
        }
        return removeThis;
    }


}
