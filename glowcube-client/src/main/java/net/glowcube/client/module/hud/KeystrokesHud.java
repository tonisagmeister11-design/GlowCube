package net.glowcube.client.module.hud;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.hud.KlickZaehler;
import net.glowcube.client.util.ColorUtil;
import net.minecraft.client.KeyMapping;

import java.util.Locale;

/**
 * Die gedrueckten Tasten - aus AxolotlClient ({@code KeystrokeHud}) mit
 * dessen Standardanordnung und -farben: W oben, A S D darunter, die zwei
 * Maustasten, darunter die Leertaste als Balken. Gedrueckte Tasten werden
 * hell, der Wechsel blendet ueber 100 ms weich ueber.
 */
public final class KeystrokesHud extends HudModul {
    private static final int HINTERGRUND = 0x64000000;
    private static final int HINTERGRUND_GEDRUECKT = 0x64FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_GEDRUECKT = 0xFF000000;
    private static final float BLENDE_MS = 100f;

    private final BooleanSetting mausCps = register(
            new BooleanSetting("CPS in Maustasten", "Statt LMB/RMB die Klicks pro Sekunde zeigen", false));
    private final BooleanSetting leertaste = register(
            new BooleanSetting("Leertaste", "Den Balken fuer die Leertaste zeigen", true));

    /** x, y, Breite, Hoehe - wie AxolotlClients setDefaultKeystrokes. */
    private static final int[][] FELDER = {
            {18, 0, 17, 17},   // vor
            {0, 18, 17, 17},   // links
            {18, 18, 17, 17},  // zurueck
            {36, 18, 17, 17},  // rechts
            {0, 36, 26, 17},   // angreifen
            {27, 36, 26, 17},  // benutzen
            {0, 54, 53, 7},    // springen
    };

    private final float[] stand = new float[FELDER.length];
    private long letzterFrame;

    public KeystrokesHud() {
        super("Keystrokes", "Zeigt WASD, Maustasten und Leertaste", false, Category.PVP_HUD);
    }

    private KeyMapping taste(int i) {
        return switch (i) {
            case 0 -> mc.options.keyUp;
            case 1 -> mc.options.keyLeft;
            case 2 -> mc.options.keyDown;
            case 3 -> mc.options.keyRight;
            case 4 -> mc.options.keyAttack;
            case 5 -> mc.options.keyUse;
            default -> mc.options.keyJump;
        };
    }

    private String beschriftung(int i) {
        if (i == 4) {
            return mausCps.get() ? KlickZaehler.links() + " CPS" : "LMB";
        }
        if (i == 5) {
            return mausCps.get() ? KlickZaehler.rechts() + " CPS" : "RMB";
        }
        String name = taste(i).getTranslatedKeyMessage().getString();
        return (name.length() > 3 ? name.substring(0, 3) : name).toUpperCase(Locale.ROOT);
    }

    @Override
    public void vorbereiten() {
        long jetzt = System.currentTimeMillis();
        float schritt = letzterFrame == 0 ? 1f : Math.min(1f, (jetzt - letzterFrame) / BLENDE_MS);
        letzterFrame = jetzt;
        for (int i = 0; i < FELDER.length; i++) {
            float ziel = taste(i).isDown() ? 1f : 0f;
            stand[i] = stand[i] < ziel ? Math.min(ziel, stand[i] + schritt) : Math.max(ziel, stand[i] - schritt);
        }
    }

    @Override
    public float breite(HudZeichner z) {
        return 53;
    }

    @Override
    public float hoehe() {
        return leertaste.get() ? 61 : 53;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        int anzahl = leertaste.get() ? FELDER.length : FELDER.length - 1;
        for (int i = 0; i < anzahl; i++) {
            int[] f = FELDER[i];
            float t = stand[i];
            float fx = x + f[0];
            float fy = y + f[1];
            z.rect(fx, fy, f[2], f[3], ColorUtil.lerp(HINTERGRUND, HINTERGRUND_GEDRUECKT, t));
            int schrift = ColorUtil.lerp(TEXT, TEXT_GEDRUECKT, t);
            if (i == 6) {
                // Die Leertaste zeigt wie bei AxolotlClient nur einen Strich.
                z.rect(fx + 4, fy + 3, f[2] - 8, 1, schrift);
                continue;
            }
            String text = beschriftung(i);
            z.text(text, fx + (f[2] - z.breite(text)) / 2.0f, fy + (f[3] - 8) / 2.0f + 1, schrift, false);
        }
    }
}
