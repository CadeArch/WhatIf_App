package com.CadeMixedUpGame.phoneapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.CadeMixedUpGame.api.AppLog;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    boolean longerThanTwoWeeks = false;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        AppLog.i(AppLog.PUSH, "FCM message received");
        // TODO: get shared preference of when user last logged on, if it has been longer than two weeks set LONGERTHANTWO WEEKS to true else false check mode
        SharedPreferences sh = getSharedPreferences("MixedUpSharedPrefs", 0);
        long lastLoggedOn = sh.getLong("logged-on",0);
        // if duration between now and last logged on is > 2 weeks set class variable to true and send the notification
        if (System.currentTimeMillis() - lastLoggedOn >  1209600000) {
            longerThanTwoWeeks = true;
        }
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        AppLog.d(AppLog.PUSH, "FCM from=" + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            AppLog.d(AppLog.PUSH, "FCM data payload keys=" + remoteMessage.getData().keySet());

        }
        else {
            if (longerThanTwoWeeks) {
                AppLog.i(AppLog.PUSH, "User inactive longer than two weeks; sending notification");
                sendNotification();
            }
        }

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            AppLog.d(AppLog.PUSH, "FCM notification body received");
        }

        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
    }

    // for making my own notification with the message recieved from Firebase Console Messages
    private void sendNotification() {
        AppLog.i(AppLog.PUSH, "Building inactivity notification");
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        String channelId = getString(R.string.default_notification_channel_id);
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.push_notification_if)
                        .setColor(getColor(R.color.orange))
                        .setContentTitle("Come Play")
                        .setContentText("It has been a while since you have played \"What If\"!")
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());
    }

}
