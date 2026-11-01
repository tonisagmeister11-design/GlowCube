package app.velora.bike;

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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import android.window.OnBackInvokedDispatcher;

import java.util.ArrayList;
import java.util.Locale;

import org.json.JSONObject;

public class MainActivity extends Activity implements LocationListener {
    private WebView web;
    private Llm llm;
    private LocationManager lm;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean navActive;
    private long lastGpsTime;
    private SpeechRecognizer recognizer;

    private static final int LIGHT_BARS = Build.VERSION.SDK_INT >= 26
            ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;

    /** Helles Design: dunkle Symbole in Status- und Navigationsleiste. */
    private void lightBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c == null) return;
            int m = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(m, m);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(LIGHT_BARS);
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(Color.WHITE);
        w.setNavigationBarColor(Color.WHITE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#F3F6FA"));
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
        web.setBackgroundColor(Color.parseColor("#F3F6FA"));
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

            // Offline-Karten: heruntergeladene Kacheln direkt aus dem Speicher liefern
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                if (!"GET".equals(req.getMethod())) return null;
                String u = req.getUrl().toString();
                if (!isTileUrl(u)) return null;
                File f = tileFile(u);
                if (!f.exists()) return null;
                try {
                    Map<String, String> h = new HashMap<>();
                    h.put("Access-Control-Allow-Origin", "*");
                    return new WebResourceResponse(mimeOf(u), null, 200, "OK", h, new FileInputStream(f));
                } catch (Exception e) {
                    return null;
                }
            }
        });
        llm = new Llm(this, this::js);
        web.addJavascriptInterface(new Bridge(), "Android");
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        lightBars();
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

    static boolean isTileUrl(String u) {
        return u.startsWith("https://tiles.openfreemap.org/") || u.startsWith("https://s3.amazonaws.com/elevation-tiles-prod/")
                || u.startsWith("https://tile.waymarkedtrails.org/");
    }

    static String mimeOf(String u) {
        if (u.endsWith(".png")) return "image/png";
        if (u.endsWith(".pbf") || u.endsWith(".mvt")) return "application/x-protobuf";
        return "application/json";
    }

    File tileFile(String u) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(u.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return new File(new File(getFilesDir(), "tiles"), sb.toString());
        } catch (Exception e) {
            return new File(getFilesDir(), "tiles/x");
        }
    }

    private volatile boolean dlStop = false;

    private void js(String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
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
        if (llm != null) llm.unload();
        if (tts != null) tts.shutdown();
        if (recognizer != null) recognizer.destroy();
        try {
            stopService(new Intent(this, NavService.class));
        } catch (Exception ignored) { }
        lm.removeUpdates(this);
        super.onDestroy();
    }

    private void toJs(final String js) {
        web.post(() -> web.evaluateJavascript(js, null));
    }

    @Override
    public void onLocationChanged(Location l) {
        boolean gps = LocationManager.GPS_PROVIDER.equals(l.getProvider());
        if (gps) {
            lastGpsTime = System.currentTimeMillis();
        } else {
            // Grobe Mobilfunk-/WLAN-Ortung nur, wenn gerade kein GPS da ist: sonst springt der Punkt und das Tempo
            if (l.getAccuracy() > 200 || System.currentTimeMillis() - lastGpsTime < 15000) return;
        }
        float sacc = Build.VERSION.SDK_INT >= 26 && l.hasSpeedAccuracy() ? l.getSpeedAccuracyMetersPerSecond() : -1;
        final String js = "window.onGps&&onGps(" + l.getLatitude() + "," + l.getLongitude() + "," + l.getAccuracy() + ","
                + (gps && l.hasSpeed() ? l.getSpeed() : -1) + "," + (gps && l.hasBearing() ? l.getBearing() : -1) + ","
                + sacc + "," + (gps ? 1 : 0) + "," + (l.hasAltitude() ? l.getAltitude() : "null") + ")";
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
                    lightBars();
                } else {
                    getWindow().getDecorView().setSystemUiVisibility(on
                            ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            : LIGHT_BARS);
                }
            });
        }

        // Spracheingabe fuer Juno (freihaendig auf dem Rad)
        @JavascriptInterface
        public boolean canListen() {
            return SpeechRecognizer.isRecognitionAvailable(MainActivity.this);
        }

        @JavascriptInterface
        public void listen() {
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
                    toJs("window.onSpeech&&onSpeech(null,'perm')");
                    return;
                }
                if (recognizer == null) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(MainActivity.this);
                    recognizer.setRecognitionListener(new RecognitionListener() {
                        @Override public void onReadyForSpeech(Bundle b) { toJs("window.onSpeech&&onSpeech(null,'ready')"); }
                        @Override public void onBeginningOfSpeech() { }
                        @Override public void onRmsChanged(float v) { }
                        @Override public void onBufferReceived(byte[] b) { }
                        @Override public void onEndOfSpeech() { }
                        @Override public void onError(int e) { toJs("window.onSpeech&&onSpeech(null,'err" + e + "')"); }
                        @Override public void onResults(Bundle b) {
                            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            String t = r != null && !r.isEmpty() ? r.get(0) : "";
                            toJs("window.onSpeech&&onSpeech(" + JSONObject.quote(t) + ")");
                        }
                        @Override public void onPartialResults(Bundle b) { }
                        @Override public void onEvent(int t, Bundle b) { }
                    });
                }
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
                recognizer.startListening(i);
            });
        }

        @JavascriptInterface
        public void setVoiceLang(String tag) {
            // Ansagen in der App-Sprache; fehlt die Stimme, bleibt die bisherige
            try {
                if (tts == null) return;
                int r = tts.setLanguage(Locale.forLanguageTag(tag));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.GERMANY);
                else ttsReady = true;
            } catch (Exception ignored) { }
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
                if (v == null) return;
                // Android 7 kennt VibrationEffect noch nicht (sonst Absturz, kein Exception-Fall)
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                else v.vibrate(ms);
            } catch (Exception ignored) { }
        }

        // Haptik wie in guten Apps: kurze, knackige Effekte statt Brummen. level 1 = leicht, 2 = stark
        @JavascriptInterface
        public void haptic(String type, int level) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v == null || !v.hasVibrator() || level <= 0) return;
                float k = level >= 2 ? 1f : 0.55f;
                if (Build.VERSION.SDK_INT >= 31 && v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK,
                        VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_THUD)) {
                    VibrationEffect.Composition c = VibrationEffect.startComposition();
                    switch (type) {
                        case "tick": c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f * k); break;
                        case "click": c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f * k); break;
                        case "soft": c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f * k).addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f * k, 70); break;
                        case "success": c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.7f * k).addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f * k, 60); break;
                        case "celebrate": c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f * k).addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f * k, 90)
                                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.8f * k, 60).addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f * k, 40); break;
                        case "left": c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f * k).addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f * k, 130); break;
                        case "right": c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f * k).addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f * k, 130).addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f * k, 130); break;
                        case "warn": c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f * k).addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f * k, 120).addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f * k, 120); break;
                        default: c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f * k);
                    }
                    v.vibrate(c.compose());
                    return;
                }
                if (Build.VERSION.SDK_INT >= 29 && (type.equals("tick") || type.equals("click") || type.equals("soft"))) {
                    v.vibrate(VibrationEffect.createPredefined(type.equals("click") ? (level >= 2 ? VibrationEffect.EFFECT_HEAVY_CLICK : VibrationEffect.EFFECT_CLICK) : VibrationEffect.EFFECT_TICK));
                    return;
                }
                int a = level >= 2 ? 255 : 140;
                long[] t;
                int[] amp;
                switch (type) {
                    case "tick": t = new long[]{0, 12}; amp = new int[]{0, a / 2}; break;
                    case "click": case "soft": t = new long[]{0, 22}; amp = new int[]{0, a}; break;
                    case "success": t = new long[]{0, 30, 60, 45}; amp = new int[]{0, a / 2, 0, a}; break;
                    case "celebrate": t = new long[]{0, 35, 70, 35, 70, 80}; amp = new int[]{0, a, 0, a, 0, a}; break;
                    case "left": t = new long[]{0, 40, 110, 40}; amp = new int[]{0, a, 0, a}; break;
                    case "right": t = new long[]{0, 40, 110, 40, 110, 40}; amp = new int[]{0, a, 0, a, 0, a}; break;
                    case "warn": t = new long[]{0, 120, 90, 120, 90, 120}; amp = new int[]{0, a, 0, a, 0, a}; break;
                    default: t = new long[]{0, 25}; amp = new int[]{0, a};
                }
                if (Build.VERSION.SDK_INT < 26) v.vibrate(t, -1);
                else if (v.hasAmplitudeControl()) v.vibrate(VibrationEffect.createWaveform(t, amp, -1));
                else v.vibrate(VibrationEffect.createWaveform(t, -1));
            } catch (Exception ignored) { }
        }

        // Datei (z. B. GPX) im Ordner Downloads speichern, liefert den Ort oder "" bei Fehler
        @JavascriptInterface
        public String saveFile(String name, String mime, String text) {
            try {
                byte[] data = text.getBytes("UTF-8");
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) return "";
                    try (OutputStream o = getContentResolver().openOutputStream(uri)) { o.write(data); }
                    return "Downloads/" + name;
                }
                File f = new File(getExternalFilesDir(null), name);
                try (OutputStream o = new FileOutputStream(f)) { o.write(data); }
                return f.getAbsolutePath();
            } catch (Exception e) {
                return "";
            }
        }

        // Offline-Karten: Kacheln im Hintergrund laden, Fortschritt an VX.dlProgress(fertig, gesamt, fehler)
        @JavascriptInterface
        public void downloadTiles(String json) {
            dlStop = false;
            new Thread(() -> {
                int done = 0, fail = 0, total = 0;
                try {
                    JSONArray a = new JSONArray(json);
                    total = a.length();
                    new File(getFilesDir(), "tiles").mkdirs();
                    for (int i = 0; i < total && !dlStop; i++) {
                        String u = a.getString(i);
                        File f = tileFile(u);
                        if (!f.exists()) {
                            try {
                                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                                c.setConnectTimeout(15000); c.setReadTimeout(20000);
                                c.setRequestProperty("User-Agent", "Velora/7.0 (Android)");
                                int code = c.getResponseCode();
                                if (code == 200) {
                                    File tmp = new File(f.getPath() + ".tmp");
                                    try (InputStream in = c.getInputStream(); OutputStream o = new FileOutputStream(tmp)) {
                                        byte[] buf = new byte[16384]; int n;
                                        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                                    }
                                    tmp.renameTo(f);
                                } else if (code != 204 && code != 404) fail++;
                                c.disconnect();
                            } catch (Exception e) { fail++; }
                        }
                        done++;
                        if (done % 20 == 0 || done == total) js("window.VX&&VX.dlProgress(" + done + "," + total + "," + fail + ")");
                    }
                } catch (Exception e) { fail++; }
                js("window.VX&&VX.dlProgress(" + done + "," + total + "," + fail + ",true)");
            }).start();
        }

        @JavascriptInterface
        public void stopDownload() { dlStop = true; }

        // Hintergrund-Browser fuer die KI Soul: Seite laden und als Text an Soul._got(id, text) geben
        @JavascriptInterface
        public void fetchText(final String id, final String url) {
            new Thread(() -> {
                String out = null, err = null;
                try {
                    URL u = new URL(url);
                    String host = u.getHost().toLowerCase(Locale.ROOT);
                    if (!"https".equals(u.getProtocol()) || host.equals("localhost") || host.endsWith(".local") || host.matches("^(10|127|192\\.168|172\\.(1[6-9]|2\\d|3[01]))\\..*")) throw new Exception("blocked");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(10000); c.setReadTimeout(12000);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36");
                    c.setRequestProperty("Accept-Language", "de,en;q=0.8");
                    try (InputStream in = c.getInputStream()) {
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[16384]; int n;
                        while ((n = in.read(buf)) > 0 && bo.size() < 800000) bo.write(buf, 0, n);
                        out = bo.toString("UTF-8");
                    }
                    c.disconnect();
                } catch (Exception e) { err = String.valueOf(e.getMessage()); }
                js("window.Soul&&Soul._got(" + org.json.JSONObject.quote(id) + "," + org.json.JSONObject.quote(out == null ? "" : out) + "," + (err == null ? "null" : org.json.JSONObject.quote(err)) + ")");
            }).start();
        }

        @JavascriptInterface public String llmInfo() { return llm.info(); }
        @JavascriptInterface public void llmDownload(String w) { llm.download(w); }
        @JavascriptInterface public void llmCancel() { llm.cancel(); }
        @JavascriptInterface public void llmDelete(String w) { llm.delete(w); }
        @JavascriptInterface public void llmLoad(String w) { llm.load(w); }
        @JavascriptInterface public void llmUnload() { llm.unload(); }
        @JavascriptInterface public void llmGenerate(String id, String prompt, int n, float temp) { llm.generate(id, prompt, n, temp); }
        @JavascriptInterface public void llmStop() { llm.stop(); }

        @JavascriptInterface
        public void shareText(String title, String text) {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(i, title));
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public String tileInfo() {
            File[] l = new File(getFilesDir(), "tiles").listFiles();
            long b = 0; int n = 0;
            if (l != null) for (File f : l) { b += f.length(); n++; }
            return n + "," + b;
        }

        @JavascriptInterface
        public void clearTiles() {
            File[] l = new File(getFilesDir(), "tiles").listFiles();
            if (l != null) for (File f : l) f.delete();
        }

        // Startbildschirm-Widget mit Wochenkilometern und Serie
        @JavascriptInterface
        public void setWidget(String big, String small, String line) {
            getSharedPreferences("widget", MODE_PRIVATE).edit().putString("big", big).putString("small", small).putString("line", line).apply();
            AppWidgetManager m = AppWidgetManager.getInstance(MainActivity.this);
            int[] ids = m.getAppWidgetIds(new ComponentName(MainActivity.this, VeloraWidget.class));
            if (ids.length > 0) VeloraWidget.update(MainActivity.this, m, ids);
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
