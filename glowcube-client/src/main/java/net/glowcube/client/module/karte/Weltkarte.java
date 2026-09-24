package net.glowcube.client.module.karte;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.karte.KartenZeichner;
import net.glowcube.client.util.Theme;
import net.minecraft.client.Minecraft;

/**
 * Die grosse Karte: fuellt fast den ganzen Bildschirm, solange sie an ist
 * (Taste M). Alles Gelaende, das der Client geladen hat, mit Spielern,
 * Wegpunkten samt Namen und dem eigenen Pfeil. Baut sich in wenigen Bildern
 * auf, weil je Bild nur ein Teil der Saeulen neu gelesen wird.
 */
public final class Weltkarte extends HudModul {
    private static final int TASTE_M = 77;

    private final NumberSetting massstab = register(new NumberSetting("Massstab",
            "Bloecke je Kartenpunkt - groesser zeigt mehr (nur so weit, wie geladen ist)", 2, 1, 8, 1));
    private final NumberSetting zelle = register(new NumberSetting("Aufloesung",
            "Bildpunkte je Kartenpunkt", 2, 1, 4, 1));
    private final BooleanSetting monster = register(new BooleanSetting("Monster", "Monster zeigen", true));
    private final BooleanSetting tiere = register(new BooleanSetting("Tiere", "Tiere zeigen", true));

    public Weltkarte() {
        super("Weltkarte", "Grosse Karte ueber den ganzen Bildschirm (Taste M)", false, Category.KARTE);
        setKey(TASTE_M);
    }

    @Override
    public boolean frei() {
        return true;
    }

    @Override
    public float breite(HudZeichner z) {
        return 0;
    }

    @Override
    public float hoehe() {
        return 0;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
    }

    @Override
    public void zeichnenFrei(HudZeichner z, int bildBreite, int bildHoehe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int rand = 30;
        int w = bildBreite - 2 * rand;
        int h = bildHoehe - 2 * rand - 12;
        if (w < 50 || h < 50) {
            return;
        }
        z.rundRect(rand - 4, rand - 16, w + 8, h + 32, 6, 0xE0101420);
        z.text("Weltkarte", rand, rand - 12, Theme.ACCENT_A, true);
        String info = mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ()
                + "   Massstab 1:" + massstab.getInt() + "   M schliesst";
        z.text(info, rand + w - z.breite(info), rand - 12, 0xFFB0B8C8, true);
        KartenZeichner.zeichnen(z, rand, rand, w, h, zelle.getInt(), massstab.getInt(),
                new KartenZeichner.Inhalt(true, monster.get(), tiere.get(), true, true));
        z.text("Wegpunkte: /wp add <name>   Teleport: /wp tp <name>", rand, rand + h + 4, 0xFF8890A0, true);
    }
}
