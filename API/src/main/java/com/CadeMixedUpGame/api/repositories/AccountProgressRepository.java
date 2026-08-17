package com.CadeMixedUpGame.api.repositories;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.MutableLiveData;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.UnlockPolicy;
import com.CadeMixedUpGame.api.models.Unlockable;
import com.CadeMixedUpGame.api.models.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;
import java.util.List;

/**
 * The account progression record: games played, the unlockable voice rows, and the leaderboard
 * flags that both drive unlocks and live on the player.
 *
 * <p>Split out of {@code UserViewModel}, which was carrying auth, presence/onDisconnect, the host
 * heartbeat, the players listener, leave-cleanup *and* this in one ~1100-line class. This cluster
 * is the easiest to lift cleanly: it only ever touches {@code AccountPlayers/<uid>/<name>/...} and
 * shares no state with the rest, whereas the presence and heartbeat code is deliberately left alone
 * for now - that is the hard-won disconnect behaviour CLAUDE.md warns against moving casually.
 *
 * <p>{@code UserViewModel} still exposes the same methods and forwards here, so call sites are
 * untouched and the move was provably behaviour-preserving: see {@code AccountProgressEmulatorTest},
 * written against the old implementation and passing unchanged against this one.
 *
 * <p>The decisions stay in {@link UnlockPolicy}; this only reads and writes.
 */
public class AccountProgressRepository {
    private final DatabaseReference db;
    /** Populated by {@link #getUnlocked} for the reading screen's voice picker. */
    public final ObservableArrayList<Unlockable> userUnlocked;

    public AccountProgressRepository(DatabaseReference db, ObservableArrayList<Unlockable> userUnlocked) {
        this.db = db;
        this.userUnlocked = userUnlocked;
    }

    public void fillUnlockables(MutableLiveData<User> user) {
        User currentUser = user.getValue();
        if (currentUser == null) {
            return;
        }
        DatabaseReference unlockables = unlockablesRef(currentUser);
        for (UnlockPolicy.Voice voice : UnlockPolicy.catalog()) {
            unlockables.child(voice.getVoiceType()).setValue(voice.toLockedUnlockable());
        }
        AppLog.i(AppLog.AUTH, "Seeded " + UnlockPolicy.catalog().size() + " unlockables for new account");
    }

    public void fillGamesPlayed(MutableLiveData<User> user) {
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("gamesPlayed").setValue(0);
    }

    public void getGamesPlayed(MutableLiveData<User> user, boolean increment) {
        Task<DataSnapshot> gamesPlayed = db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("gamesPlayed").get();
        gamesPlayed.addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (!task.isSuccessful()) {
                    AppLog.e(AppLog.FIREBASE, "Failed to load gamesPlayed", task.getException());
                    return;
                }
                DataSnapshot snapshot = gamesPlayed.getResult();
                Integer totalPlayed = snapshot.getValue(Integer.class);
                if (totalPlayed == null || user.getValue() == null) {
                    return;
                }
                user.getValue().gamesPlayed = totalPlayed;
                AppLog.d(AppLog.AUTH, "Loaded gamesPlayed=" + user.getValue().gamesPlayed);
                if (increment) {
                    incrementGamesPlayed(user);
                }
            }
        });
    }

    public void incrementGamesPlayed(MutableLiveData<User> user) {
        user.getValue().gamesPlayed += 1;
        db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("gamesPlayed").setValue(user.getValue().gamesPlayed);
    }

    /**
     * Unlocks every voice the player's current stats have earned.
     *
     * <p>Replaces a chain of per-voice {@code if}s gated on a stringly-typed {@code which}
     * argument ({@code "numGames"}/{@code "leaderBoards"}), which meant a voice was only ever
     * considered at the one call site that passed its matching string - and four of the seven
     * voices had no branch at all, so they could never be earned. Asking UnlockPolicy for the whole
     * earned set instead means every call re-asserts everything earned so far: unlocking is
     * idempotent, so an account that missed an unlock (offline when it was earned, or earned before
     * that rule existed) repairs itself on the next call rather than staying short forever.
     */
    public void unlockEarnedVoices(MutableLiveData<User> user) {
        User currentUser = user.getValue();
        if (currentUser == null) {
            return;
        }
        DatabaseReference unlockables = unlockablesRef(currentUser);
        for (UnlockPolicy.Voice voice : UnlockPolicy.earnedVoices(
                currentUser.gamesPlayed, currentUser.madeLeaderBoard, currentUser.perfectLeaderBoard)) {
            unlockables.child(voice.getVoiceType()).child("unlocked").setValue(true);
            AppLog.i(AppLog.AUTH, "Unlocked " + voice.getVoiceType() + " voice");
        }
        // Kept from the previous leaderboard branches: these flags live on the player record as
        // well as driving unlocks, and were being persisted here rather than where they are set.
        if (currentUser.madeLeaderBoard) {
            accountPlayerRef(currentUser).child("madeLeaderBoard").setValue(true);
        }
        if (currentUser.perfectLeaderBoard) {
            accountPlayerRef(currentUser).child("perfectLeaderBoard").setValue(true);
        }
    }

    private DatabaseReference accountPlayerRef(User user) {
        return db.child("AccountPlayers").child(user.uid).child(user.userName);
    }

    private DatabaseReference unlockablesRef(User user) {
        return accountPlayerRef(user).child("unlockables");
    }

    public void getUnlocked(MutableLiveData<User> user) {
        Task<DataSnapshot> unlocked = db.child("AccountPlayers").child(user.getValue().uid).child(user.getValue().userName).child("unlockables").get();
        unlocked.addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DataSnapshot> task) {
                if (!task.isSuccessful()) {
                    AppLog.e(AppLog.FIREBASE, "Failed to load unlockables", task.getException());
                    return;
                }
                DataSnapshot snapshot = unlocked.getResult();
                // Collected first, then published in one go. Adding straight into the observable
                // list one row at a time fires a separate change event per voice, and every
                // observer does its full rebuild on each - the voice picker was rebuilding and
                // re-notifying its adapter eight times in five milliseconds for a single load
                // ("Voice picker rebuilt with 4/5/6/7/8 option(s)" back to back in the log).
                // clear() + addAll() is two events regardless of how many voices exist.
                List<Unlockable> loaded = new ArrayList<Unlockable>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Unlockable unlockable = ds.getValue(Unlockable.class);
                    if (unlockable != null) {
                        loaded.add(unlockable);
                    }
                }
                userUnlocked.clear();
                userUnlocked.addAll(loaded);
            }
        });
    }
}
