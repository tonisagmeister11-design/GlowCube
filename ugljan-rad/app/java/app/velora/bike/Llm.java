package app.velora.bike;

import android.app.ActivityManager;
import android.content.Context;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * Soul-Sprachmodell direkt auf dem Handy (llama.cpp ueber JNI).
 * Kein Server, keine Kosten: das Modell wird einmal heruntergeladen und laeuft dann offline.
 */
public class Llm {
    public interface Out { void js(String code); }
    public interface TokenCb { boolean onToken(byte[] piece); }

    static native long nLoad(String path, int nCtx, int nThreads);
    static native int nGenerate(long h, String prompt, int nPredict, float temp, float topP, int topK, float rep, TokenCb cb);
    static native void nStop(long h);
    static native void nFree(long h);

    // Qwen3 (Apache-2.0). "std" laeuft auf jedem Handy, "pro" ist klueger und braucht mehr RAM.
    static final String[][] MODELS = {
        {"std", "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            "1107409472", "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897", "soul-std.gguf"},
        {"pro", "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
            "2497281120", "3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597", "soul-pro.gguf"},
    };

    private static boolean libOk = false;
    private static String libName = "";
    private final Context ctx;
    private final Out out;
    private final ExecutorService gen = Executors.newSingleThreadExecutor();
    private volatile long handle = 0;
    private volatile String loaded = "";
    private volatile String loading = "";
    private volatile boolean dlStop = false;
    private volatile String dlWhich = "";
    private volatile long dlDone = 0, dlTotal = 0;
    private volatile String dlState = "";

    Llm(Context c, Out o) {
        ctx = c; out = o;
        loadLib();
    }

    private static synchronized void loadLib() {
        if (libOk) return;
        boolean dot = false;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String l;
            while ((l = r.readLine()) != null) if (l.startsWith("Features") && l.contains(" asimddp")) { dot = true; break; }
        } catch (Exception ignored) { }
        String[] order = dot ? new String[]{"velora_llm", "velora_llm_base"} : new String[]{"velora_llm_base"};
        for (String n : order) {
            try { System.loadLibrary(n); libOk = true; libName = n; return; } catch (Throwable ignored) { }
        }
    }

    private String[] model(String w) {
        for (String[] m : MODELS) if (m[0].equals(w)) return m;
        return null;
    }

    private File file(String w) {
        String[] m = model(w);
        return new File(new File(ctx.getFilesDir(), "soul"), m == null ? "x" : m[4]);
    }

    private boolean have(String w) {
        String[] m = model(w);
        File f = file(w);
        return m != null && f.exists() && f.length() == Long.parseLong(m[2]);
    }

