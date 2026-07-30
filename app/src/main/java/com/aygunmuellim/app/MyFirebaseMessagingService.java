package com.aygunmuellim.app;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String BASE_URL = "https://lms-2hd.pages.dev";

    @Override
    public void onNewToken(String token) {
        saveToken(token);
        registerWithServer();
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        String title = null;
        String body = null;

        if (message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        }
        if (title == null && message.getData() != null) {
            title = message.getData().get("title");
            body = message.getData().get("body");
        }
        if (title == null) title = "Ayg\u00fcn M\u00fc\u0259llim";
        if (body == null) body = "Yeni bildiri\u015f";

        showNotification(title, body);
    }

    private void showNotification(String title, String body) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "aygun_muellim_notifications")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void saveToken(String token) {
        getSharedPreferences("aygun_prefs", MODE_PRIVATE)
                .edit().putString("fcm_token", token).apply();
    }

    private void registerWithServer() {
        new Thread(() -> {
            try {
                String token = getSharedPreferences("aygun_prefs", MODE_PRIVATE)
                        .getString("fcm_token", null);
                if (token == null) return;

                SharedPreferences prefs = getSharedPreferences("aygun_prefs", MODE_PRIVATE);
                String cookie = prefs.getString("auth_cookie", null);
                if (cookie == null || cookie.isEmpty()) return;

                URL url = new URL(BASE_URL + "/api/auth/register-device-token");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Cookie", cookie);
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                String json = "{\"token\":\"" + token + "\"}";
                OutputStream os = conn.getOutputStream();
                os.write(json.getBytes("UTF-8"));
                os.close();

                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {
            }
        }).start();
    }
}
