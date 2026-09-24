package de.glowcube.claudeai.task;

import java.util.List;

import org.bukkit.Location;

import de.glowcube.claudeai.build.UndoStore;
import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.world.Fx;

/** Stellt den Zustand vor dem letzten Bau wieder her - von oben nach unten. */
public final class UndoTask extends Task {

    private final UndoStore.Entry entry;
    private int index;
    private int approachTicks;

    public UndoTask(UndoStore.Entry entry) {
        this.entry = entry;
        this.index = entry.changes().size() - 1;
    }

    @Override
    public String label() {
        return "reisse " + entry.name() + " ab";
    }

    @Override
    public Status tick(Npc npc) {
        List<UndoStore.Change> changes = entry.changes();
        int budget = 12;
        Location me = npc.location();
        while (index >= 0 && budget-- > 0) {
            UndoStore.Change c = changes.get(index);
            Location at = c.block().getLocation().add(0.5, 0.5, 0.5);
            if (!at.getWorld().equals(me.getWorld())) {
                npc.teleportNear(at);
                return Status.RUNNING;
            }
            if (Math.hypot(me.getX() - at.getX(), me.getZ() - at.getZ()) > 9 && approachTicks < 200) {
                Location ground = new Location(at.getWorld(), at.getX(), me.getY(), at.getZ());
                npc.mover().go(ground, 6, false, true);
                approachTicks++;
                if (npc.mover().status() == de.glowcube.claudeai.move.Mover.Status.FAILED) {
                    npc.mover().stop();
                    if (!npc.teleportNear(ground)) approachTicks = 200;
                }
                return Status.RUNNING;
            }
            approachTicks = 0;
            if (!c.block().getType().isAir()) Fx.breakEffect(c.block(), c.block().getBlockData());
            c.block().setBlockData(c.before(), false);
            if (budget % 4 == 0) npc.swing();
            index--;
        }
        if (index < 0) {
            npc.say(entry.name() + " ist wieder weg - alles wie vorher.");
            return Status.DONE;
        }
        return Status.RUNNING;
    }

    @Override
    public void stop(Npc npc) {
        npc.mover().stop();
    }
}
