package hr.ugljan.rad;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.window.OnBackInvokedDispatcher;

import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {
    private WebView web;
    private LocationManager lm;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean navActive;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#14181D"));
        w.setNavigationBarColor(Color.parseColor("#14181D"));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#14181D"));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0D1B26"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        // MapLibre laedt Schriften und Reliefbild per XHR aus den App-Dateien
        s.setAllowFileAccessFromFileURLs(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) { }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        web.loadUrl("file:///android_asset/index.html");

        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.GERMANY);
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    private void handleBack() {
        web.evaluateJavascript("window.goBack&&goBack()", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void startGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            lm.removeUpdates(this);
            // Waehrend der Fahrt jede Sekunde, sonst sparsamer (Akku)
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, navActive ? 1000 : 3000, navActive ? 0 : 5, this);
        } catch (Exception ignored) { }
        try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, navActive ? 3000 : 10000, 10, this);
        } catch (Exception ignored) { }
        try {
            Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null && System.currentTimeMillis() - last.getTime() < 10 * 60 * 1000) onLocationChanged(last);
        } catch (Exception ignored) { }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        startGps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startGps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Bei laufender Fahrt GPS weiterlaufen lassen (Vordergrund-Dienst haelt die App aktiv)
        if (!navActive) lm.removeUpdates(this);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        try {
            stopService(new Intent(this, NavService.class));
        } catch (Exception ignored) { }
        lm.removeUpdates(this);
        super.onDestroy();
    }

    @Override
    public void onLocationChanged(Location l) {
        if (LocationManager.NETWORK_PROVIDER.equals(l.getProvider()) && l.getAccuracy() > 200) return;
        final String js = "window.onGps&&onGps(" + l.getLatitude() + "," + l.getLongitude() + "," + l.getAccuracy() + ","
                + (l.hasSpeed() ? l.getSpeed() : -1) + "," + (l.hasBearing() ? l.getBearing() : -1) + ")";
        web.post(() -> web.evaluateJavascript(js, null));
    }

    @Override public void onStatusChanged(String p, int s, Bundle e) { }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }

    class Bridge {
        @JavascriptInterface
        public void keepScreenOn(final boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void setNavActive(final boolean on, final String text) {
            runOnUiThread(() -> {
                navActive = on;
                Intent i = new Intent(MainActivity.this, NavService.class).putExtra("text", text);
                try {
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
                        }
                        boolean loc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                        if (loc) {
                            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                        }
                    } else {
                        stopService(i);
                    }
                } catch (Exception ignored) { }
                startGps();
            });
        }

        @JavascriptInterface
        public void setFullscreen(final boolean on) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowInsetsController c = getWindow().getInsetsController();
                    if (c == null) return;
                    if (on) {
                        c.hide(WindowInsets.Type.systemBars());
                        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    } else {
                        c.show(WindowInsets.Type.systemBars());
                    }
                } else {
                    getWindow().getDecorView().setSystemUiVisibility(on
                            ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            : 0);
                }
            });
        }

        @JavascriptInterface
        public void speak(String text) {
            if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav");
        }

        @JavascriptInterface
        public boolean canSpeak() {
            return ttsReady;
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public boolean gpsEnabled() {
            try {
                return lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            } catch (Exception e) {
                return true;
            }
        }

        @JavascriptInterface
        public boolean hasPermission() {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void openLocationSettings() {
            runOnUiThread(() -> {
                if (!hasPermission()) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                } else {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                }
            });
        }
    }
}
