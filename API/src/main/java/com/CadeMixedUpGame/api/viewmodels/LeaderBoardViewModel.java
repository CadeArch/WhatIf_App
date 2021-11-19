package com.CadeMixedUpGame.api.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableArrayList;
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

    public void loadTempLeaderItems(MutableLiveData<User> user) {
        db.child("rooms").child(user.getValue().gameRoom).child("tempVoting").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                String child = snapshot.getKey();
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

    public void pushLeaderBoardItem(MutableLiveData<User> user, LeaderBoardItem lbi) {
        db.child("rooms").child(user.getValue().gameRoom).child("tempVoting").child(lbi.getId()).setValue(lbi);
        System.out.println("Pushed Potential LBI to temp voting in database");
    }

    public ObservableArrayList<LeaderBoardItem> getLeaderBoard() {
        return leaderBoard;
    }

    public ObservableArrayList<LeaderBoardItem> getPotentialLeaderBoardItems() {
        return potentialLeaderBoardItems;
    }

    public boolean isLeaderBoardFull () {
        if(getLeaderBoard().size() < 20) {
            return false;
        }
        else {
            return true;
        }
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
