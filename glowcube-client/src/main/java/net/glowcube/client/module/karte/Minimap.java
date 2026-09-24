package net.glowcube.client.module.karte;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.karte.KartenZeichner;
import net.minecraft.client.Minecraft;

/**
 * Minimap in der Bildschirmecke: Gelaende in Kartenfarben (in Hoehlen und im
 * Nether die Ebene, auf der man steht), Spieler, Monster, Tiere und
 * Wegpunkte. Norden ist oben.
 */
public final class Minimap extends HudModul {
    private final NumberSetting groesse = register(new NumberSetting("Groesse",
            "Kantenlaenge in Bildpunkten", 110, 60, 200, 5));
    private final NumberSetting massstab = register(new NumberSetting("Massstab",
            "Bloecke je Kartenpunkt - groesser zeigt mehr Umgebung", 1, 1, 4, 1));
    private final NumberSetting zelle = register(new NumberSetting("Aufloesung",
            "Bildpunkte je Kartenpunkt - kleiner ist feiner", 2, 1, 4, 1));
    private final BooleanSetting spieler = register(new BooleanSetting("Spieler", "Andere Spieler zeigen", true));
    private final BooleanSetting monster = register(new BooleanSetting("Monster", "Monster zeigen", true));
    private final BooleanSetting tiere = register(new BooleanSetting("Tiere", "Tiere zeigen", false));
    private final BooleanSetting wegpunkte = register(new BooleanSetting("Wegpunkte", "Wegpunkte zeigen", true));
    private final BooleanSetting koordinaten = register(new BooleanSetting("Koordinaten",
            "Eigene Koordinaten unter der Karte", true));

    public Minimap() {
        super("Minimap", "Kleine Karte der Umgebung in der Ecke", true, Category.KARTE);
    }

    @Override
    public float breite(HudZeichner z) {
        return groesse.getInt() + 4;
    }

    @Override
    public float hoehe() {
        return groesse.getInt() + 4 + (koordinaten.get() ? 11 : 0);
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        int g = groesse.getInt();
        z.rundRect(x, y, g + 4, hoehe(), 4, 0xCC101420);
        KartenZeichner.zeichnen(z, x + 2, y + 2, g, g, zelle.getInt(), massstab.getInt(),
                new KartenZeichner.Inhalt(spieler.get(), monster.get(), tiere.get(), wegpunkte.get(), false));
        if (koordinaten.get()) {
            Minecraft mc = Minecraft.getInstance();
            String text = mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
            z.text(text, x + (g + 4 - z.breite(text)) / 2.0f, y + g + 5, 0xFFF2F5FF, true);
        }
    }
}
