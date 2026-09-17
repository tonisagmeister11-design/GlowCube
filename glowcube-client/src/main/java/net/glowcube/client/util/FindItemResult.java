package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), {@code FindItemResult}.
 *
 * <p>Ein Fund im Rucksack: welcher Platz, und wie viele Stueck insgesamt.
 * Die Zahl ist der Grund fuer den eigenen Typ - AutoTotem will nicht nur
 * wissen, ob es noch ein Totem gibt, sondern wie viele.
 */
public record FindItemResult(int slot, int count) {
    public boolean found() {
        return slot != -1;
    }

    /** In welcher Hand das Gefundene schon liegt - null heisst: in keiner. */
    public InteractionHand hand() {
        Minecraft mc = Minecraft.getInstance();
        if (slot == SlotUtils.OFFHAND) {
            return InteractionHand.OFF_HAND;
        }
        if (mc.player != null && slot == mc.player.getInventory().getSelectedSlot()) {
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    public boolean isOffhand() {
        return hand() == InteractionHand.OFF_HAND;
    }

    public boolean isHotbar() {
        return slot >= SlotUtils.HOTBAR_START && slot <= SlotUtils.HOTBAR_END;
    }
}
