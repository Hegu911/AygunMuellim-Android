package com.aygunmuellim.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private Handler cookieHandler;
    private static final String PREFS_NAME = "aygun_prefs";
    private static final String KEY_AUTH_COOKIE = "auth_cookie";
    private static final String BASE_URL = "https://lms-2hd.pages.dev";

    static void saveAuthCookie(Context context) {
        try {
            String cookie = CookieManager.getInstance().getCookie(BASE_URL);
            if (cookie != null && !cookie.isEmpty()) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putString(KEY_AUTH_COOKIE, cookie).apply();
            }
        } catch (Exception ignored) {
        }
    }

    static String getSavedAuthCookie(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_AUTH_COOKIE, null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        String savedCookie = getSavedAuthCookie(this);
        if (savedCookie != null) {
            try {
                String[] parts = savedCookie.split(";\\s*");
                for (String part : parts) {
                    part = part.trim();
                    if (!part.isEmpty()) {
                        CookieManager.getInstance().setCookie(BASE_URL, part);
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush();
                }
            } catch (Exception ignored) {
            }
        }

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        settings.setMediaPlaybackRequiresUserGesture(false);

        String defaultUA = settings.getUserAgentString();
        settings.setUserAgentString(defaultUA + " AygunMuellim/1.0");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                saveAuthCookie(MainActivity.this);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidNotifier");

        webView.loadUrl(BASE_URL);

        Intent serviceIntent = new Intent(this, NotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        scheduleAlarm();

        cookieHandler = new Handler(Looper.getMainLooper());
        cookieHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                saveAuthCookie(MainActivity.this);
                cookieHandler.postDelayed(this, 30000);
            }
        }, 5000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        saveAuthCookie(this);
    }

    @Override
    protected void onDestroy() {
        if (cookieHandler != null) {
            cookieHandler.removeCallbacksAndMessages(null);
        }
        saveAuthCookie(this);
        super.onDestroy();
    }

    private void scheduleAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, CheckAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        long first = System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(first, null);
            am.setAlarmClock(info, pendingIntent);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, first, pendingIntent);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
