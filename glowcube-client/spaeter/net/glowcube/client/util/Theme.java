package net.glowcube.client.util;

/**
 * Die Farbwelt von GlowCube: tiefes Anthrazit als Grund, ein Verlauf von
 * Tuerkis nach Violett als Akzent. Der Akzent wandert langsam - deshalb sind
 * es Methoden und keine Konstanten.
 */
public final class Theme {
    private Theme() {
    }

    public static final int BACKDROP      = 0xC8070810;
    public static final int PANEL         = 0xF20E1018;
    public static final int PANEL_LIGHT   = 0xFF151926;
    public static final int RAIL          = 0xFF0B0D14;
    public static final int CARD          = 0xFF141826;
    public static final int CARD_HOVER    = 0xFF1B2133;
    public static final int OUTLINE       = 0x26FFFFFF;
    public static final int OUTLINE_SOFT  = 0x14FFFFFF;

    public static final int TEXT          = 0xFFF2F5FF;
    public static final int TEXT_DIM      = 0xFF8A93AC;
    public static final int TEXT_FAINT    = 0xFF565E75;

    public static final int ACCENT_A      = 0xFF3BF0D4;
    public static final int ACCENT_B      = 0xFF9B6BFF;
    public static final int ACCENT_C      = 0xFFFF5FA2;

    private static final long CYCLE_NANOS = 8_000_000_000L;

    /** Wandert in 8 Sekunden einmal durch den Verlauf. */
    public static float phase() {
        // floorMod, weil nanoTime laut Spezifikation auch negativ sein darf.
        return Math.floorMod(System.nanoTime(), CYCLE_NANOS) / (float) CYCLE_NANOS;
    }

    /** Der linke Ton des laufenden Verlaufs. */
    public static int accentStart() {
        return ColorUtil.gradient(ACCENT_A, ACCENT_B, ACCENT_C, phase());
    }

    /** Der rechte Ton - eine Viertelrunde spaeter, damit immer Kontrast da ist. */
    public static int accentEnd() {
        return ColorUtil.gradient(ACCENT_A, ACCENT_B, ACCENT_C, (phase() + 0.25f) % 1.0f);
    }

    /** Derselbe Verlauf, aber pro Zeile versetzt - fuer HUD-Listen. */
    public static int accentAt(float offset) {
        return ColorUtil.gradient(ACCENT_A, ACCENT_B, ACCENT_C, (phase() + offset) % 1.0f);
    }
}
