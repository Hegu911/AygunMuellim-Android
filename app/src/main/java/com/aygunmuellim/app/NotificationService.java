package com.aygunmuellim.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.webkit.CookieManager;

import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationService extends Service {

    private static final String CHANNEL_ID = "aygun_muellim_foreground";
    private static final int NOTIFICATION_ID = 1;
    private static final String BASE_URL = "https://lms-2hd.pages.dev";
    private volatile boolean running = true;

    @Override
    public void onCreate() {
        super.onCreate();
        createForegroundChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildForegroundNotification();
        startForeground(NOTIFICATION_ID, notification);

        Thread thread = new Thread(this::pollLoop);
        thread.setDaemon(true);
        thread.start();

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    private void createForegroundChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Aygün Müəllim Arxa Plan",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Arxa plan xidməti");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildForegroundNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Aygün Müəllim")
                .setContentText("Bildirişlər dinlənilir...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void pollLoop() {
        int lastAnnouncementId = 0;
        int lastMessageId = 0;

        while (running) {
            try {
                String cookie = getAuthCookie();
                if (cookie != null && !cookie.isEmpty()) {
                    lastAnnouncementId = checkAnnouncements(cookie, lastAnnouncementId);
                    lastMessageId = checkMessages(cookie, lastMessageId);
                }
            } catch (Exception ignored) {
            }

            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private String getAuthCookie() {
        CookieManager cookieManager = CookieManager.getInstance();
        return cookieManager.getCookie(BASE_URL);
    }

    private int checkAnnouncements(String cookie, int lastId) {
        try {
            String json = httpGet(BASE_URL + "/api/announcements", cookie);
            if (json == null) return lastId;

            int maxId = parseMaxId(json, "announcements", "id");
            if (maxId > 0 && lastId > 0 && maxId > lastId) {
                String title = extractField(json, "title");
                showNotification("Yeni elan", title != null ? title : "Yeni elan var");
            }
            return Math.max(maxId, lastId);
        } catch (Exception e) {
            return lastId;
        }
    }

    private int checkMessages(String cookie, int lastId) {
        try {
            String json = httpGet(BASE_URL + "/api/messages", cookie);
            if (json == null) return lastId;

            int maxMsgId = lastId;
            int idx = 0;

            while (true) {
                int startId = json.indexOf("\"id\":", idx);
                if (startId == -1) break;
                int endId = json.indexOf(",", startId + 5);
                if (endId == -1) endId = json.indexOf("}", startId + 5);
                if (endId == -1) break;
                int msgId = parseInt(json.substring(startId + 5, endId).trim());
                if (msgId > maxMsgId) maxMsgId = msgId;

                int nameStart = json.indexOf("\"student_name\":\"", idx);
                String name = null;
                if (nameStart != -1 && nameStart < endId + 50) {
                    int nameEnd = json.indexOf("\"", nameStart + 16);
                    if (nameEnd != -1) {
                        name = json.substring(nameStart + 16, nameEnd);
                    }
                }

                int lastMsgStart = json.indexOf("\"last_message\":{", idx);
                if (lastMsgStart != -1 && lastMsgStart < endId + 100) {
                    int lastMsgEnd = json.indexOf("}", lastMsgStart);
                    if (lastMsgEnd != -1) {
                        String lastMsg = json.substring(lastMsgStart + 16, lastMsgEnd);
                        int contentStart = lastMsg.indexOf("\"content\":\"");
                        if (contentStart != -1) {
                            contentStart += 10;
                            int contentEnd = lastMsg.indexOf("\"", contentStart);
                            if (contentEnd != -1) {
                                String content = lastMsg.substring(contentStart, contentEnd);
                                if (lastId > 0 && msgId > lastId) {
                                    showNotification(
                                            name != null ? name + " yazdı" : "Yeni mesaj",
                                            content
                                    );
                                }
                            }
                        }
                    }
                }

                idx = endId + 1;
            }

            return Math.max(maxMsgId, lastId);
        } catch (Exception e) {
            return lastId;
        }
    }

    private void showNotification(String title, String body) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, "aygun_muellim_notifications")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), notification);
        }
    }

    private String httpGet(String urlStr, String cookie) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cookie", cookie);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int code = conn.getResponseCode();
            if (code != 200) return null;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private int parseMaxId(String json, String arrayField, String idField) {
        try {
            int maxId = 0;
            int idx = 0;
            String searchKey = "\"" + idField + "\":";
            while (true) {
                int pos = json.indexOf(searchKey, idx);
                if (pos == -1) break;
                int end = json.indexOf(",", pos + searchKey.length());
                if (end == -1) end = json.indexOf("}", pos + searchKey.length());
                if (end == -1) break;
                int val = parseInt(json.substring(pos + searchKey.length(), end).trim());
                if (val > maxId) maxId = val;
                idx = end + 1;
            }
            return maxId;
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractField(String json, String field) {
        try {
            String key = "\"" + field + "\":\"";
            int start = json.indexOf(key);
            if (start == -1) return null;
            start += key.length();
            int end = json.indexOf("\"", start);
            return end != -1 ? json.substring(start, end) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
