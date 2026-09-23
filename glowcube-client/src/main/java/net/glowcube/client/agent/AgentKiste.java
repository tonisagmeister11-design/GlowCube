package net.glowcube.client.agent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Sammelkiste: volle Agenten legen ihre Beute hier ab. Kisten, Faesser
 * und alles andere mit Inventar; bei einer Doppelkiste zaehlt die zweite
 * Haelfte mit.
 */
final class AgentKiste {
    private AgentKiste() {
    }

    static boolean istKiste(ServerLevel welt, BlockPos pos) {
        return welt.isLoaded(pos) && welt.getBlockEntity(pos) instanceof Container;
    }

    private static List<Container> behaelter(ServerLevel welt, BlockPos pos) {
        List<Container> liste = new ArrayList<>();
        BlockEntity be = welt.getBlockEntity(pos);
        if (be instanceof Container c) {
            liste.add(c);
        }
        if (be instanceof ChestBlockEntity) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (welt.getBlockEntity(pos.relative(d)) instanceof ChestBlockEntity nachbar) {
                    liste.add(nachbar);
                }
            }
        }
        return liste;
    }

    /**
     * Legt so viel wie moeglich aus dem Lager in die Kiste - ausser dem, was
     * der Agent behalten will (Bausteine fuer Bruecken).
     *
     * @return wie viele Items umgelagert wurden; -1, wenn es keine Kiste (mehr) ist
     */
    static int einlagern(ServerLevel welt, BlockPos pos, SimpleContainer lager,
                         java.util.function.Predicate<ItemStack> behalten) {
        List<Container> ziele = behaelter(welt, pos);
        if (ziele.isEmpty()) {
            return -1;
        }
        int bewegt = 0;
        for (int i = 0; i < lager.getContainerSize(); i++) {
            ItemStack stapel = lager.getItem(i);
            if (stapel.isEmpty() || behalten.test(stapel)) {
                continue;
            }
            int vorher = stapel.getCount();
            for (Container c : ziele) {
                einfuellen(c, stapel);
                if (stapel.isEmpty()) {
                    break;
                }
            }
            bewegt += vorher - stapel.getCount();
            lager.setItem(i, stapel.isEmpty() ? ItemStack.EMPTY : stapel);
        }
        for (Container c : ziele) {
            c.setChanged();
        }
        lager.setChanged();
        return bewegt;
    }

    /** Erst auf passende Stapel, dann in leere Faecher. */
    private static void einfuellen(Container c, ItemStack stapel) {
        for (int i = 0; i < c.getContainerSize() && !stapel.isEmpty(); i++) {
            ItemStack dort = c.getItem(i);
            if (!dort.isEmpty() && ItemStack.isSameItemSameComponents(dort, stapel)) {
                int platz = Math.min(dort.getMaxStackSize(), c.getMaxStackSize()) - dort.getCount();
                if (platz > 0) {
                    int n = Math.min(platz, stapel.getCount());
                    dort.grow(n);
                    stapel.shrink(n);
                }
            }
        }
        for (int i = 0; i < c.getContainerSize() && !stapel.isEmpty(); i++) {
            if (c.getItem(i).isEmpty() && c.canPlaceItem(i, stapel)) {
                c.setItem(i, stapel.copy());
                stapel.setCount(0);
            }
        }
    }

    /** Wie viele Items im Lager sind. */
    static int anzahl(SimpleContainer lager) {
        int n = 0;
        for (int i = 0; i < lager.getContainerSize(); i++) {
            n += lager.getItem(i).getCount();
        }
        return n;
    }
}
