package net.glowcube.client.hud;

import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.util.Theme;

/**
 * Eine Anzeige aus einer Textzeile - nach AxolotlClients
 * {@code SimpleTextHudEntry}: ein Kasten von mindestens 53 x 13 Pixeln, der
 * Text mittig darin, Kasten und Schatten abschaltbar.
 */
public abstract class TextHudModul extends HudModul {
    private static final float MIN_BREITE = 53;
    private static final float HOEHE = 13;

    private final BooleanSetting hintergrund = register(
            new BooleanSetting("Hintergrund", "Dunkler Kasten hinter dem Text", true));
    private final BooleanSetting schatten = register(
            new BooleanSetting("Schatten", "Text mit Schatten", true));

    private String text = "";

    protected TextHudModul(String name, String beschreibung) {
        super(name, beschreibung, false);
    }

    protected TextHudModul(String name, String beschreibung, boolean rechts) {
        super(name, beschreibung, rechts);
    }

    /** Was gerade angezeigt wird. Wird einmal je Frame gefragt. */
    protected abstract String text();

    @Override
    public void vorbereiten() {
        String neu = text();
        text = neu == null ? "" : neu;
    }

    @Override
    public float breite(HudZeichner z) {
        return Math.max(MIN_BREITE, z.breite(text) + 8);
    }

    /** Leerer Text heisst: die Anzeige hat gerade nichts zu sagen und nimmt keinen Platz ein. */
    @Override
    public float hoehe() {
        return text.isEmpty() ? 0 : HOEHE;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        float w = breite(z);
        if (hintergrund.get()) {
            z.rundRect(x, y, w, HOEHE, 3, 0x99101420);
        }
        z.text(text, x + (w - z.breite(text)) / 2.0f, y + 3, Theme.TEXT, schatten.get());
    }
}
