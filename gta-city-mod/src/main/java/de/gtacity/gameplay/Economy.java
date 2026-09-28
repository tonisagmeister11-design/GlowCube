package de.gtacity.gameplay;

import de.gtacity.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/** The player's wallet, stored as a Fabric data attachment on the player. */
public final class Economy {
    private Economy() {
    }

    public static long get(Player player) {
        Long money = player.getAttached(ModAttachments.MONEY);
        return money == null ? 500L : money;
    }

    public static void set(Player player, long amount) {
        player.setAttached(ModAttachments.MONEY, Math.max(0L, amount));
    }

    public static void add(Player player, long amount) {
        set(player, get(player) + amount);
    }

    public static boolean trySpend(Player player, long amount) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        long money = get(player);
        if (money < amount) {
            return false;
        }
        set(player, money - amount);
        return true;
    }

    public static String format(long amount) {
        return "$" + String.format(Locale.US, "%,d", amount);
    }
}
