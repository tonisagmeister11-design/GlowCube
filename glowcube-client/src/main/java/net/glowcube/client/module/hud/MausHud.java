package net.glowcube.client.module.hud;

import net.glowcube.client.core.Category;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.util.Theme;

/**
 * Wie sich die Maus gerade bewegt - nach AxolotlClients
 * {@code MouseMovementHud}: ein Kaestchen, in dem ein Punkt der Bewegung
 * folgt und langsam zur Mitte zurueckgleitet.
 */
public final class MausHud extends HudModul {
    private static final float GROESSE = 31;

    private float letzteGier = Float.NaN;
    private float letzteNeigung;
    private float px;
    private float py;

    public MausHud() {
        super("Mausbewegung", "Zeigt, wie du die Maus gerade bewegst", false, Category.PVP_HUD);
    }

    @Override
    public void vorbereiten() {
        if (mc.player == null) {
            return;
        }
        float gier = mc.player.getYRot();
        float neigung = mc.player.getXRot();
        if (!Float.isNaN(letzteGier)) {
            px += (gier - letzteGier) * 0.5f;
            py += (neigung - letzteNeigung) * 0.5f;
        }
        letzteGier = gier;
        letzteNeigung = neigung;
        float grenze = GROESSE / 2 - 2;
        px = Math.max(-grenze, Math.min(grenze, px)) * 0.85f;
        py = Math.max(-grenze, Math.min(grenze, py)) * 0.85f;
    }

    @Override
    public float breite(HudZeichner z) {
        return GROESSE;
    }

    @Override
    public float hoehe() {
        return GROESSE;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        z.rundRect(x, y, GROESSE, GROESSE, 3, 0x99101420);
        float mx = x + GROESSE / 2;
        float my = y + GROESSE / 2;
        z.rect(mx, y + 3, 1, GROESSE - 6, 0x33FFFFFF);
        z.rect(x + 3, my, GROESSE - 6, 1, 0x33FFFFFF);
        z.rect(mx + px - 1, my + py - 1, 3, 3, Theme.ACCENT_A);
    }
}
