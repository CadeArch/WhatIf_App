package com.CadeMixedUpGame.api.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.CadeMixedUpGame.api.models.LeaderBoardItem;
import com.CadeMixedUpGame.api.models.User;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class LeaderBoardViewModel {
    ArrayList<LeaderBoardItem> leaderBoard;
    DatabaseReference db;

    public LeaderBoardViewModel() {
        db = FirebaseDatabase.getInstance().getReference();
        if (leaderBoard == null) {
            leaderBoard = new ArrayList<LeaderBoardItem>();
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

    public ArrayList<LeaderBoardItem> getLeaderBoard() {
        return leaderBoard;
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
