package com.CadeMixedUpGame.api.repositories;

import com.google.android.gms.tasks.Task;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseReference;

public interface GameRepository {
    DatabaseReference root();

    DatabaseReference room(String roomId);

    DatabaseReference players(String roomId);

    DatabaseReference player(String roomId, String playerKey);

    Task<Void> setRoomInProgress(String roomId, boolean inProgress);

    void listenToPlayers(String roomId, ChildEventListener listener);

    void removePlayersListener(String roomId, ChildEventListener listener);
}
