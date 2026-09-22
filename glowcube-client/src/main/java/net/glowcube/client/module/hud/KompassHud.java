package net.glowcube.client.module.hud;

import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.util.Theme;

/**
 * Ein Kompassband - nach AxolotlClients {@code CompassHud}: die
 * Himmelsrichtungen laufen am Blick vorbei, alle 15 Grad ein Strich, in der
 * Mitte die Marke und darunter die Gradzahl.
 */
public final class KompassHud extends HudModul {
    private static final float BREITE = 140;
    private static final float PIXEL_JE_GRAD = 1.2f;
    private static final String[] RICHTUNGEN = {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};

    private final BooleanSetting grad = register(
            new BooleanSetting("Gradzahl", "Die Blickrichtung in Grad darunter schreiben", true));

    private float blick;

    public KompassHud() {
        super("Kompass", "Zeigt ein Kompassband mit Himmelsrichtungen", false);
    }

    @Override
    public void vorbereiten() {
        if (mc.player != null) {
            // Minecraft zaehlt ab Sued; +180 macht Nord zu 0 Grad.
            blick = ((mc.player.getYRot() + 180) % 360 + 360) % 360;
        }
    }

    @Override
    public float breite(HudZeichner z) {
        return BREITE;
    }

    @Override
    public float hoehe() {
        return grad.get() ? 24 : 14;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        z.rundRect(x, y, BREITE, 14, 3, 0x99101420);
        float mitte = x + BREITE / 2;
        float halb = BREITE / 2 / PIXEL_JE_GRAD;
        int start = (int) Math.floor((blick - halb) / 15) * 15;
        for (int winkel = start; winkel <= blick + halb; winkel += 15) {
            float px = mitte + (winkel - blick) * PIXEL_JE_GRAD;
            int norm = ((winkel % 360) + 360) % 360;
            if (norm % 45 == 0) {
                String name = RICHTUNGEN[norm / 45];
                int farbe = norm % 90 == 0 ? Theme.TEXT : Theme.TEXT_DIM;
                float tx = px - z.breite(name) / 2.0f;
                if (tx >= x + 1 && tx + z.breite(name) <= x + BREITE - 1) {
                    z.text(name, tx, y + 3, farbe, true);
                }
            } else if (px >= x + 1 && px <= x + BREITE - 2) {
                z.rect(px, y + 5, 1, 4, Theme.TEXT_FAINT);
            }
        }
        z.rect(mitte, y, 1, 2, Theme.ACCENT_A);
        z.rect(mitte, y + 12, 1, 2, Theme.ACCENT_A);
        if (grad.get()) {
            String text = Math.round(blick) % 360 + "°";
            z.text(text, mitte - z.breite(text) / 2.0f, y + 15, Theme.ACCENT_A, true);
        }
    }
}
