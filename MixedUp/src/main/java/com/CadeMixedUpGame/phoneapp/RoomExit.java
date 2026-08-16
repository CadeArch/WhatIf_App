package com.CadeMixedUpGame.phoneapp;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

/**
 * Leaving a room on purpose, cleaned up properly.
 *
 * <p>Back navigation is blocked app-wide in {@code MainActivity} so players cannot back out
 * mid-round and leave the room in a broken state. That is the right default for screens where
 * leaving really would corrupt things (a half-collected round), but it was applied to *every*
 * screen - including the lobby, where a player who joins the wrong room has no way out at all.
 * Force-quitting is not an escape either: {@code onDisconnect} only marks a player
 * {@code connected:false}, so they linger in the room as a disconnected participant.
 *
 * <p>A fragment that opts in gets a callback registered against its own view lifecycle. The
 * dispatcher runs the most recently added enabled callback first, so an opted-in screen overrides
 * the Activity's blanket block while it is showing, and the block is back the moment it is gone.
 *
 * <p>Host and guest need different cleanup, which is the whole reason this is shared rather than
 * copy-pasted: a host leaving takes the room with them, a guest leaving must only remove
 * themselves (and {@code removeCurrentPlayerFromRoom} then deletes the room if it emptied out).
 */
final class RoomExit {
    private final Fragment fragment;
    private final UserViewModel userViewModel;
    private final RoomViewModel roomViewModel;
    /** Leaving is a two-step async cleanup; a second back press mid-flight would double-delete. */
    private boolean leaving;

    RoomExit(Fragment fragment, UserViewModel userViewModel, RoomViewModel roomViewModel) {
        this.fragment = fragment;
        this.userViewModel = userViewModel;
        this.roomViewModel = roomViewModel;
    }

    /** Routes the system back button and the predictive back gesture to a clean exit. */
    void wireSystemBack() {
        fragment.requireActivity().getOnBackPressedDispatcher().addCallback(
                fragment.getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        leave("system back");
                    }
                });
    }

    void leave(String reason) {
        if (leaving) {
            return;
        }
        User currentUser = userViewModel.getUser().getValue();
        // A host leaving takes everyone else's game with them, so it gets a confirmation. A guest
        // leaving only affects themselves, so it does not - making them tap twice to escape a room
        // they joined by mistake would be its own small annoyance.
        if (currentUser != null && currentUser.host && currentUser.gameRoom != null
                && currentUser.gameRoom.length() > 0) {
            new androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
                    .setTitle("End the game?")
                    .setMessage("You are the host, so leaving ends this game for everyone in the room.")
                    .setNegativeButton("Stay", null)
                    .setPositiveButton("End game", (dialog, which) -> performLeave(reason))
                    .show();
            return;
        }
        performLeave(reason);
    }

    private void performLeave(String reason) {
        if (leaving) {
            return;
        }
        leaving = true;
        leaveRoom(fragment.requireActivity(), userViewModel, roomViewModel, reason, this::navigateHome);
    }

    /**
     * The one place a player leaves a room, whichever control triggered it.
     *
     * <p>Shared with MainActivity, which offers guests a way out when the host has gone quiet -
     * that lives in the Activity chrome so it works on every screen, and it must clean up exactly
     * the same way a back press does.
     */
    static void leaveRoom(androidx.fragment.app.FragmentActivity activity, UserViewModel userViewModel,
                          RoomViewModel roomViewModel, String reason, Runnable onDone) {
        User currentUser = userViewModel.getUser().getValue();
        String room = currentUser == null ? null : currentUser.gameRoom;
        if (currentUser == null || room == null || room.length() == 0) {
            AppLog.w(AppLog.ROOM, "Leaving with no room to clean up, reason=" + reason);
            onDone.run();
            return;
        }
        if (currentUser.host) {
            // A host leaving ends the game - nobody is left to run it. The tombstone is written
            // before the delete so everyone else is told "the host ended the game" rather than
            // watching the room evaporate.
            AppLog.i(AppLog.ROOM, "Host leaving room=" + room + ", reason=" + reason);
            if (roomViewModel != null) {
                roomViewModel.markRoomExpired(room, "The host ended the game.",
                        () -> userViewModel.deleteRoom(currentUser, onDone));
                return;
            }
            userViewModel.deleteRoom(currentUser, onDone);
            return;
        }
        AppLog.i(AppLog.ROOM, "Guest leaving room=" + room + ", reason=" + reason);
        userViewModel.removeCurrentPlayerFromRoom(onDone);
    }

    /**
     * Tears down room state before going home, then replaces the screen <b>without</b> adding to the
     * back stack.
     *
     * <p>Both halves were missing in the first version and both broke visibly on a real device.
     * Skipping the teardown left the ViewModel still holding the old room and player, so
     * StartFragment came back up in the wrong mode - account-play controls showing in a free-play
     * session. Using addToBackStack meant a second back press walked *into* the room screen that had
     * just been dismantled, whose listeners and round state were gone, and the UI locked up. The
     * other exits (EndFrag, the host-disconnect path) already did it this way; this now matches
     * them.
     */
    private void navigateHome() {
        if (!fragment.isAdded()) {
            return;
        }
        userViewModel.removePlayersListenerOnDB();
        userViewModel.removeListenerOnDB();
        if (roomViewModel != null) {
            roomViewModel.clearLocalRoundState();
        }
        userViewModel.reset();
        userViewModel.clearLocalRoomIdentity();
        Utils.navigateHomeReplacingCurrent(fragment.getActivity());
        AppLog.i(AppLog.GAME_FLOW, "RoomExit -> StartFragment");
    }
}