    long ramMb() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return mi.totalMem / (1024 * 1024);
    }

    String info() {
        try {
            JSONObject j = new JSONObject();
            long ram = ramMb();
            j.put("ok", libOk);
            j.put("lib", libName);
            j.put("ram", ram);
            j.put("rec", ram >= 5400 ? "pro" : "std");
            j.put("proOk", ram >= 3700);
            j.put("loaded", loaded);
            j.put("loading", loading);
            JSONObject ms = new JSONObject();
            for (String[] m : MODELS) {
                JSONObject x = new JSONObject();
                x.put("have", have(m[0]));
                x.put("size", Long.parseLong(m[2]));
                File p = new File(file(m[0]).getPath() + ".part");
                x.put("part", p.exists() ? p.length() : 0);
                ms.put(m[0], x);
            }
            j.put("models", ms);
            j.put("free", new File(ctx.getFilesDir().getPath()).getUsableSpace());
            if (!dlWhich.isEmpty()) {
                JSONObject d = new JSONObject();
                d.put("which", dlWhich); d.put("done", dlDone); d.put("total", dlTotal); d.put("state", dlState);
                j.put("dl", d);
            }
            return j.toString();
        } catch (Exception e) { return "{}"; }
    }

    private void ev(String type, String data) {
        out.js("window.SoulLLM&&SoulLLM._ev(" + JSONObject.quote(type) + "," + data + ")");
    }

    void download(final String w) {
        final String[] m = model(w);
        if (m == null || !dlWhich.isEmpty()) return;
        dlWhich = w; dlStop = false; dlState = "run";
        new Thread(() -> {
            String err = null;
            File dst = file(w);
            File part = new File(dst.getPath() + ".part");
            try {
                dst.getParentFile().mkdirs();
                long total = Long.parseLong(m[2]);
                dlTotal = total;
                if (dst.getParentFile().getUsableSpace() < total - part.length() + 200L * 1024 * 1024) throw new Exception("space");
                String url = m[1];
                for (int tries = 0; tries < 8 && !dlStop; tries++) {
                    long have = part.exists() ? part.length() : 0;
                    if (have >= total) break;
                    try {
                        HttpURLConnection c = null;
                        // Weiterleitungen (Hugging Face -> CDN) selbst folgen, damit Range erhalten bleibt
                        String u = url;
                        for (int r = 0; r < 6; r++) {
                            c = (HttpURLConnection) new URL(u).openConnection();
                            c.setInstanceFollowRedirects(false);
                            c.setConnectTimeout(15000); c.setReadTimeout(30000);
                            c.setRequestProperty("User-Agent", "BikeOra/6.6 (Android)");
                            if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
                            int code = c.getResponseCode();
                            if (code >= 300 && code < 400) { u = new URL(new URL(u), c.getHeaderField("Location")).toString(); c.disconnect(); continue; }
                            break;
                        }
                        int code = c.getResponseCode();
                        if (code == 200 && have > 0) have = 0;   // Server kann kein Range -> neu
                        else if (code != 200 && code != 206) throw new Exception("http " + code);
                        try (InputStream in = c.getInputStream(); RandomAccessFile raf = new RandomAccessFile(part, "rw")) {
                            raf.setLength(have); raf.seek(have);
                            byte[] buf = new byte[1 << 16]; int n; long last = 0;
                            while (!dlStop && (n = in.read(buf)) > 0) {
                                raf.write(buf, 0, n); have += n; dlDone = have;
                                long now = System.currentTimeMillis();
                                if (now - last > 400) { last = now; ev("dl", "{\"which\":\"" + w + "\",\"done\":" + have + ",\"total\":" + total + "}"); }
                            }
                        }
                        c.disconnect();
                        tries = 0;
                    } catch (Exception e) {
                        err = String.valueOf(e.getMessage());
                        if (dlStop) break;
                        try { Thread.sleep(2000L * (tries + 1)); } catch (InterruptedException ignored) { }
                    }
                }
                if (dlStop) throw new Exception("stopped");
                if (part.length() != total) throw new Exception(err == null ? "incomplete" : err);
                dlState = "check";
                ev("dl", "{\"which\":\"" + w + "\",\"state\":\"check\",\"done\":" + total + ",\"total\":" + total + "}");
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream in = new FileInputStream(part)) {
                    byte[] buf = new byte[1 << 20]; int n;
                    while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
                }
                StringBuilder sb = new StringBuilder();
                for (byte b : md.digest()) sb.append(String.format("%02x", b));
                if (!sb.toString().equals(m[3])) { part.delete(); throw new Exception("checksum"); }
                if (!part.renameTo(dst)) throw new Exception("rename");
                err = null;
            } catch (Exception e) {
                err = String.valueOf(e.getMessage());
            }
            dlWhich = ""; dlState = "";
            ev("dlEnd", "{\"which\":\"" + w + "\",\"ok\":" + (err == null) + ",\"err\":" + JSONObject.quote(err == null ? "" : err) + "}");
        }).start();
    }

    void cancel() { dlStop = true; }

    void delete(String w) {
        if (w.equals(loaded)) unload();
        file(w).delete();
        new File(file(w).getPath() + ".part").delete();
    }

    void unload() {
        final long h = handle;
        handle = 0; loaded = "";
        if (h != 0) gen.execute(() -> nFree(h));
    }

    void load(final String w) {
        if (!libOk) { ev("load", "{\"ok\":false,\"err\":\"lib\"}"); return; }
        if (w.equals(loaded) && handle != 0) { ev("load", "{\"ok\":true,\"which\":\"" + w + "\"}"); return; }
        if (!have(w)) { ev("load", "{\"ok\":false,\"err\":\"missing\"}"); return; }
        loading = w;
        gen.execute(() -> {
            if (handle != 0) { nFree(handle); handle = 0; loaded = ""; }
            int cores = Runtime.getRuntime().availableProcessors();
            int threads = cores >= 8 ? 4 : Math.max(2, cores / 2);
            long h = nLoad(file(w).getPath(), 2048, threads);
            loading = "";
            if (h != 0) { handle = h; loaded = w; }
            ev("load", "{\"ok\":" + (h != 0) + ",\"which\":\"" + w + "\"}");
        });
    }

    void stop() {
        long h = handle;
        if (h != 0) nStop(h);
    }

    void generate(final String id, final String prompt, final int nPredict, final float temp) {
        gen.execute(() -> {
            long h = handle;
            if (h == 0) { ev("end", "{\"id\":" + JSONObject.quote(id) + ",\"n\":-1}"); return; }
            final ByteArrayOutputStream pend = new ByteArrayOutputStream();
            final long[] last = {0};
            final StringBuilder chunk = new StringBuilder();
            int n = nGenerate(h, prompt, nPredict, temp, 0.9f, 40, 1.1f, piece -> {
                pend.write(piece, 0, piece.length);
                byte[] b = pend.toByteArray();
                int ok = utf8Complete(b);
                if (ok > 0) {
                    chunk.append(new String(b, 0, ok, StandardCharsets.UTF_8));
                    pend.reset();
                    pend.write(b, ok, b.length - ok);
                }
                long now = System.currentTimeMillis();
                if (chunk.length() > 0 && now - last[0] > 60) {
                    last[0] = now;
                    ev("tok", "{\"id\":" + JSONObject.quote(id) + ",\"t\":" + JSONObject.quote(chunk.toString()) + "}");
                    chunk.setLength(0);
                }
                return true;
            });
            if (chunk.length() > 0) ev("tok", "{\"id\":" + JSONObject.quote(id) + ",\"t\":" + JSONObject.quote(chunk.toString()) + "}");
            ev("end", "{\"id\":" + JSONObject.quote(id) + ",\"n\":" + n + "}");
        });
    }

    /** Laenge des Anfangs, der aus vollstaendigen UTF-8-Zeichen besteht. */
    static int utf8Complete(byte[] b) {
        int i = b.length;
        int k = 0;
        while (k < 4 && i - 1 - k >= 0 && (b[i - 1 - k] & 0xC0) == 0x80) k++;
        int lead = i - 1 - k;
        if (lead < 0) return 0;
        int c = b[lead] & 0xFF;
        int need = c < 0x80 ? 1 : c >= 0xF0 ? 4 : c >= 0xE0 ? 3 : c >= 0xC0 ? 2 : 1;
        return (k + 1 >= need) ? i : lead;
    }
}
