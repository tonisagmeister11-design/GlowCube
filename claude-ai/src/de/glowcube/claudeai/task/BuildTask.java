package de.glowcube.claudeai.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import de.glowcube.claudeai.brain.Lexicon;
import de.glowcube.claudeai.build.Blueprint;
import de.glowcube.claudeai.build.UndoStore;
import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.world.Blocks;

/**
 * Baut einen Bauplan Block fuer Block. Natur wird weggeraeumt, fremde Bauwerke bleiben
 * unangetastet. Alles wird fuer "rueckgaengig" mitgeschrieben.
 */
public final class BuildTask extends Task {

    private final Blueprint plan;
    private final UndoStore undo;
    private final List<UndoStore.Change> changes = new ArrayList<>();
    private int index;
    private int skipped;
    private boolean checked;
    private int approachTicks;

    public BuildTask(Blueprint plan, UndoStore undo) {
        this.plan = plan;
        this.undo = undo;
    }

    @Override
    public String label() {
        int pct = plan.placements.isEmpty() ? 100 : index * 100 / plan.placements.size();
        return "baue " + plan.name + " (" + pct + "%)";
    }

    @Override
    public Status tick(Npc npc) {
        if (!checked) {
            checked = true;
            Block ground = plan.world.getBlockAt(plan.center);
            if (!ground.getType().isAir() && !npc.mayBreak(ground)) return fail("Hier darf ich nicht bauen - das Gebiet ist geschuetzt.");
            if (npc.settings().buildNeedsMaterials) {
                for (Map.Entry<Material, Integer> e : plan.bill().entrySet()) {
                    int have = npc.count(e.getKey());
                    if (have < e.getValue()) {
                        return fail("Mir fehlen " + (e.getValue() - have) + "x " + Lexicon.name(e.getKey())
                                + " fuer " + plan.name + ". Gib mir das Material oder schalte build.needs-materials aus.");
                    }
                }
            }
        }
        if (index >= plan.placements.size()) {
            undo.push(plan.name, changes);
            npc.hold(null);
            npc.say(Lexicon.pick(Lexicon.BUILD_DONE_LINES, npc.random()).replace("%s", plan.name)
                    + (skipped > 0 ? " (" + skipped + " Bloecke habe ich ausgelassen, da stand schon was.)" : ""));
            return Status.DONE;
        }

        int budgetBuild = npc.settings().blocksPerTick;
        int budgetClear = 16;
        Location me = npc.location();
        while (index < plan.placements.size() && (budgetBuild > 0 || budgetClear > 0)) {
            Blueprint.Placement p = plan.placements.get(index);
            Block b = Blocks.at(plan.world, p.x(), p.y(), p.z());
            if (b == null) {
                index++;
                continue;
            }
            Location c = b.getLocation().add(0.5, 0.5, 0.5);
            double horiz = Math.hypot(me.getX() - c.getX(), me.getZ() - c.getZ());
            if (horiz > 7 && approachTicks < 200) {
                // Auf Bodenhoehe hinlaufen - an Dachbloecke kommt man nie direkt heran
                Location ground = new Location(c.getWorld(), c.getX(), me.getY(), c.getZ());
                npc.mover().go(ground, 5.0, false, true);
                approachTicks++;
                if (npc.mover().status() == de.glowcube.claudeai.move.Mover.Status.FAILED) {
                    npc.mover().stop();
                    if (!npc.teleportNear(ground)) approachTicks = 200;
                }
                return Status.RUNNING;
            }
            approachTicks = 0;
            if (p.data() == null) {
                if (budgetClear <= 0) break;
                Material type = b.getType();
                if (!type.isAir() && Blocks.isNatural(type)) {
                    changes.add(new UndoStore.Change(b, b.getBlockData().clone()));
                    b.setType(Material.AIR, false);
                    budgetClear--;
                }
                index++;
                continue;
            }
            if (budgetBuild <= 0) break;
            Material want = p.data().getMaterial();
            Material type = b.getType();
            if (type == want && b.getBlockData().getAsString().equals(p.data().getAsString())) {
                index++;
                continue;
            }
            boolean replaceable = type.isAir() || b.isLiquid() || Blocks.isNatural(type) || type == want;
            if (!replaceable) {
                skipped++;
                index++;
                continue;
            }
            if (npc.settings().buildNeedsMaterials && p.item() != null && npc.take(p.item(), 1) == 0) {
                return fail("Mir ist " + Lexicon.name(p.item()) + " ausgegangen.");
            }
            if (p.item() != null) npc.hold(p.item());
            changes.add(new UndoStore.Change(b, b.getBlockData().clone()));
            npc.lookAtBlock(b);
            npc.placeBlock(b, p.data());
            budgetBuild--;
            index++;
        }
        return Status.RUNNING;
    }

    @Override
    public void stop(Npc npc) {
        npc.mover().stop();
        // Unterbrochen: was schon steht, bleibt rueckgaengig machbar
        if (index < plan.placements.size() && !changes.isEmpty()) {
            undo.push(plan.name + " (halb fertig)", changes);
            changes.clear();
        }
    }
}
