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
import com.CadeMixedUpGame.api.GameFlowPolicy;
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
    private Runnable hostDisconnectRunnable;
    private Runnable hostConnectionCountdownRunnable;
    private Runnable hostHeartbeatRunnable;
    private Runnable presencePulseRunnable;
    private long pendingHostDisconnectDeadlineMs = 0L;
    private boolean hostDisconnectExpired = false;
    private boolean hostLocalGraceExpired = false;
    private String pendingExpiredRoomMessage = "";

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

        // Intentionally disabling back navigation (including the predictive back gesture)
        // for all fragments so players cannot back out mid-game and corrupt room state.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // no-op
            }
        });

        // assure nightmode wont work in app
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setupConnectionBanner();
        setupHostDisconnectNavigation();
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
            handleDisruptedRoomMessage(message);
        });
    }

    private void setupHostDisconnectNavigation() {
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        userViewModel.hostDisconnectedMessage.observe(this, message -> {
            if (message == null || message.length() == 0 || handlingHostDisconnect) {
                return;
            }
            userViewModel.clearHostDisconnectedMessage();
            handleDisruptedRoomMessage(message);
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

    private void sendPlayerHomeAfterHostDisconnect(String message) {
        if (handlingHostDisconnect) {
            return;
        }
        handlingHostDisconnect = true;
        User currentUser = userViewModel.getUser().getValue();
        String room = currentUser == null ? "" : currentUser.gameRoom;
        AppLog.w(AppLog.ROOM, "Host disconnected; returning client home room=" + room
                + ", from=" + Utils.currentFragmentName(this));

        userViewModel.removePlayersListenerOnDB();
        roomViewModel.clearLocalRoundState();
        if (room != null && room.length() > 0) {
            roomViewModel.deleteRoom(room);
            if (currentUser != null && currentUser.host) {
                roomViewModel.deleteExpiredRoomMarker(room);
            }
        }
        userViewModel.reset();
        userViewModel.clearLocalRoomIdentity();
        stopHostHeartbeat();
        cancelPendingHostDisconnect();
        Utils.navigateLandingReplacingCurrent(this);
        UiMessenger.showSnackbar(findViewById(R.id.fragment_container), message);
        roomViewModel.expiredRoomMessage.setValue("");
        pendingHostDisconnectDeadlineMs = 0L;
        hostDisconnectExpired = false;
        hostLocalGraceExpired = false;
        pendingExpiredRoomMessage = "";
        handlingHostDisconnect = false;
    }

    private void handleDisruptedRoomMessage(String message) {
        User currentUser = userViewModel == null ? null : userViewModel.getUser().getValue();
        if (currentUser != null && currentUser.host && !hostLocalGraceExpired) {
            pendingExpiredRoomMessage = message;
            AppLog.w(AppLog.ROOM, "Host disruption message held until local grace expires");
            return;
        }
        sendPlayerHomeAfterHostDisconnect(message);
    }

    private void scheduleHostDisconnect(long disconnectedAt) {
        cancelPendingHostDisconnect();
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - disconnectedAt);
        long remainingMs = Math.max(0L, GameFlowPolicy.CONNECTION_GRACE_MS - elapsedMs);
        pendingHostDisconnectDeadlineMs = System.currentTimeMillis() + remainingMs;
        hostDisconnectExpired = false;
        AppLog.w(AppLog.ROOM, "Host disconnected grace timer started remainingMs=" + remainingMs);
        hostDisconnectRunnable = () -> expireHostDisconnectedRoom("timer expired");
        connectionHandler.postDelayed(hostDisconnectRunnable, remainingMs);
    }

    private void scheduleHostHeartbeatExpiration(long lastSeenAt) {
        User currentUser = userViewModel == null ? null : userViewModel.getUser().getValue();
        if (currentUser == null || currentUser.host) {
            return;
        }
        cancelPendingHostDisconnect();
        long remainingMs = GameFlowPolicy.millisUntilHostHeartbeatExpires(System.currentTimeMillis(), lastSeenAt);
        pendingHostDisconnectDeadlineMs = System.currentTimeMillis() + remainingMs;
        hostDisconnectExpired = false;
        AppLog.w(AppLog.ROOM, "Host heartbeat deadline scheduled remainingMs=" + remainingMs);
        hostDisconnectRunnable = () ->
                connectionHandler.postDelayed(
                        () -> expireHostDisconnectedRoom("host heartbeat expired"),
                        GameFlowPolicy.CLIENT_HOME_AFTER_HOST_EXPIRE_DELAY_MS);
        connectionHandler.postDelayed(hostDisconnectRunnable, remainingMs);
    }

    private void handleHostConnectionRecovered(boolean cancelHeartbeatTimer) {
        if (hostDisconnectExpired) {
            expireHostDisconnectedRoom("host recovered after expiration");
            return;
        }
        if (pendingHostDisconnectDeadlineMs > 0L && System.currentTimeMillis() >= pendingHostDisconnectDeadlineMs) {
            expireHostDisconnectedRoom("host recovered after deadline");
            return;
        }
        if (cancelHeartbeatTimer) {
            cancelPendingHostDisconnect();
            pendingHostDisconnectDeadlineMs = 0L;
        }
    }

    private void expireHostDisconnectedRoom(String reason) {
        if (handlingHostDisconnect || hostDisconnectExpired) {
            return;
        }
        hostDisconnectExpired = true;
        AppLog.w(AppLog.ROOM, "Expiring room after host disconnect: " + reason);
        User currentUser = userViewModel == null ? null : userViewModel.getUser().getValue();
        String room = currentUser == null ? "" : currentUser.gameRoom;
        String message = "Sorry! Host disconnected - create a new game!";
        if (room == null || room.length() == 0) {
            sendPlayerHomeAfterHostDisconnect(message);
            return;
        }
        roomViewModel.markRoomExpired(room, message, () -> sendPlayerHomeAfterHostDisconnect(message));
    }

    private void cancelPendingHostDisconnect() {
        if (hostDisconnectRunnable != null) {
            connectionHandler.removeCallbacks(hostDisconnectRunnable);
            hostDisconnectRunnable = null;
            AppLog.i(AppLog.ROOM, "Host disconnect grace timer cancelled");
        }
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
        long cutoff = System.currentTimeMillis() - GameFlowPolicy.EXPIRED_ROOM_TOMBSTONE_TTL_MS;
        roomViewModel.cleanupOldExpiredRoomMarkers(cutoff);
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
        connectionBanner.setVisibility(connected ? View.GONE : View.VISIBLE);
        if (connected) {
            if (hostLocalGraceExpired && pendingExpiredRoomMessage.length() > 0) {
                sendPlayerHomeAfterHostDisconnect(pendingExpiredRoomMessage);
                return;
            }
            stopHostConnectionCountdown();
            markCurrentPlayerConnectedIfNeeded();
        }
        else {
            stopHostHeartbeat();
            updateDisconnectedBannerText();
            AppLog.w(AppLog.FIREBASE, "Showing offline connection banner");
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
            startHostConnectionCountdown();
            return;
        }
        setConnectionBannerText("Connection lost. Reconnecting before the game can move on.");
    }

    private void startHostConnectionCountdown() {
        if (hostConnectionCountdownRunnable != null) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        hostConnectionCountdownRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsedMs = Math.max(0L, System.currentTimeMillis() - startedAt);
                long remainingMs = Math.max(0L, GameFlowPolicy.CONNECTION_GRACE_MS - elapsedMs);
                long remainingSeconds = Math.max(0L, (remainingMs + 999L) / 1000L);
                if (remainingMs > 0L) {
                    setConnectionBannerText("Connection lost. Host grace: " + remainingSeconds + "s before players are sent home.");
                }
                else {
                    hostLocalGraceExpired = true;
                    setConnectionBannerText("Connection lost. Create a new game after internet connection is restored.");
                }
                if (remainingMs > 0L) {
                    connectionHandler.postDelayed(this, 1000L);
                }
            }
        };
        hostConnectionCountdownRunnable.run();
    }

    private void stopHostConnectionCountdown() {
        if (hostConnectionCountdownRunnable != null) {
            connectionHandler.removeCallbacks(hostConnectionCountdownRunnable);
            hostConnectionCountdownRunnable = null;
        }
        if (!hostLocalGraceExpired) {
            pendingExpiredRoomMessage = "";
        }
        setConnectionBannerText("Connection lost. Check Wi-Fi and try again.");
    }

    private void startHostHeartbeatIfNeeded() {
        User currentUser = userViewModel == null ? null : userViewModel.getUser().getValue();
        if (currentUser == null || !currentUser.host || hostHeartbeatRunnable != null) {
            return;
        }
        hostHeartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (!firebaseConnected || !deviceNetworkAvailable) {
                    stopHostHeartbeat();
                    return;
                }
                userViewModel.writeHostHeartbeat();
                connectionHandler.postDelayed(this, GameFlowPolicy.HOST_HEARTBEAT_INTERVAL_MS);
            }
        };
        hostHeartbeatRunnable.run();
    }

    private void stopHostHeartbeat() {
        if (hostHeartbeatRunnable != null) {
            connectionHandler.removeCallbacks(hostHeartbeatRunnable);
            hostHeartbeatRunnable = null;
        }
    }

    private void startPresencePulse() {
        if (presencePulseRunnable != null) {
            return;
        }
        presencePulseRunnable = new Runnable() {
            @Override
            public void run() {
                if (firebaseConnected && deviceNetworkAvailable) {
                    markCurrentPlayerConnectedIfNeeded();
                }
                connectionHandler.postDelayed(this, GameFlowPolicy.HOST_HEARTBEAT_INTERVAL_MS);
            }
        };
        presencePulseRunnable.run();
    }

    private void stopPresencePulse() {
        if (presencePulseRunnable != null) {
            connectionHandler.removeCallbacks(presencePulseRunnable);
            presencePulseRunnable = null;
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
