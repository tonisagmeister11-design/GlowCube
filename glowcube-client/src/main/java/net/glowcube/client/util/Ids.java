package net.glowcube.client.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Registernamen ("diamond_chestplate", "end_crystal") statt fester
 * Konstanten. Die Konstanten-Felder aendern sich zwischen den Fassungen
 * (26.x hat manche {@code EntityType}-Felder nicht mehr), die Namen nicht.
 */
public final class Ids {
    private static Map<String, Item> itemsNachName;

    private Ids() {
    }

    public static String item(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    public static String item(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    public static String block(BlockState zustand) {
        return BuiltInRegistries.BLOCK.getKey(zustand.getBlock()).getPath();
    }

    public static String wesen(Entity wesen) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(wesen.getType()).getPath();
    }

    /** Das Item zu einem Namen ("diamond" oder "minecraft:diamond"), oder null. */
    public static Item itemNachName(String name) {
        if (itemsNachName == null) {
            Map<String, Item> karte = new HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                karte.put(item(item), item);
            }
            itemsNachName = karte;
        }
        String kurz = name.trim().toLowerCase(java.util.Locale.ROOT);
        int doppelpunkt = kurz.indexOf(':');
        if (doppelpunkt >= 0) {
            kurz = kurz.substring(doppelpunkt + 1);
        }
        return itemsNachName.get(kurz);
    }
}
