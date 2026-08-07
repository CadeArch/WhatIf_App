package com.CadeMixedUpGame.api.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.ChildEventListenerAdapter;
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
    ObservableArrayList<String> votedPlayerKeys = new ObservableArrayList<String>();
    ChildEventListener votesListener;
    ChildEventListener leaderBoardListener;
    ChildEventListener votingItemsListener;
    ObservableList.OnListChangedCallback<ObservableList<String>> castVotesCallback;
    String votesListenerRoom;
    String votingItemsListenerRoom;
    public MutableLiveData<String> databaseMessage = new MutableLiveData<String>();
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
        removeCastVotesCallback();
        potentialLeaderBoardItems = new ObservableArrayList<LeaderBoardItem>();
        castvotes = new ObservableArrayList<String>();
        votedPlayerKeys = new ObservableArrayList<String>();
        mostVotedID = "";
        mostVotes = 0;
        plbi = null;
        AppLog.i(AppLog.VOTE, "Reset vote state");
    }

    public void setLeaderBoard(ObservableArrayList<LeaderBoardItem> leaderBoard) {
        this.leaderBoard = leaderBoard;
    }

    public void loadLeaderBoardItems() {
        if (leaderBoardListener != null || db == null) {
            return;
        }
        leaderBoardListener = new ChildEventListenerAdapter(AppLog.FIREBASE, "Leaderboard listener cancelled") {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                addLeaderBoardItem(snapshot);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                removeLeaderBoardItemLocally(snapshot);
            }
        };
        db.child("leaderBoard").addChildEventListener(leaderBoardListener);
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

    private void removeLeaderBoardItemLocally(@NonNull DataSnapshot snapshot) {
        LeaderBoardItem removed = snapshot.getValue(LeaderBoardItem.class);
        if (leaderBoard == null || removed == null || removed.getId() == null) {
            return;
        }
        leaderBoard.removeIf(item -> removed.getId().equals(item.getId()));
        AppLog.d(AppLog.VOTE, "Removed leaderboard item id=" + removed.getId() + ", total=" + leaderBoard.size());
    }

    public void loadVotingItems(MutableLiveData<User> user) {
        if (user == null || user.getValue() == null || user.getValue().gameRoom == null) {
            AppLog.w(AppLog.VOTE, "loadVotingItems skipped: missing user or room");
            return;
        }
        String room = user.getValue().gameRoom;
        if (room.equals(votingItemsListenerRoom) && votingItemsListener != null) {
            AppLog.d(AppLog.FIREBASE, "Voting items listener already active room=" + room);
            return;
        }
        removeVotingItemsListener();
        AppLog.i(AppLog.FIREBASE, "Attaching voting items listener room=" + room);
        votingItemsListenerRoom = room;
        votingItemsListener = new ChildEventListenerAdapter(AppLog.FIREBASE, "Voting items listener cancelled") {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                addVotingItem(snapshot);
            }
        };
        db.child("rooms").child(room).child("votingItems").addChildEventListener(votingItemsListener);
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
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not prepare voting. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed pushing voting item room=" + room, e);
                });
    }

    //changed to userName and userID instead of just username in case users have same name todo MAKE SURE IT DIDNT BREAK ANYTHING
    public void castVote(MutableLiveData<User> user, String vote) {
        castVote(user, vote, null);
    }

    public void castVote(MutableLiveData<User> user, String vote, Runnable onSuccess) {
        String room = user.getValue().gameRoom;
        String playerKey = user.getValue().userName + "-" + user.getValue().userID;
        AppLog.i(AppLog.VOTE, "Casting vote room=" + room + ", player=" + playerKey + ", vote=" + vote);
        db.child("rooms").child(room).child("votes").child(playerKey).setValue(vote)
                .addOnSuccessListener(unused -> {
                    AppLog.i(AppLog.FIREBASE, "Vote write succeeded room=" + room);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not send your vote. Check your connection and tap submit again.");
                    AppLog.e(AppLog.FIREBASE, "Vote write failed room=" + room, e);
                });
    }

    public void removeCastVotesListener(String gameroom) {
        if (votesListener != null && gameroom != null) {
            AppLog.i(AppLog.FIREBASE, "Removing votes listener room=" + gameroom);
            db.child("rooms").child(gameroom).child("votes").removeEventListener(votesListener);
            votesListener = null;
            votesListenerRoom = null;
        }
    }

    public void createAndListenToCastVotes(String gameroom) {
        if (gameroom == null || gameroom.length() == 0) {
            AppLog.w(AppLog.VOTE, "createAndListenToCastVotes skipped: missing room");
            return;
        }
        if (gameroom.equals(votesListenerRoom) && votesListener != null) {
            AppLog.d(AppLog.FIREBASE, "Votes listener already active room=" + gameroom);
            return;
        }
        if (votesListenerRoom != null) {
            removeCastVotesListener(votesListenerRoom);
        }
        AppLog.i(AppLog.FIREBASE, "Attaching votes listener room=" + gameroom);
        votesListenerRoom = gameroom;
        votesListener = new ChildEventListenerAdapter(AppLog.FIREBASE, "Votes listener cancelled") {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String vote = snapshot.getValue(String.class);
                if (vote != null) {
                    // castvotes MUST be updated before votedPlayerKeys: adding to an
                    // ObservableArrayList notifies its listeners synchronously, and
                    // CollectingVotesFrag watches votedPlayerKeys. Adding the key first fired that
                    // screen's progress check while castvotes was still one behind, so the final
                    // vote left it reading "1 of 2" with no later event to correct it - the screen
                    // hung forever. Keep the observed list last so everything it reads is settled.
                    castvotes.add(vote);
                    // The child key is the voter's playerKey (see castVote) - tracked so
                    // CollectingVotesFrag can show *who* the group is still waiting on, the same way
                    // the If/Then collecting screens do. castvotes stays the list of vote values
                    // because findBestSentence tallies frequencies over it.
                    if (snapshot.getKey() != null && !votedPlayerKeys.contains(snapshot.getKey())) {
                        votedPlayerKeys.add(snapshot.getKey());
                    }
                    AppLog.d(AppLog.VOTE, "Vote received room=" + gameroom + ", total=" + castvotes.size());
                }
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

    /** playerKeys of everyone whose vote has landed, for the collecting-votes waiting screen. */
    public ObservableArrayList<String> getVotedPlayerKeys() {
        return votedPlayerKeys;
    }

    public void castVoteListener(int numOfUsers) {
        removeCastVotesCallback();
        castVotesCallback = new ObservableList.OnListChangedCallback<ObservableList<String>>() {
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
        };
        castvotes.addOnListChangedCallback(castVotesCallback);
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
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not update the leaderboard. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed removing leaderboard item id=" + lbi.getId(), e);
                });
    }

    public void pushToLeaderBoards(LeaderBoardItem lbi) {
        // Todo fill in all values of lbi
        lbi.setPercentLoved(calculatePercentLoved());
        lbi.setLoadedToLeaderBoard(System.currentTimeMillis());
        AppLog.i(AppLog.VOTE, "Pushing leaderboard item id=" + lbi.getId() + ", percentLoved=" + lbi.getPercentLoved());
        db.child("leaderBoard").child(Long.toString(lbi.getLoadedToLeaderBoard())).setValue(lbi)
                .addOnFailureListener(e -> {
                    databaseMessage.setValue("Could not update the leaderboard. Check your connection.");
                    AppLog.e(AppLog.FIREBASE, "Failed pushing leaderboard item id=" + lbi.getId(), e);
                });
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

    public void removeVotingItemsListener() {
        if (votingItemsListener != null && votingItemsListenerRoom != null) {
            AppLog.i(AppLog.FIREBASE, "Removing voting items listener room=" + votingItemsListenerRoom);
            db.child("rooms").child(votingItemsListenerRoom).child("votingItems").removeEventListener(votingItemsListener);
            votingItemsListener = null;
            votingItemsListenerRoom = null;
        }
    }

    private void removeCastVotesCallback() {
        if (castVotesCallback != null) {
            castvotes.removeOnListChangedCallback(castVotesCallback);
            castVotesCallback = null;
        }
    }

    @Override
    protected void onCleared() {
        if (leaderBoardListener != null && db != null) {
            db.child("leaderBoard").removeEventListener(leaderBoardListener);
            leaderBoardListener = null;
        }
        removeVotingItemsListener();
        if (votesListenerRoom != null) {
            removeCastVotesListener(votesListenerRoom);
        }
        removeCastVotesCallback();
        super.onCleared();
    }


}
