package net.glowcube.client.module.hud;

import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.util.Theme;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Die angelegte Ruestung - aus AxolotlClient ({@code ArmorHud}): Helm bis
 * Stiefel untereinander, darunter was in der Hand liegt, je mit
 * Haltbarkeitsbalken. Auf Wunsch steht die verbleibende Haltbarkeit als Zahl
 * daneben, wie man es von PvP-Clients kennt.
 */
public final class RuestungHud extends HudModul {
    private static final EquipmentSlot[] PLAETZE = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND,
    };
    private static final int ZEILE = 20;

    private final BooleanSetting hand = register(
            new BooleanSetting("Hand", "Auch den Gegenstand in der Hand zeigen", true));
    private final BooleanSetting zahl = register(
            new BooleanSetting("Haltbarkeit als Zahl", "Verbleibende Haltbarkeit neben dem Gegenstand", true));
    private final BooleanSetting hintergrund = register(
            new BooleanSetting("Hintergrund", "Dunkler Kasten hinter der Anzeige", true));

    private final ItemStack[] stapel = new ItemStack[PLAETZE.length];

    public RuestungHud() {
        super("Ruestung", "Zeigt Ruestung und Hand mit Haltbarkeit", true);
    }

    private int anzahl() {
        return hand.get() ? PLAETZE.length : PLAETZE.length - 1;
    }

    @Override
    public void vorbereiten() {
        for (int i = 0; i < PLAETZE.length; i++) {
            stapel[i] = mc.player == null ? ItemStack.EMPTY : mc.player.getItemBySlot(PLAETZE[i]);
        }
    }

    private static String haltbarkeit(ItemStack s) {
        if (s == null || s.isEmpty() || s.getMaxDamage() <= 0) {
            return "";
        }
        return String.valueOf(s.getMaxDamage() - s.getDamageValue());
    }

    private static int farbe(ItemStack s) {
        if (s.getMaxDamage() <= 0) {
            return Theme.TEXT;
        }
        float rest = 1f - (float) s.getDamageValue() / s.getMaxDamage();
        return rest > 0.5f ? 0xFF5FE3A1 : rest > 0.2f ? 0xFFFFC53D : 0xFFFF5F6D;
    }

    @Override
    public float breite(HudZeichner z) {
        float text = 0;
        if (zahl.get()) {
            for (int i = 0; i < anzahl(); i++) {
                text = Math.max(text, z.breite(haltbarkeit(stapel[i])));
            }
        }
        return 20 + (text > 0 ? text + 4 : 0);
    }

    @Override
    public float hoehe() {
        return anzahl() * ZEILE;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        if (hintergrund.get()) {
            z.rundRect(x, y, breite(z), hoehe(), 3, 0x99101420);
        }
        for (int i = 0; i < anzahl(); i++) {
            ItemStack s = stapel[i];
            if (s == null || s.isEmpty()) {
                continue;
            }
            int ix = Math.round(x) + 2;
            int iy = Math.round(y) + i * ZEILE + 2;
            z.gegenstand(s, ix, iy);
            if (zahl.get()) {
                String text = haltbarkeit(s);
                if (!text.isEmpty()) {
                    z.text(text, ix + 20, iy + 4, farbe(s), true);
                }
            }
        }
    }
}
