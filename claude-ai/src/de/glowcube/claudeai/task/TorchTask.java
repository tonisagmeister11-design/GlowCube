package de.glowcube.claudeai.task;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.world.Blocks;
import de.glowcube.claudeai.world.Mats;

/** Stellt an dunklen Stellen in der Umgebung Fackeln auf, damit keine Monster spawnen. */
public final class TorchTask extends Task {

    private final int radius;
    private final List<Block> placed = new ArrayList<>();
    private Block target;
    private int ticks;

    public TorchTask(int radius) {
        this.radius = radius;
    }

    @Override
    public String label() {
        return "stelle Fackeln auf";
    }

    @Override
    public Status tick(Npc npc) {
        Material torch = Mats.get("TORCH");
        if (++ticks > 20 * 180 || placed.size() >= 16) return done(npc);
        if (npc.settings().buildNeedsMaterials && npc.count(torch) == 0) {
            if (placed.isEmpty()) return fail("Ich habe keine Fackeln. Sag 'mach mir Fackeln', dann bastel ich welche.");
            return done(npc);
        }
        if (target == null) {
            target = findDark(npc);
            if (target == null) return done(npc);
        }
        Location c = target.getLocation().add(0.5, 0.5, 0.5);
        if (npc.location().distance(c) > 4) {
            npc.mover().go(c, 3.5, false, false);
            if (npc.mover().status() == de.glowcube.claudeai.move.Mover.Status.FAILED) {
                placed.add(target); // nicht nochmal versuchen
                target = null;
            }
            return Status.RUNNING;
        }
        npc.mover().stop();
        if (target.getType().isAir()) {
            if (npc.settings().buildNeedsMaterials) npc.take(torch, 1);
            npc.hold(torch);
            npc.lookAtBlock(target);
            npc.placeBlock(target, torch.createBlockData());
            npc.swing();
        }
        placed.add(target);
        target = null;
        return Status.RUNNING;
    }

    private Status done(Npc npc) {
        long real = placed.stream().filter(b -> Mats.is(b.getType(), "TORCH")).count();
        npc.hold(null);
        if (real > 0) npc.say(real + " Fackeln stehen. Jetzt ist es hier schoen hell!");
        else npc.say("Hier ist es schon hell genug.");
        return Status.DONE;
    }

    private Block findDark(Npc npc) {
        Location me = npc.location();
        Block best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    Block b = Blocks.at(me.getWorld(), me.getBlockX() + dx, me.getBlockY() + dy, me.getBlockZ() + dz);
                    if (b == null || !b.getType().isAir()) continue;
                    Block below = b.getRelative(0, -1, 0);
                    if (!Blocks.floor(below) || Mats.isLeaves(below.getType())) continue;
                    if (b.getLightLevel() >= 8) continue;
                    if (tooClose(b)) continue;
                    double d = dx * dx + dz * dz + dy * dy * 4;
                    if (d < bestD) {
                        bestD = d;
                        best = b;
                    }
                }
            }
        }
        return best;
    }

    private boolean tooClose(Block b) {
        for (Block p : placed) {
            double dx = p.getX() - b.getX(), dz = p.getZ() - b.getZ();
            if (dx * dx + dz * dz < 36) return true;
        }
        return false;
    }

    @Override
    public void stop(Npc npc) {
        npc.mover().stop();
    }
}
