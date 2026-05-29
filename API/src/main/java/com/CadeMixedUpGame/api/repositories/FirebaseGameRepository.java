package com.CadeMixedUpGame.api.repositories;

import com.google.android.gms.tasks.Task;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class FirebaseGameRepository implements GameRepository {
    private final DatabaseReference db;

    public FirebaseGameRepository() {
        db = FirebaseDatabase.getInstance().getReference();
    }

    @Override
    public DatabaseReference root() {
        return db;
    }

    @Override
    public DatabaseReference room(String roomId) {
        return db.child("rooms").child(roomId);
    }

    @Override
    public DatabaseReference players(String roomId) {
        return room(roomId).child("players");
    }

    @Override
    public DatabaseReference player(String roomId, String playerKey) {
        return players(roomId).child(playerKey);
    }

    @Override
    public Task<Void> setRoomInProgress(String roomId, boolean inProgress) {
        return room(roomId).child("gameInProgress").setValue(inProgress);
    }

    @Override
    public void listenToPlayers(String roomId, ChildEventListener listener) {
        players(roomId).addChildEventListener(listener);
    }

    @Override
    public void removePlayersListener(String roomId, ChildEventListener listener) {
        players(roomId).removeEventListener(listener);
    }
}
