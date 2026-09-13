package de.adminfield.disguise;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holt Name, UUID und Skin eines Minecraft-Kontos bei Mojang.
 *
 * <p>Reines Java, keine Server-API - das laeuft im Hintergrund-Thread und darf deshalb nichts
 * am Spiel anfassen. Absichtlich ohne JSON-Bibliothek: die beiden Antworten sind so einfach
 * gebaut, dass Heraussuchen der Felder reicht und wir uns keine Abhaengigkeit einhandeln.
 */
public final class SkinFetch {

    /** Was Mojang ueber ein Konto liefert. */
    public record Skin(UUID id, String name, String value, String signature) {
    }

    private static final String NAME_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE_URL =
            "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false";

    private static final Map<String, Skin> CACHE = new ConcurrentHashMap<>();

    private SkinFetch() {
    }

    /**
     * Sucht ein Konto anhand des Namens.
     *
     * @return der Skin, oder {@code null}, wenn es den Namen nicht gibt
     * @throws Exception wenn Mojang nicht erreichbar ist oder die Antwort nicht passt
     */
    public static Skin lookup(String name) throws Exception {
        String clean = name == null ? "" : name.trim();
        if (!isValidName(clean)) {
            return null;
        }
        Skin cached = CACHE.get(clean.toLowerCase(Locale.ROOT));
        if (cached != null) {
            return cached;
        }

        String first = get(NAME_URL + clean);
        if (first == null || first.isBlank()) {
            return null;
        }
        String rawId = field(first, "id");
        String realName = field(first, "name");
        if (rawId == null || rawId.length() != 32) {
            return null;
        }

        String second = get(String.format(PROFILE_URL, rawId));
        if (second == null) {
            return null;
        }
        String value = field(second, "value");
        String signature = field(second, "signature");
        if (value == null) {
            return null;
        }

        Skin skin = new Skin(uuidOf(rawId), realName == null ? clean : realName, value, signature);
        CACHE.put(clean.toLowerCase(Locale.ROOT), skin);
        return skin;
    }

    /** Vergisst einen zwischengespeicherten Eintrag, damit ein geaenderter Skin neu geholt wird. */
    public static void forget(String name) {
        if (name != null) {
            CACHE.remove(name.trim().toLowerCase(Locale.ROOT));
        }
    }

    public static boolean isValidName(String name) {
        if (name == null || name.length() < 3 || name.length() > 16) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setRequestProperty("User-Agent", "AdminField");
        try {
            int status = connection.getResponseCode();
            if (status == 204 || status == 404) {
                // Mojang kennt den Namen nicht.
                return null;
            }
            if (status != 200) {
                throw new IllegalStateException("Mojang antwortete mit " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Liest den Wert eines Feldes aus der Antwort.
     *
     * <p>Die gesuchten Werte (UUID, Name, Base64-Skin, Signatur) enthalten selbst nie ein
     * Anfuehrungszeichen, deshalb reicht das erste schliessende Zeichen als Ende.
     */
    private static String field(String json, String key) {
        String needle = "\"" + key + "\"";
        int at = json.indexOf(needle);
        if (at < 0) {
            return null;
        }
        int colon = json.indexOf(':', at + needle.length());
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        int end = json.indexOf('"', start + 1);
        if (end < 0) {
            return null;
        }
        return json.substring(start + 1, end);
    }

    private static UUID uuidOf(String raw) {
        return UUID.fromString(raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-"
                + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-" + raw.substring(20));
    }
}
