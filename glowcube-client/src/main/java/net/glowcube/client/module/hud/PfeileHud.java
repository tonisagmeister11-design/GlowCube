package net.glowcube.client.module.hud;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.util.Theme;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Wie viele Pfeile du dabeihast - aus AxolotlClient ({@code ArrowHud}):
 * Pfeil-Symbol und Anzahl. Gezaehlt werden normale, Spektral- und
 * Trankpfeile im ganzen Inventar.
 */
public final class PfeileHud extends HudModul {
    private final BooleanSetting nurMitBogen = register(
            new BooleanSetting("Nur mit Bogen", "Nur zeigen, wenn Bogen oder Armbrust in der Hand ist", false));

    /** Erst beim ersten Zeichnen anlegen - ab 26.x sind die Item-Daten beim Mod-Start noch nicht gebunden. */
    private static ItemStack symbol;
    private int anzahl;
    private boolean sichtbar;

    public PfeileHud() {
        super("Pfeile", "Zeigt, wie viele Pfeile du dabeihast", true, Category.PVP_HUD);
    }

    @Override
    public void vorbereiten() {
        anzahl = 0;
        sichtbar = mc.player != null;
        if (!sichtbar) {
            return;
        }
        if (nurMitBogen.get()) {
            ItemStack hand = mc.player.getMainHandItem();
            sichtbar = hand.is(Items.BOW) || hand.is(Items.CROSSBOW);
        }
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.ARROW) || s.is(Items.SPECTRAL_ARROW) || s.is(Items.TIPPED_ARROW)) {
                anzahl += s.getCount();
            }
        }
    }

    @Override
    public float breite(HudZeichner z) {
        return 24 + z.breite(String.valueOf(anzahl));
    }

    @Override
    public float hoehe() {
        return sichtbar ? 20 : 0;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        z.rundRect(x, y, breite(z), 20, 3, 0x99101420);
        if (symbol == null) {
            symbol = new ItemStack(Items.ARROW);
        }
        z.gegenstand(symbol, Math.round(x) + 2, Math.round(y) + 2);
        z.text(String.valueOf(anzahl), x + 20, y + 6, anzahl > 0 ? Theme.TEXT : 0xFFFF5F6D, true);
    }
}
