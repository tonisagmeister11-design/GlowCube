package net.glowcube.client.module.hud;

import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.minecraft.world.item.ItemStack;

/**
 * Das Inventar ohne Hotbar - aus AxolotlClient ({@code InventoryHud}): die
 * drei Reihen zu je neun Plaetzen, so wie sie im Inventar stehen.
 */
public final class InventarHud extends HudModul {
    private static final int FELD = 18;

    public InventarHud() {
        super("Inventar", "Zeigt dein Inventar dauerhaft am Bildschirmrand", false);
    }

    @Override
    public float breite(HudZeichner z) {
        return 9 * FELD + 2;
    }

    @Override
    public float hoehe() {
        return 3 * FELD + 2;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        z.rundRect(x, y, breite(z), hoehe(), 3, 0x99101420);
        if (mc.player == null) {
            return;
        }
        for (int reihe = 0; reihe < 3; reihe++) {
            for (int spalte = 0; spalte < 9; spalte++) {
                int fx = Math.round(x) + 1 + spalte * FELD;
                int fy = Math.round(y) + 1 + reihe * FELD;
                z.rect(fx + 1, fy + 1, FELD - 2, FELD - 2, 0x33FFFFFF);
                ItemStack s = mc.player.getInventory().getItem(9 + reihe * 9 + spalte);
                if (!s.isEmpty()) {
                    z.gegenstand(s, fx + 1, fy + 1);
                }
            }
        }
    }
}
