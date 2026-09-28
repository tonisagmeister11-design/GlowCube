package de.gtacity.item;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class AmmoItem extends Item {
    public final GunType.AmmoKind kind;

    public AmmoItem(GunType.AmmoKind kind, Properties properties) {
        super(properties.stacksTo(kind == GunType.AmmoKind.ROCKET ? 16 : 99));
        this.kind = kind;
    }

    /** Counts the rounds of the given kind in the player's inventory. */
    public static int count(Player player, GunType.AmmoKind kind) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof AmmoItem ammo && ammo.kind == kind) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Removes up to {@code amount} rounds and returns how many were taken. */
    public static int take(Player player, GunType.AmmoKind kind, int amount) {
        Inventory inv = player.getInventory();
        int taken = 0;
        for (int i = 0; i < inv.getContainerSize() && taken < amount; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof AmmoItem ammo && ammo.kind == kind) {
                int n = Math.min(stack.getCount(), amount - taken);
                stack.shrink(n);
                taken += n;
            }
        }
        return taken;
    }
}
