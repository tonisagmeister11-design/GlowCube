package net.glowcube.client.module.player;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Ids;
import net.glowcube.client.util.InvUtils;
import net.glowcube.client.util.SlotUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * AutoArmor: zieht die beste Ruestung an, die im Inventar liegt.
 *
 * <p>"Beste" heisst hier: zuerst das Material (Netherit vor Diamant vor
 * Eisen ...), dann verzaubert vor unverzaubert, dann die Haltbarkeit. Eine
 * angelegte Elytra bleibt auf Wunsch, wo sie ist.
 */
public final class AutoArmor extends Module {
    /** Rucksack-Index des Ruestungsplatzes: 36 Stiefel ... 39 Helm. */
    private static final EquipmentSlot[] PLAETZE = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final String[] ENDUNGEN = {"_boots", "_leggings", "_chestplate", "_helmet"};

    private final NumberSetting pause = register(new NumberSetting("Pause",
            "Ticks zwischen zwei Wechseln", 3, 0, 20, 1));
    private final BooleanSetting elytra = register(new BooleanSetting("Elytra behalten",
            "Eine angelegte Elytra nicht durch einen Brustpanzer ersetzen", true));

    private int warten;

    public AutoArmor() {
        super("AutoArmor", "Zieht von selbst die beste Ruestung an", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (warten > 0) {
            warten--;
            return;
        }
        // Nur im eigenen Inventar - in einer offenen Kiste stimmen die Plaetze nicht.
        if (player().containerMenu != player().inventoryMenu) {
            return;
        }
        for (int i = 0; i < PLAETZE.length; i++) {
            ItemStack jetzt = player().getItemBySlot(PLAETZE[i]);
            if (elytra.get() && PLAETZE[i] == EquipmentSlot.CHEST && Ids.item(jetzt).equals("elytra")) {
                continue;
            }
            int bester = wert(jetzt, i);
            int platz = -1;
            for (int j = 0; j <= SlotUtils.MAIN_END; j++) {
                int w = wert(player().getInventory().getItem(j), i);
                if (w > bester) {
                    bester = w;
                    platz = j;
                }
            }
            if (platz >= 0) {
                InvUtils.verschieben().von(platz).nachRuestung(SlotUtils.ARMOR_START + i);
                warten = pause.getInt();
                return;
            }
        }
    }

    /** Wie gut ein Stueck fuer diesen Platz ist; -1 heisst: passt nicht oder leer. */
    static int wert(ItemStack stack, int platz) {
        String id = Ids.item(stack);
        if (id.isEmpty()) {
            return -1;
        }
        boolean passt = id.endsWith(ENDUNGEN[platz]) || (platz == 3 && id.equals("turtle_helmet"));
        if (!passt) {
            return -1;
        }
        int material;
        if (id.startsWith("netherite")) {
            material = 7;
        } else if (id.startsWith("diamond")) {
            material = 6;
        } else if (id.startsWith("iron")) {
            material = 5;
        } else if (id.startsWith("turtle")) {
            material = 4;
        } else if (id.startsWith("chainmail") || id.startsWith("copper")) {
            material = 3;
        } else if (id.startsWith("golden")) {
            material = 2;
        } else {
            material = 1;
        }
        int wert = material * 100 + (stack.isEnchanted() ? 20 : 0);
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            wert += 10 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
        }
        return wert;
    }
}
