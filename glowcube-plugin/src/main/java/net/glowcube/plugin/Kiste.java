package net.glowcube.plugin;

import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.function.Predicate;

/** Die Sammelkiste: volle Agenten legen ihre Beute hier ab (Doppelkisten zaehlen ganz). */
final class Kiste {
    private Kiste() {
    }

    static boolean istKiste(Block b) {
        return b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4) && b.getState() instanceof Container;
    }

    /** @return umgelagerte Items; -1, wenn es keine Kiste (mehr) ist */
    static int einlagern(Block b, Inventory lager, Predicate<ItemStack> behalten) {
        if (!(b.getState() instanceof Container behaelter)) {
            return -1;
        }
        Inventory ziel = behaelter.getInventory();
        int bewegt = 0;
        for (int i = 0; i < lager.getSize(); i++) {
            ItemStack stapel = lager.getItem(i);
            if (stapel == null || stapel.getType().isAir() || behalten.test(stapel)) {
                continue;
            }
            int vorher = stapel.getAmount();
            Map<Integer, ItemStack> rest = ziel.addItem(stapel.clone());
            int uebrig = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
            bewegt += vorher - uebrig;
            if (uebrig == 0) {
                lager.setItem(i, null);
            } else {
                stapel.setAmount(uebrig);
                lager.setItem(i, stapel);
            }
        }
        return bewegt;
    }

    static int anzahl(Inventory lager) {
        int n = 0;
        for (ItemStack s : lager.getContents()) {
            if (s != null) {
                n += s.getAmount();
            }
        }
        return n;
    }
}
