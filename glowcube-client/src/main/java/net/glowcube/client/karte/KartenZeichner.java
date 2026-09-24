package net.glowcube.client.karte;

import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.module.karte.Wegpunkte;
import net.glowcube.client.util.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/**
 * Zeichnet einen Kartenausschnitt mit Norden oben: Gelaende aus dem
 * {@link KartenSpeicher}, darueber Wegpunkte, Wesen und der eigene Pfeil.
 * Gleiche Farben in einer Zeile werden zu einem Rechteck zusammengefasst -
 * so bleibt es auch bei grossen Karten bei wenigen Zeichenbefehlen.
 */
public final class KartenZeichner {
    private KartenZeichner() {
    }

    /** Was ausser dem Gelaende auf die Karte soll. */
    public record Inhalt(boolean spieler, boolean monster, boolean tiere, boolean wegpunkte, boolean namen) {
    }

    /**
     * @param zelle     Bildpunkte je Kartenzelle
     * @param massstab  Bloecke je Kartenzelle
     */
    public static void zeichnen(HudZeichner z, float x, float y, int breite, int hoehe, int zelle, int massstab,
                                Inhalt inhalt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        boolean hoehle = KartenSpeicher.unterDecke();
        KartenSpeicher.neuesBild(hoehle, mc.player.getBlockY());
        z.rect(x, y, breite, hoehe, 0xFF0B0E16);

        int spalten = breite / zelle;
        int zeilen = hoehe / zelle;
        double mx = mc.player.getX();
        double mz = mc.player.getZ();
        int startX = (int) Math.floor(mx) - spalten * massstab / 2;
        int startZ = (int) Math.floor(mz) - zeilen * massstab / 2;

        for (int zeile = 0; zeile < zeilen; zeile++) {
            int bz = startZ + zeile * massstab;
            int laufFarbe = -2;
            int laufStart = 0;
            for (int spalte = 0; spalte <= spalten; spalte++) {
                int farbe = spalte == spalten ? -3 : KartenSpeicher.farbe(startX + spalte * massstab, bz);
                if (farbe != laufFarbe) {
                    if (laufFarbe >= 0) {
                        z.rect(x + laufStart * zelle, y + zeile * zelle, (spalte - laufStart) * zelle, zelle,
                                0xFF000000 | laufFarbe);
                    }
                    laufFarbe = farbe;
                    laufStart = spalte;
                }
            }
        }

        // Umrechnung Welt -> Bildschirm.
        double proBlock = (double) zelle / massstab;
        float mitteX = x + breite / 2.0f;
        float mitteY = y + hoehe / 2.0f;

        if (inhalt.wegpunkte()) {
            for (Wegpunkte.Punkt p : Wegpunkte.hier()) {
                float px = (float) (mitteX + (p.x() + 0.5 - mx) * proBlock);
                float py = (float) (mitteY + (p.z() + 0.5 - mz) * proBlock);
                px = Math.max(x + 2, Math.min(x + breite - 3, px));
                py = Math.max(y + 2, Math.min(y + hoehe - 3, py));
                int f = Wegpunkte.farbe(p.name());
                z.rect(px - 2, py - 2, 5, 5, 0xFF000000);
                z.rect(px - 1.5f, py - 1.5f, 4, 4, f);
                if (inhalt.namen()) {
                    z.text(p.name(), px + 4, py - 4, f, true);
                }
            }
        }

        if (mc.level != null) {
            for (Entity wesen : mc.level.entitiesForRendering()) {
                if (wesen == mc.player || !(wesen instanceof LivingEntity)) {
                    continue;
                }
                int farbe;
                if (wesen instanceof Player) {
                    if (!inhalt.spieler()) {
                        continue;
                    }
                    farbe = 0xFFFFFFFF;
                } else if (wesen instanceof Monster) {
                    if (!inhalt.monster()) {
                        continue;
                    }
                    farbe = 0xFFFF4A4A;
                } else if (wesen instanceof Animal) {
                    if (!inhalt.tiere()) {
                        continue;
                    }
                    farbe = 0xFF6BE08A;
                } else {
                    continue;
                }
                float px = (float) (mitteX + (wesen.getX() - mx) * proBlock);
                float py = (float) (mitteY + (wesen.getZ() - mz) * proBlock);
                if (px < x || py < y || px > x + breite - 2 || py > y + hoehe - 2) {
                    continue;
                }
                z.rect(px - 1, py - 1, 3, 3, 0xFF000000);
                z.rect(px - 0.5f, py - 0.5f, 2, 2, farbe);
                if (inhalt.namen() && wesen instanceof Player) {
                    z.text(wesen.getName().getString(), px + 3, py - 4, farbe, true);
                }
            }
        }

        // Der eigene Pfeil: ein Punkt und drei Punkte in Blickrichtung.
        double rad = Math.toRadians(mc.player.getYRot());
        float dx = (float) -Math.sin(rad);
        float dz = (float) Math.cos(rad);
        z.rect(mitteX - 2, mitteY - 2, 5, 5, 0xFF000000);
        z.rect(mitteX - 1.5f, mitteY - 1.5f, 4, 4, Theme.ACCENT_A);
        for (int i = 1; i <= 3; i++) {
            z.rect(mitteX + dx * (2 + i * 2) - 1, mitteY + dz * (2 + i * 2) - 1, 2, 2, 0xFFFFFFFF);
        }
        z.text("N", mitteX - z.breite("N") / 2.0f, y + 2, 0xFFFF5F6D, true);
        if (hoehle) {
            z.text("Hoehle", x + 2, y + hoehe - 10, 0xFFB0B8C8, true);
        }
    }
}
