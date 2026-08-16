package com.CadeMixedUpGame.phoneapp;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameLogic;
import androidx.databinding.ObservableList;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.snackbar.Snackbar;
import com.CadeMixedUpGame.api.GameFlowPolicy;
import com.CadeMixedUpGame.api.HostDisconnectScheduler;
import com.CadeMixedUpGame.api.RepeatingPulse;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;
import com.CadeMixedUpGame.api.models.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;


public class MainActivity extends AppCompatActivity {
    private RoomViewModel roomViewModel;
    private UserViewModel userViewModel;
    private View connectionBanner;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean firebaseConnected = true;
    private boolean deviceNetworkAvailable = true;
    private boolean handlingHostDisconnect = false;
    private final Handler connectionHandler = new Handler(Looper.getMainLooper());
    private final HostDisconnectScheduler.DelayedRunner pulseRunner = new HostDisconnectScheduler.DelayedRunner() {
        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            connectionHandler.postDelayed(runnable, delayMs);
        }

        @Override
        public void cancel(Runnable runnable) {
            connectionHandler.removeCallbacks(runnable);
        }
    };
    private final HostDisconnectScheduler hostDisconnectScheduler = new HostDisconnectScheduler(
            new HostDisconnectScheduler.DelayedRunner() {
                @Override
                public void postDelayed(Runnable runnable, long delayMs) {
                    connectionHandler.postDelayed(runnable, delayMs);
                }

                @Override
                public void cancel(Runnable runnable) {
                    connectionHandler.removeCallbacks(runnable);
                }
            },
            reason -> markHostAway(reason));
    // The heartbeat and presence tickers are the same self-rescheduling shape, so they share
    // RepeatingPulse. (A third, hand-rolled countdown used to live here: the host's own "give up on
    // my room" timer. It is gone - a host no longer abandons its own room just because its phone
    // was locked, which is the whole point of the wait-for-them-to-return model.)
    private RepeatingPulse hostHeartbeatPulse;
    private RepeatingPulse presencePulse;
    private boolean resumedFromBackground = false;
    private boolean hostAway = false;
    private Snackbar hostAwayBar;
    private Snackbar kickBar;
    private String kickBarPlayerName;

    // Firebase's .info/connected briefly reports false on every fresh launch, before the initial
    // WebSocket handshake completes - not a real disconnect. Debounce showing the banner so that
    // ordinary startup (and other sub-second blips) never flashes it; a genuine disconnect still
    // shows it, just ~1s later than before.
    private static final long CONNECTION_BANNER_SHOW_DELAY_MS = 1200L;
    private final Runnable showConnectionBannerRunnable = () -> {
        if (connectionBanner != null) {
            connectionBanner.setVisibility(View.VISIBLE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Edge-to-edge is mandatory (can't opt out) once targeting API 36, so real content must
        // consume system-bar insets itself instead of relying on the system to reserve space.
        // Padding is applied to each *fragment's own root view* (not fragment_container) so that
        // view's own paper-texture background still paints full-bleed under the status/nav bars
        // (a View's background is never clipped by its own padding, only its children are) --
        // padding fragment_container itself left a visible seam where its neighboring sibling
        // decorative background (a different paper texture) showed through the inset strip.
        View connectionBannerView = findViewById(R.id.connection_banner);
        int bannerPaddingTop = connectionBannerView.getPaddingTop();
        androidx.core.graphics.Insets[] latestSystemBarInsets = {androidx.core.graphics.Insets.NONE};
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(@NonNull androidx.fragment.app.FragmentManager fm,
                                                       @NonNull Fragment f, @NonNull View v, Bundle savedInstanceState) {
                        androidx.core.graphics.Insets insets = latestSystemBarInsets[0];
                        v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                        // Screen-transition breadcrumb for the auto error-log table (README
                        // roadmap) - covers every screen change regardless of how it's triggered,
                        // not just calls through Utils.navigateToFragment.
                        AppLog.i(AppLog.UI, "Screen shown: " + f.getClass().getSimpleName());
                    }
                }, false);
        View rootView = findViewById(R.id.main_root);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            // WindowInsetsCompat reports physical left/right (not start/end), so pad with setPadding.
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            latestSystemBarInsets[0] = systemBars;
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (current != null && current.getView() != null) {
                current.getView().setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            }
            connectionBannerView.setPadding(
                    connectionBannerView.getPaddingLeft(),
                    bannerPaddingTop + systemBars.top,
                    connectionBannerView.getPaddingRight(),
                    connectionBannerView.getPaddingBottom());
            return insets;
        });

        // Back means "leave the room", from wherever you are, with a confirmation.
        //
        // This used to be an unconditional no-op on every screen, because backing out mid-round left
        // the round in a state nobody could finish. That is no longer true: leaving is now the same
        // operation as being removed, so the round is rebuilt (before reading) or the host covers the
        // empty slot (during it). With that in place, blocking back only trapped people - a player
        // who joined the wrong room had no way out, and force-quitting did not release them either,
        // since a dropped player is assumed to be coming back.
        //
        // Screens with no room state opt out and simply navigate (Utils.wireSystemBackTo); the lobby
        // opts out to leave without a prompt, because nothing has started there yet.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmLeaveFromBack();
            }
        });

        // assure nightmode wont work in app
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setupConnectionBanner();
        setupHostDisconnectNavigation();
        setupRoomMembershipChrome();
        startPresencePulse();
        cleanupOldExpiredRooms();

        // showing users token to test messages from firebase
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            AppLog.e(AppLog.PUSH, "Fetching FCM registration token failed", task.getException());
                            return;
                        }

                        // Get new FCM registration token
                        String token = task.getResult();
