package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), {@code SlotUtils}.
 *
 * <p>Der ganze Sinn dieser Klasse: das Spiel kennt zwei Nummerierungen fuer
 * denselben Platz. Im Rucksack ist die Hotbar 0..8; in dem, was an den Server
 * geht, haengt die Nummer davon ab, welches Fenster gerade offen ist - bei
 * einer Truhe mit drei Reihen steht die Hotbar bei 54, bei einer mit sechs
 * bei 81. Wer hier daneben liegt, tauscht einem Fremden Sachen um.
 *
 * <p>Die Tabelle stammt daher eins zu eins von dort.
 */
public final class SlotUtils {
    public static final int HOTBAR_START = 0;
    public static final int HOTBAR_END = 8;
    public static final int MAIN_START = 9;
    public static final int MAIN_END = 35;
    public static final int ARMOR_START = 36;
    public static final int ARMOR_END = 39;
    public static final int OFFHAND = 40;

    private SlotUtils() {
    }

    /** Vom Rucksack-Index zur Nummer, die der Server versteht. -1 = geht nicht. */
    public static int indexToId(int i) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return -1;
        }
        AbstractContainerMenu menu = mc.player.containerMenu;

        if (menu instanceof InventoryMenu) {
            return rucksack(i);
        }
        if (menu instanceof ChestMenu truhe) {
            return behaelter(i, truhe.getRowCount());
        }
        if (menu instanceof ShulkerBoxMenu) {
            return behaelter(i, 3);
        }
        if (menu instanceof CraftingMenu) {
            return versetzt(i, 37, 1);
        }
        if (menu instanceof FurnaceMenu || menu instanceof BlastFurnaceMenu || menu instanceof SmokerMenu) {
            return versetzt(i, 30, 3);
        }
        if (menu instanceof DispenserMenu || menu instanceof CrafterMenu) {
            return i <= HOTBAR_END ? 36 + i : (istHaupt(i) ? i : -1);
        }
        if (menu instanceof EnchantmentMenu || menu instanceof StonecutterMenu) {
            return versetzt(i, 29, 2);
        }
        if (menu instanceof BrewingStandMenu || menu instanceof HopperMenu) {
            return versetzt(i, 32, 5);
        }
        if (menu instanceof MerchantMenu || menu instanceof CartographyTableMenu || menu instanceof GrindstoneMenu) {
            return versetzt(i, 30, 3);
        }
        if (menu instanceof BeaconMenu) {
            return versetzt(i, 28, 1);
        }
        if (menu instanceof LoomMenu || menu instanceof SmithingMenu) {
            return versetzt(i, 31, 4);
        }
        return -1;
    }

    private static int rucksack(int i) {
        if (istHotbar(i)) {
            return 36 + i;
        }
        if (istRuestung(i)) {
            // 36 sind die Stiefel (Menue-Platz 8), 39 der Helm (Platz 5).
            return 8 - (i - ARMOR_START);
        }
        if (i == OFFHAND) {
            return 45;
        }
        return i;
    }

    private static int behaelter(int i, int reihen) {
        if (istHotbar(i)) {
            return (reihen + 3) * 9 + i;
        }
        if (istHaupt(i)) {
            return reihen * 9 + (i - MAIN_START);
        }
        return -1;
    }

    /**
     * Fast alle Fenster folgen demselben Muster: eine Handvoll eigener
     * Plaetze, dann der Rucksack, dann die Hotbar. Nur die beiden Zahlen
     * unterscheiden sich, also stehen sie hier als Parameter statt in einem
     * Dutzend gleicher Methoden.
     */
    private static int versetzt(int i, int hotbar, int haupt) {
        if (istHotbar(i)) {
            return hotbar + i;
        }
        if (istHaupt(i)) {
            return haupt + (i - MAIN_START);
        }
        return -1;
    }

    public static boolean istHotbar(int i) {
        return i >= HOTBAR_START && i <= HOTBAR_END;
    }

    public static boolean istHaupt(int i) {
        return i >= MAIN_START && i <= MAIN_END;
    }

    public static boolean istRuestung(int i) {
        return i >= ARMOR_START && i <= ARMOR_END;
    }
}
