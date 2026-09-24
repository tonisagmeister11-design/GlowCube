package de.glowcube.claudeai.task;

import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;

import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.world.Blocks;
import de.glowcube.claudeai.world.Mats;

/** Erntet reife Pflanzen und setzt gleich neu an. */
public final class HarvestTask extends Task {

    private final Predicate<Material> crops;
    private final Predicate<Material> item;
    private final int wanted;
    private int baseline = -1;
    private int harvested;
    private Block target;
    private int ticks;

    /** wanted < 0: alles Reife in der Naehe ernten. */
    public HarvestTask(Predicate<Material> crops, Predicate<Material> item, int wanted) {
        this.crops = crops;
        this.item = item;
        this.wanted = wanted;
    }

    public static boolean isCrop(Material m) {
        String n = Mats.n(m);
        return n.equals("WHEAT") || n.equals("CARROTS") || n.equals("POTATOES") || n.equals("BEETROOTS")
                || n.equals("NETHER_WART") || n.equals("TORCHFLOWER_CROP") || n.equals("PITCHER_CROP");
    }

    private static Material seedFor(Material crop) {
        return switch (Mats.n(crop)) {
            case "WHEAT" -> Mats.get("WHEAT_SEEDS");
            case "CARROTS" -> Mats.get("CARROT");
            case "POTATOES" -> Mats.get("POTATO");
            case "BEETROOTS" -> Mats.get("BEETROOT_SEEDS");
            case "NETHER_WART" -> Mats.get("NETHER_WART");
            default -> null;
        };
    }

    @Override
    public String label() {
        return "ernte das Feld ab";
    }

    @Override
    public void start(Npc npc) {
        if (baseline < 0 && item != null) baseline = npc.count(item);
        target = null;
        ticks = 0;
    }

    @Override
    public Status tick(Npc npc) {
        if (wanted > 0 && item != null && npc.count(item) - baseline >= wanted) return Status.DONE;
        if (++ticks > 20 * 300) return Status.DONE;
        if (target == null || !ripe(target)) {
            target = findRipe(npc);
            if (target == null) {
                if (harvested > 0) return Status.DONE;
                return fail("Hier ist gerade nichts reif zum Ernten.");
            }
        }
        Location c = target.getLocation().add(0.5, 0.5, 0.5);
        if (npc.location().distance(c) > 3.2) {
            npc.mover().go(c, 2.5, false, false);
            if (npc.mover().status() == de.glowcube.claudeai.move.Mover.Status.FAILED) target = null;
            return Status.RUNNING;
        }
        npc.mover().stop();
        npc.lookAtBlock(target);
        Material crop = target.getType();
        if (!npc.breakBlock(target) || !target.getType().isAir()) {
            target = null;
            return Status.RUNNING;
        }
        harvested++;
        npc.pickupNear(c, 2.5);
        Material seed = seedFor(crop);
        if (seed != null && Blocks.floor(target.getRelative(0, -1, 0)) && npc.take(seed, 1) == 1) {
            npc.placeBlock(target, crop.createBlockData());
        }
        target = null;
        return Status.RUNNING;
    }

    private boolean ripe(Block b) {
        if (!crops.test(b.getType())) return false;
        return b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge();
    }

    private Block findRipe(Npc npc) {
        Location me = npc.location();
        Block best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -24; dx <= 24; dx++) {
            for (int dz = -24; dz <= 24; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    Block b = Blocks.at(me.getWorld(), me.getBlockX() + dx, me.getBlockY() + dy, me.getBlockZ() + dz);
                    if (b == null || !ripe(b)) continue;
                    double d = dx * dx + dz * dz + dy * dy * 3;
                    if (d < bestD) {
                        bestD = d;
                        best = b;
                    }
                }
            }
        }
        return best;
    }

    @Override
    public void stop(Npc npc) {
        npc.mover().stop();
    }
}
