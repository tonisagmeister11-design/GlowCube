package net.glowcube.client.module.hud;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.hud.Meldungen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Haltbarkeitswarnung: warnt, bevor Werkzeug oder Ruestung zerbricht, und
 * zeigt die knappen Stuecke im HUD. Mit "Werkzeug schuetzen" wird ein
 * Werkzeug kurz vor dem Zerbrechen nicht mehr zum Abbauen benutzt - so geht
 * die verzauberte Spitzhacke nicht aus Versehen kaputt.
 */
public final class Haltbarkeit extends HudModul {
    private static final EquipmentSlot[] PLAETZE = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final NumberSetting schwelle = register(new NumberSetting("Schwelle",
            "Ab so viel Prozent Rest wird gewarnt", 10, 1, 50, 1));
    private final BooleanSetting schuetzen = register(new BooleanSetting("Werkzeug schuetzen",
            "Mit einem fast kaputten Werkzeug nicht mehr abbauen", true));
    private final NumberSetting schutzRest = register(new NumberSetting("Schutz ab",
            "So viele Nutzungen bleiben uebrig, dann wird gestoppt", 5, 1, 50, 1));

    private final List<String> zeilen = new ArrayList<>();
    private final Set<String> gewarnt = new HashSet<>();

    public Haltbarkeit() {
        super("Haltbarkeit", "Warnt, bevor Werkzeug oder Ruestung zerbricht", true, Category.PVP_HUD);
    }

    private static int rest(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    @Override
    public void onTick() {
        Set<String> jetzt = new HashSet<>();
        for (EquipmentSlot platz : PLAETZE) {
            ItemStack stack = player().getItemBySlot(platz);
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
                continue;
            }
            int prozent = rest(stack) * 100 / stack.getMaxDamage();
            if (prozent <= schwelle.get()) {
                String schluessel = platz.name() + ":" + stack.getHoverName().getString();
                jetzt.add(schluessel);
                if (!gewarnt.contains(schluessel)) {
                    Meldungen.melden(stack.getHoverName().getString() + " fast kaputt (" + prozent + "%)", 0xFFFFC53D);
                }
            }
        }
        gewarnt.retainAll(jetzt);
        gewarnt.addAll(jetzt);
    }

    @Override
    public boolean onBlockBreak(BlockPos pos) {
        if (!schuetzen.get()) {
            return false;
        }
        ItemStack hand = player().getMainHandItem();
        if (hand.isDamageableItem() && hand.getMaxDamage() > 0 && rest(hand) <= schutzRest.getInt()) {
            Meldungen.melden(hand.getHoverName().getString() + " geschuetzt - nicht mehr abbauen", 0xFFFF5F6D);
            return true;
        }
        return false;
    }

    @Override
    public void vorbereiten() {
        zeilen.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        for (EquipmentSlot platz : PLAETZE) {
            ItemStack stack = mc.player.getItemBySlot(platz);
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
                continue;
            }
            int prozent = rest(stack) * 100 / stack.getMaxDamage();
            if (prozent <= schwelle.get()) {
                zeilen.add("⚠ " + stack.getHoverName().getString() + " " + prozent + "%");
            }
        }
    }

    @Override
    public float breite(HudZeichner z) {
        float w = 40;
        for (String zeile : zeilen) {
            w = Math.max(w, z.breite(zeile) + 8);
        }
        return w;
    }

    @Override
    public float hoehe() {
        return zeilen.isEmpty() ? 0 : zeilen.size() * 10 + 4;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        z.rundRect(x, y, breite(z), hoehe(), 3, 0x99101420);
        for (int i = 0; i < zeilen.size(); i++) {
            z.text(zeilen.get(i), x + 4, y + 3 + i * 10, 0xFFFFC53D, true);
        }
    }
}
