package net.glowcube.client.hud;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Module;

/**
 * Stapelt die eingeschalteten HUD-Anzeigen an den Bildschirmrand: jede
 * Anzeige mit "Seite: Links" unter die vorige links, jede mit "Rechts" unter
 * die vorige rechts. Die Reihenfolge ist die der Anmeldung im ModuleManager.
 */
public final class HudAnzeigen {
    private static final float RAND = 6;
    private static final float ABSTAND = 3;

    private HudAnzeigen() {
    }

    /**
     * @param bildBreite  skalierte Breite des Bildschirms
     * @param linksStart  erste freie Zeile links (unter dem Wasserzeichen)
     * @param rechtsStart erste freie Zeile rechts (unter der Modulliste)
     */
    public static void zeichnen(HudZeichner z, int bildBreite, int bildHoehe, float linksStart, float rechtsStart) {
        float links = linksStart;
        float rechts = rechtsStart;
        for (Module module : GlowCubeClient.modules().all()) {
            if (!(module instanceof HudModul anzeige) || !anzeige.isEnabled()) {
                continue;
            }
            try {
                anzeige.vorbereiten();
                if (anzeige.frei()) {
                    anzeige.zeichnenFrei(z, bildBreite, bildHoehe);
                    continue;
                }
                float w = anzeige.breite(z);
                float h = anzeige.hoehe();
                if (h <= 0) {
                    continue;
                }
                if (anzeige.rechts()) {
                    anzeige.zeichnen(z, bildBreite - RAND - w, rechts);
                    rechts += h + ABSTAND;
                } else {
                    anzeige.zeichnen(z, RAND, links);
                    links += h + ABSTAND;
                }
            } catch (Throwable fehler) {
                // Eine kaputte Anzeige darf nie das Spiel mitreissen.
                anzeige.setEnabled(false);
                GlowCubeClient.LOGGER.warn("GlowCube: HUD-Anzeige {} abgeschaltet", anzeige.name(), fehler);
            }
        }
    }
}
