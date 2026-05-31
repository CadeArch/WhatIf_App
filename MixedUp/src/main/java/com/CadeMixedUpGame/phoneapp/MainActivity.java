package com.CadeMixedUpGame.phoneapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;


public class MainActivity extends AppCompatActivity {
    private RoomViewModel roomViewModel;
    private View connectionBanner;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean firebaseConnected = true;
    private boolean deviceNetworkAvailable = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // assure nightmode wont work in app
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setupConnectionBanner();

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
        boolean connected = firebaseConnected || deviceNetworkAvailable;
        connectionBanner.setVisibility(connected ? View.GONE : View.VISIBLE);
        if (!connected) {
            AppLog.w(AppLog.FIREBASE, "Showing offline connection banner");
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
    public void onBackPressed() {
        // by not calling the below i am disabling the phones back button for all fragments
//        super.onBackPressed();

    }

    @Override
    protected void onDestroy() {
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }
        super.onDestroy();
    }
}
