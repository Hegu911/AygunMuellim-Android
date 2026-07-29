package com.aygunmuellim.app;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CheckAlarmReceiver extends BroadcastReceiver {

    private static final String BASE_URL = "https://lms-2hd.pages.dev";
    private static final String PREFS_NAME = "alarm_prefs";
    private static final String KEY_LAST_MSG_ID = "lastAlarmMsgId";
    private static final String KEY_LAST_ANN_ID = "lastAlarmAnnId";

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        new Thread(() -> {
            try {
                String cookie = getAuthCookie(context);
                if (cookie == null || cookie.isEmpty()) return;

                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                int lastMsgId = prefs.getInt(KEY_LAST_MSG_ID, 0);
                int lastAnnId = prefs.getInt(KEY_LAST_ANN_ID, 0);

                int newMsgId = checkMessages(cookie, lastMsgId, context);
                int newAnnId = checkAnnouncements(cookie, lastAnnId, context);

                if (newMsgId > lastMsgId) {
                    prefs.edit().putInt(KEY_LAST_MSG_ID, newMsgId).apply();
                }
                if (newAnnId > lastAnnId) {
                    prefs.edit().putInt(KEY_LAST_ANN_ID, newAnnId).apply();
                }
            } catch (Exception ignored) {
            } finally {
                pendingResult.finish();
            }
        }).start();
    }

    private String getAuthCookie(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("aygun_prefs", Context.MODE_PRIVATE);
        return prefs.getString("auth_cookie", null);
    }

    private int checkMessages(String cookie, int lastId, Context context) {
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
                                    showNotification(context,
                                            name != null ? name + " yazd\u0131" : "Yeni mesaj",
                                            content);
                                }
                            }
                        }
                    }
                }

                idx = endId + 1;
            }

            return maxMsgId;
        } catch (Exception e) {
            return lastId;
        }
    }

    private int checkAnnouncements(String cookie, int lastId, Context context) {
        try {
            String json = httpGet(BASE_URL + "/api/announcements", cookie);
            if (json == null) return lastId;

            int maxId = 0;
            int idx = 0;
            while (true) {
                int pos = json.indexOf("\"id\":", idx);
                if (pos == -1) break;
                int end = json.indexOf(",", pos + 5);
                if (end == -1) end = json.indexOf("}", pos + 5);
                if (end == -1) break;
                int id = parseInt(json.substring(pos + 5, end).trim());
                if (id > maxId) maxId = id;
                idx = end + 1;
            }

            if (maxId > 0 && lastId > 0 && maxId > lastId) {
                String title = extractField(json, "title");
                showNotification(context, "Yeni elan", title != null ? title : "Yeni elan var");
            }

            return Math.max(maxId, lastId);
        } catch (Exception e) {
            return lastId;
        }
    }

    private void showNotification(Context context, String title, String body) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "aygun_muellim_notifications")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
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
