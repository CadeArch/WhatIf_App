package com.CadeMixedUpGame.api;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * The app's single {@link DatabaseReference} root.
 *
 * <p>Every call to {@code FirebaseDatabase.getInstance().getReference()} hands back a <em>different</em>
 * root object, and they do not behave identically: only the first reliably delivers callbacks. A
 * write made through a later one still reaches the database, but its completion listener may never
 * fire - and an unresolved local write <b>masks server updates at that path</b>, so the client keeps
 * reading its own stale value while everyone else has moved on.
 *
 * <p>That is not theoretical here. It was proven on-device when the Tier B room-code handoff hung:
 * the value was sitting in the database (confirmed by REST) while the writing client's callback
 * never fired and the reading client never saw it. The app then had <b>three</b> separate roots -
 * one in {@code FirebaseGameRepository} (so, RoomViewModel) and two in {@code UserViewModel} - which
 * is how two ViewModels in the same process ended up disagreeing about the state of the same room:
 * a host stuck on {@code activeReaderIndex=1} while the guest had already moved to 2, each waiting
 * for the other, with the round deadlocked between them.
 *
 * <p>Use this instead of calling {@code getReference()} directly. Tests that want their own
 * connection pass an explicit {@code DatabaseReference} into the ViewModel constructors and do not
 * go through here.
 */
public final class FirebaseRoot {
    private static DatabaseReference root;

    private FirebaseRoot() {
    }

    public static synchronized DatabaseReference get() {
        if (root == null) {
            root = FirebaseDatabase.getInstance().getReference();
            AppLog.i(AppLog.FIREBASE, "Created the app's single database root");
        }
        return root;
    }

    /** For tests that swap the underlying FirebaseApp between cases. */
    public static synchronized void reset() {
        root = null;
    }
}