//                        System.out.println("TOKEN---------------" + token);

                    }


                });

        // subscribing to my message compaign to send messages every 2 weeks on a friday
        FirebaseMessaging.getInstance().subscribeToTopic("MixedUp")
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        String msg = "subscribed";
                        if (!task.isSuccessful()) {
                            msg = "subscription failed";
                            AppLog.e(AppLog.PUSH, "FCM topic subscription failed", task.getException());
                        }
//                        System.out.println(msg);
                        AppLog.i(AppLog.PUSH, "FCM topic status=" + msg);
//                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });

        //MyFirebaseMessagingService msg = new MyFirebaseMessagingService();
        //todo whenever they launch the app store get time value into shared preferences which will be used by the notification handler


        // Storing data into SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("MixedUpSharedPrefs",MODE_PRIVATE);

        // Creating an Editor object to edit(write to the file)
        SharedPreferences.Editor myEdit = sharedPreferences.edit();
        // Storing the key and its value as the data fetched from edittext
        myEdit.putLong("logged-on", System.currentTimeMillis());
        myEdit.commit();


        // which fragment to display first
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, FirstFrag.class, null)
                    .setReorderingAllowed(true)
                    .commit();
        }
    }

    private void setupConnectionBanner() {
        roomViewModel = new ViewModelProvider(this).get(RoomViewModel.class);
        connectionBanner = findViewById(R.id.connection_banner);
        setupDeviceNetworkMonitor();
        roomViewModel.listenToConnectionState();
        roomViewModel.firebaseConnected.observe(this, connected -> {
            firebaseConnected = connected == null || connected;
            updateConnectionBanner();
        });
        roomViewModel.expiredRoomMessage.observe(this, message -> {
            if (message == null || message.length() == 0 || handlingHostDisconnect) {
                return;
            }
            roomViewModel.expiredRoomMessage.setValue("");
            handleRoomEnded(message);
        });
    }

    private void setupHostDisconnectNavigation() {
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        userViewModel.hostDisconnectedMessage.observe(this, message -> {
            if (message == null || message.length() == 0 || handlingHostDisconnect) {
                return;
            }
            userViewModel.clearHostDisconnectedMessage();
            // The host's *connection* dropped. That pauses the room; it does not end it. These two
            // signals are easy to conflate and must not be: routing this at the room-ended handler
            // sent everyone to the landing screen on the ordinary connection blip that happens when
            // a finished room is torn down, so a normal end of round looked like a crash.
            markHostAway(message);
        });
        userViewModel.hostDisconnectedAt.observe(this, disconnectedAt -> {
            if (disconnectedAt == null || disconnectedAt <= 0L) {
                handleHostConnectionRecovered(false);
                return;
            }
            scheduleHostDisconnect(disconnectedAt);
        });
        userViewModel.hostLastSeenAt.observe(this, lastSeenAt -> {
            if (lastSeenAt == null || lastSeenAt <= 0L) {
                return;
            }
            scheduleHostHeartbeatExpiration(lastSeenAt);
        });
    }

    private void sendPlayerHomeAfterRoomEnded(String message) {
        if (handlingHostDisconnect) {
            return;
        }
        handlingHostDisconnect = true;
        User currentUser = userViewModel.getUser().getValue();
        String room = currentUser == null ? "" : currentUser.gameRoom;
        AppLog.w(AppLog.ROOM, "Room ended; returning client home room=" + room
                + ", from=" + Utils.currentFragmentName(this));

        userViewModel.removePlayersListenerOnDB();
        roomViewModel.clearLocalRoundState();
        if (room != null && room.length() > 0 && currentUser != null && currentUser.host) {
            roomViewModel.deleteExpiredRoomMarker(room);
        }
        userViewModel.reset();
        userViewModel.clearLocalRoomIdentity();
        stopHostHeartbeat();
        hostDisconnectScheduler.reset();
        hostAway = false;
        userViewModel.hostAway.setValue(false);
        Utils.navigateLandingReplacingCurrent(this);
        UiMessenger.showSnackbar(findViewById(R.id.fragment_container), message);
        roomViewModel.expiredRoomMessage.setValue("");
        handlingHostDisconnect = false;
    }

    /**
     * An {@code expiredRooms} tombstone appeared, so this room is genuinely over and its data is
     * going away - send the player home.
     *
     * <p>Only two things write that marker now: a host deliberately ending the game, and the
     * maintenance sweep reclaiming a room nobody came back to. A host merely being *disconnected*
     * no longer writes one and no longer ends anything - that path calls markHostAway() and holds.
     */
    private void handleRoomEnded(String message) {
        sendPlayerHomeAfterRoomEnded(message);
    }

    private void scheduleHostDisconnect(long disconnectedAt) {
        long now = System.currentTimeMillis();
        long elapsedMs = Math.max(0L, now - disconnectedAt);
        long remainingMs = Math.max(0L, GameFlowPolicy.CONNECTION_GRACE_MS - elapsedMs);
        AppLog.w(AppLog.ROOM, "Host disconnected grace timer started remainingMs=" + remainingMs);
        hostDisconnectScheduler.scheduleForDisconnectTimestamp(disconnectedAt, now);
    }

    private void scheduleHostHeartbeatExpiration(long lastSeenAt) {
        User currentUser = userViewModel == null ? null : userViewModel.getUser().getValue();
        if (currentUser == null || currentUser.host) {
            return;
        }
        long now = System.currentTimeMillis();
        AppLog.w(AppLog.ROOM, "Host heartbeat deadline scheduled remainingMs="
                + GameFlowPolicy.millisUntilHostHeartbeatExpires(now, lastSeenAt));
        hostDisconnectScheduler.scheduleForHeartbeat(lastSeenAt, now);
    }

    private void handleHostConnectionRecovered(boolean cancelHeartbeatTimer) {
        // "connected" is not evidence the host is actually there. A frozen app keeps its socket up
        // while its heartbeat stops - measured at over two minutes from nothing worse than a locked
        // phone - so the room reports connected=true with a lastSeenAt going stale. Treating that
        // as recovery cancelled the very heartbeat deadline meant to catch it, and the host could
        // then never be shown as away at all. A fresh heartbeat is the only thing that counts.
        Long lastSeenAt = userViewModel.hostLastSeenAt.getValue();
        boolean heartbeatFresh = lastSeenAt != null && lastSeenAt > 0L
                && !GameFlowPolicy.hostHeartbeatExpired(System.currentTimeMillis(), lastSeenAt);
        if (!heartbeatFresh) {
            return;
        }
        handleHostBack();
        hostDisconnectScheduler.reset();
        if (cancelHeartbeatTimer) {
            hostDisconnectScheduler.cancel();
        }
    }

    /**
     * The host has gone quiet for longer than the grace window.
     *
     * <p>This used to delete the room and send everyone home. It no longer does either, because
     * measurement showed the trigger is routine: a host's heartbeat freezes for over two minutes
     * from nothing worse than a locked phone, while the socket stays up and onDisconnect never
     * fires. Deleting on that signal ended real games whose host was about to walk back in - the
     * reported "came back and the app was broken".
     *
     * <p>Now the room is simply marked as waiting. Players hold where they are and can leave under
     * their own steam. A room whose host genuinely never returns is cleaned up by the maintenance
     * sweep after ABANDONED_ROOM_TTL_MS - the only automatic deletion left in the app.
     */
    /**
     * Room-level controls that must be reachable from every screen: a way out for guests when the
     * host has gone quiet, and the host's control for removing someone who is not coming back.
     *
     * <p>Lives in the Activity rather than in each fragment on purpose. There are sixteen game
     * screens; adding a bar to each is sixteen ConstraintLayouts to get right and sixteen chances
     * to break a view something else is anchored to. Snackbars sit above whatever screen is showing
     * and need no layout changes at all.
     */
    /**
     * Back from an in-round screen: confirm, then leave cleanly.
     *
     * <p>Both roles are asked, for different reasons. A host leaving ends the game for everyone, so
     * the prompt says so. A guest leaving costs them the round they are in the middle of - not the
     * end of the world, but not something to do by brushing a gesture area either.
     */
    private void confirmLeaveFromBack() {
        User currentUser = userViewModel == null ? null : userViewModel.getUser().getValue();
        if (currentUser == null || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0) {
            // Not in a room: nothing to leave, and nothing worth backing out of either.
            return;
        }
        boolean host = currentUser.host;
        new AlertDialog.Builder(this)
                .setTitle(host ? "End the game?" : "Leave the game?")
                .setMessage(host
                        ? "You are the host, so leaving ends this game for everyone in the room."
                        : "You will be dropped from this round. The others will keep playing.")
                .setNegativeButton("Stay", null)
                .setPositiveButton(host ? "End game" : "Leave", (dialog, which) -> leaveRoomFromChrome())
                .show();
    }

    private void setupRoomMembershipChrome() {
        userViewModel.hostAway.observe(this, away -> updateHostAwayBar(Boolean.TRUE.equals(away)));
        // Being removed is the one room event a player cannot act on themselves - send them home
        // rather than leaving them looking at a game that is no longer counting them.
        userViewModel.removedFromRoomMessage.observe(this, message -> {
            if (message == null || message.length() == 0) {
                return;
            }
            userViewModel.removedFromRoomMessage.setValue("");
            AppLog.w(AppLog.ROOM, "Removed from room; returning home");
            dismissKickBar();
            sendPlayerHomeAfterRoomEnded(message);
        });
        userViewModel.getUsers().addOnListChangedCallback(
                new ObservableList.OnListChangedCallback<ObservableList<User>>() {
                    @Override
                    public void onChanged(ObservableList<User> sender) {
                        updateKickBar();
                    }

                    @Override
                    public void onItemRangeChanged(ObservableList<User> sender, int start, int count) {
                        updateKickBar();
                    }

                    @Override
                    public void onItemRangeInserted(ObservableList<User> sender, int start, int count) {
                        updateKickBar();
                    }

                    @Override
                    public void onItemRangeMoved(ObservableList<User> sender, int from, int to, int count) {
                        updateKickBar();
                    }

                    @Override
                    public void onItemRangeRemoved(ObservableList<User> sender, int start, int count) {
                        updateKickBar();
                    }
                });
    }

    private void updateHostAwayBar(boolean away) {
        User currentUser = userViewModel == null ? null : userViewModel.getUser().getValue();
        boolean showToGuest = away && currentUser != null && !currentUser.host
                && currentUser.gameRoom != null && currentUser.gameRoom.length() > 0;
        if (!showToGuest) {
            if (hostAwayBar != null) {
                hostAwayBar.dismiss();
                hostAwayBar = null;
            }
            return;
        }
        if (hostAwayBar != null && hostAwayBar.isShownOrQueued()) {
            return;
        }
        // Without this a guest is simply stuck: the round now waits for the host indefinitely by
        // design, so if the host never comes back there has to be a door.
        hostAwayBar = UiMessenger.showPersistentAction(findViewById(R.id.fragment_container),
                "Host is away. The game is paused.", "Leave game", this::leaveRoomFromChrome);
    }

    private void leaveRoomFromChrome() {
        RoomExit.leaveRoom(this, userViewModel, roomViewModel, "host away - guest left", () -> {
            userViewModel.removePlayersListenerOnDB();
            roomViewModel.clearLocalRoundState();
            userViewModel.reset();
            userViewModel.clearLocalRoomIdentity();
            hostAway = false;
            userViewModel.hostAway.setValue(false);
            Utils.navigateHomeReplacingCurrent(this);
        });
    }

    /** Offers the host a way to remove someone who has been gone past the kick threshold. */
    private void updateKickBar() {
        User currentUser = userViewModel == null ? null : userViewModel.getUser().getValue();
        if (currentUser == null || !currentUser.host) {
            dismissKickBar();
            return;
        }
        User kickable = firstKickablePlayer();
        if (kickable == null) {
            dismissKickBar();
            return;
        }
        // isShownOrQueued matters: snackbars are a queue, so anything else the app shows ("Your turn
        // to read", a database message) displaces this one permanently. Without this check the bar
        // is remembered as still up and never comes back - which is exactly how it vanished by the
        // time the round reached reading, the phase where the host most needs it.
        if (kickBar != null && kickBar.isShownOrQueued()
                && kickable.userName != null && kickable.userName.equals(kickBarPlayerName)) {
            return;
        }
        dismissKickBar();
        kickBarPlayerName = kickable.userName;
        kickBar = UiMessenger.showPersistentAction(findViewById(R.id.fragment_container),
                kickable.userName + " has been away a while.", "Remove",
                () -> confirmRemovePlayer(kickable));
    }

    private User firstKickablePlayer() {
        long now = System.currentTimeMillis();
        for (User player : userViewModel.getUsers()) {
            if (GameFlowPolicy.canKickPlayer(player, now)) {
                return player;
            }
        }
        return null;
    }

    private void dismissKickBar() {
        if (kickBar != null) {
            kickBar.dismiss();
            kickBar = null;
        }
        kickBarPlayerName = null;
    }

    private void confirmRemovePlayer(User player) {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null || currentUser.gameRoom == null || player == null) {
            return;
        }
        // Re-checked here, not just when the bar was drawn: they may have reconnected in between,
        // and removing someone who has just walked back in would be the wrong outcome.
        if (!GameFlowPolicy.canKickPlayer(player, System.currentTimeMillis())) {
            UiMessenger.showSnackbar(findViewById(R.id.fragment_container),
                    player.userName + " is back - leaving them in the game.");
            dismissKickBar();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Remove " + player.userName + "?")
                .setMessage("They will be dropped from this round. Anything they already wrote is "
                        + "discarded and the round is rebuilt for the remaining players.")
                .setNegativeButton("Keep waiting", null)
                .setPositiveButton("Remove", (dialog, which) -> removePlayer(currentUser, player))
                .show();
    }

    private void removePlayer(User currentUser, User player) {
        String room = currentUser.gameRoom;
        String playerKey = GameLogic.playerKey(player);
        dismissKickBar();
        roomViewModel.removePlayerFromRound(room, playerKey, userViewModel.getUsers(),
                userViewModel.gamePhase.getValue(),
                () -> UiMessenger.showSnackbar(findViewById(R.id.fragment_container),
                        player.userName + " was removed."),
                () -> endRoundWithTooFewPlayers(room));
    }

    /** A round cannot run with one player. End it rather than leaving someone alone in a dead game. */
    private void endRoundWithTooFewPlayers(String room) {
        AppLog.w(AppLog.ROOM, "Ending room - not enough players left room=" + room);
        roomViewModel.markRoomExpired(room, "Not enough players left to keep going.",
                () -> handleRoomEnded("Not enough players left to keep going."));
    }

    private void markHostAway(String reason) {
        if (hostAway) {
            return;
        }
        hostAway = true;
        AppLog.w(AppLog.ROOM, "Host is away (holding the room, not ending it): " + reason);
        userViewModel.hostAway.setValue(true);
    }

    private void handleHostBack() {
        if (!hostAway) {
            return;
        }
        hostAway = false;
        AppLog.i(AppLog.ROOM, "Host is back");
        userViewModel.hostAway.setValue(false);
    }

    private void setupDeviceNetworkMonitor() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return;
        }
        deviceNetworkAvailable = hasUsableNetwork();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    deviceNetworkAvailable = true;
                    firebaseConnected = true;
                    roomViewModel.firebaseConnected.setValue(true);
                    updateConnectionBanner();
                    markCurrentPlayerConnectedIfNeeded();
                    AppLog.i(AppLog.FIREBASE, "Device network available; hiding connection banner");
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> {
                    deviceNetworkAvailable = hasUsableNetwork();
                    updateConnectionBanner();
                    AppLog.w(AppLog.FIREBASE, "Device network lost; connected=" + deviceNetworkAvailable);
                });
            }
        };
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
        updateConnectionBanner();
    }

    private void cleanupOldExpiredRooms() {
        // Claims a shared once-a-day slot rather than sweeping on every launch of every install -
        // see RoomViewModel.runDailyMaintenanceIfDue. Covers both jobs: dropping rooms nobody is
        // coming back to, and dropping the stale "this room died" flags.
        roomViewModel.runDailyMaintenanceIfDue(System.currentTimeMillis());
    }

    private boolean hasUsableNetwork() {
        if (connectivityManager == null || connectivityManager.getActiveNetwork() == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void updateConnectionBanner() {
        if (connectionBanner == null) {
            return;
        }
        boolean connected = firebaseConnected && deviceNetworkAvailable;
        connectionHandler.removeCallbacks(showConnectionBannerRunnable);
        if (connected) {
            connectionBanner.setVisibility(View.GONE);
            markCurrentPlayerConnectedIfNeeded();
        }
        else {
            stopHostHeartbeat();
            updateDisconnectedBannerText();
            // Debounced, not immediate - see CONNECTION_BANNER_SHOW_DELAY_MS. Cancelled above if
            // connectivity recovers before it fires, so a brief blip never shows anything.
            connectionHandler.postDelayed(showConnectionBannerRunnable, CONNECTION_BANNER_SHOW_DELAY_MS);
            AppLog.w(AppLog.FIREBASE, "Connection lost; banner will show in " + CONNECTION_BANNER_SHOW_DELAY_MS + "ms if it doesn't recover");
        }
    }

    private void markCurrentPlayerConnectedIfNeeded() {
        if (userViewModel != null && firebaseConnected && deviceNetworkAvailable) {
            User currentUser = userViewModel.getUser().getValue();
            if (currentUser != null && currentUser.gameRoom != null && currentUser.gameRoom.length() > 0) {
                roomViewModel.listenToExpiredRoom(currentUser.gameRoom);
            }
            userViewModel.markCurrentPlayerConnected();
            startHostHeartbeatIfNeeded();
        }
    }

    private void updateDisconnectedBannerText() {
        User currentUser = userViewModel == null ? null : userViewModel.getUser().getValue();
        if (currentUser != null && currentUser.host) {
            return;
        }
        setConnectionBannerText("Connection lost. Reconnecting before the game can move on.");
    }



    private void startHostHeartbeatIfNeeded() {
        User currentUser = userViewModel == null ? null : userViewModel.getUser().getValue();
        if (currentUser == null || !currentUser.host || (hostHeartbeatPulse != null && hostHeartbeatPulse.isRunning())) {
            return;
        }
        hostHeartbeatPulse = new RepeatingPulse(pulseRunner, GameFlowPolicy.HOST_HEARTBEAT_INTERVAL_MS, () -> {
            if (!firebaseConnected || !deviceNetworkAvailable) {
                stopHostHeartbeat();
                return;
            }
            userViewModel.writeHostHeartbeat();
        });
        hostHeartbeatPulse.start();
    }

    private void stopHostHeartbeat() {
        if (hostHeartbeatPulse != null) {
            hostHeartbeatPulse.stop();
        }
    }

    private void startPresencePulse() {
        if (presencePulse != null && presencePulse.isRunning()) {
            return;
        }
        presencePulse = new RepeatingPulse(pulseRunner, GameFlowPolicy.HOST_HEARTBEAT_INTERVAL_MS, () -> {
            // Room-level bars are re-checked on the pulse as well as on list changes. Eligibility is
            // a function of *elapsed time*, so nothing may change in the room at the moment someone
            // becomes removable - and a bar displaced by another snackbar has to be able to return.
            updateKickBar();
            updateHostAwayBar(hostAway);
            if (firebaseConnected && deviceNetworkAvailable) {
                markCurrentPlayerConnectedIfNeeded();
            }
        });
        presencePulse.start();
    }

    private void stopPresencePulse() {
        if (presencePulse != null) {
            presencePulse.stop();
        }
    }

    private void setConnectionBannerText(String message) {
        if (connectionBanner instanceof TextView) {
            ((TextView) connectionBanner).setText(message);
        }
    }

//    @Override
//    public void onNewToken(String token) {
//        Log.d(TAG, "Refreshed token: " + token);
//
//        // If you want to send messages to this application instance or
//        // manage this apps subscriptions on the server side, send the
//        // FCM registration token to your app server.
//        sendRegistrationToServer(token);
//    }

    @Override
    protected void onPause() {
        super.onPause();
        // Only a resume that follows a real pause may touch the connection - see onResume.
        resumedFromBackground = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Backgrounding this app is not free: measured on a real device, Android freezes the cached
        // process and the Realtime Database socket dies ~38s later even though the process lives,
        // and coming back did not re-establish it on its own - the app sat foreground and visible on
        // "Connection lost" for 4+ minutes. So resuming has to say so explicitly rather than assume
        // the SDK healed itself.
        // Guarded on an actual pause. onResume also runs immediately after onCreate, and forcing
        // the offline->online cycle *during* the first handshake wedged it outright - the app then
        // never connected at all on launch. Measured, after this fix was written the naive way.
        if (resumedFromBackground && userViewModel != null) {
            userViewModel.reconnectAfterResume();
        }
        resumedFromBackground = false;
        // The heartbeat is stopped in onDestroy and must come back with the Activity, or the room
        // looks host-less to everyone else after a rotation.
        startPresencePulse();
        startHostHeartbeatIfNeeded();
    }

    @Override
    protected void onDestroy() {
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }
        stopHostHeartbeat();
        stopPresencePulse();
        connectionHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
