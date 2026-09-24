package de.glowcube.claudeai.task;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.world.Blocks;
import de.glowcube.claudeai.world.Mats;

/**
 * Grundlage fuer Aufgaben an einer Station (Werkbank, Ofen): hinlaufen, wenn eine in der
 * Naehe steht - sonst die eigene aus dem Inventar hinstellen und danach wieder einpacken.
 */
abstract class StationTask extends Task {

    private final String stationName;
    private final boolean needed;
    protected Block station;
    private boolean placedOwn;
    private boolean ready;

    StationTask(String stationName, boolean needed) {
        this.stationName = stationName;
        this.needed = needed;
    }

    /** Liefert RUNNING solange hingelaufen wird, DONE wenn die Station bereit ist, FAILED wenn es keine gibt. */
    protected Status prepare(Npc npc) {
        if (!needed || ready) return Status.DONE;
        Material type = Mats.get(stationName);
        if (station == null || station.getType() != type) station = find(npc, type);
        if (station == null) {
            if (npc.count(type) == 0) return fail("Ich habe keine " + label(stationName) + ".");
            Block spot = freeSpot(npc);
            if (spot == null) return fail("Hier ist kein Platz fuer meine " + label(stationName) + ".");
            npc.take(type, 1);
            npc.placeBlock(spot, type.createBlockData());
            npc.lookAtBlock(spot);
            station = spot;
            placedOwn = true;
        }
        Location c = station.getLocation().add(0.5, 0.5, 0.5);
        if (npc.location().distance(c) > 3.5) {
            npc.mover().go(c, 3.0, false, true);
            if (npc.mover().status() == de.glowcube.claudeai.move.Mover.Status.FAILED) {
                return fail("Ich komme nicht an die " + label(stationName) + " heran.");
            }
            return Status.RUNNING;
        }
        npc.mover().stop();
        npc.lookAtBlock(station);
        ready = true;
        return Status.DONE;
    }

    /** Eigene Station wieder abbauen und einpacken. */
    protected void packUp(Npc npc) {
        if (placedOwn && station != null && station.getType() == Mats.get(stationName)) {
            npc.breakBlock(station);
        }
        placedOwn = false;
        ready = false;
        station = null;
    }

    @Override
    public void stop(Npc npc) {
        packUp(npc);
        npc.mover().stop();
    }

    private static String label(String station) {
        return station.equals("FURNACE") ? "Ofen" : "Werkbank";
    }

    private static Block find(Npc npc, Material type) {
        Location me = npc.location();
        Block best = null;
        double bestD = 24 * 24;
        for (int dx = -24; dx <= 24; dx++) {
            for (int dz = -24; dz <= 24; dz++) {
                for (int dy = -6; dy <= 6; dy++) {
                    Block b = Blocks.at(me.getWorld(), me.getBlockX() + dx, me.getBlockY() + dy, me.getBlockZ() + dz);
                    if (b == null || b.getType() != type) continue;
                    double d = dx * dx + dz * dz + dy * dy;
                    if (d < bestD) {
                        bestD = d;
                        best = b;
                    }
                }
            }
        }
        return best;
    }

    /** Freier Platz in Reichweite: Luft (oder Gras), fester Boden drunter, nicht dort, wo Claude selbst steht. */
    private static Block freeSpot(Npc npc) {
        Location me = npc.location();
        int bx = me.getBlockX(), by = me.getBlockY(), bz = me.getBlockZ();
        Block best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (dx == 0 && dz == 0) continue;
                    Block b = Blocks.at(me.getWorld(), bx + dx, by + dy, bz + dz);
                    if (b == null) continue;
                    boolean free = b.getType().isAir() || Blocks.isNatural(b.getType()) && b.isPassable() && !b.isLiquid();
                    if (!free || !Blocks.floor(b.getRelative(0, -1, 0))) continue;
                    double d = dx * dx + dz * dz + dy * dy * 1.5;
                    if (d < bestD) {
                        bestD = d;
                        best = b;
                    }
                }
            }
        }
        return best;
    }
}
