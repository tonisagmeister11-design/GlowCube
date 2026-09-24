package de.glowcube.claudeai.task;

import java.util.function.Supplier;

import org.bukkit.Location;

import de.glowcube.claudeai.move.Mover;
import de.glowcube.claudeai.npc.Npc;

/** Irgendwohin laufen - das Ziel darf sich bewegen (z.B. ein Spieler). */
public final class GotoTask extends Task {

    private final Supplier<Location> target;
    private final double reach;
    private final boolean teleport;
    private final String label;
    private int ticks;

    public GotoTask(Supplier<Location> target, double reach, boolean teleport, String label) {
        this.target = target;
        this.reach = reach;
        this.teleport = teleport;
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public void start(Npc npc) {
        ticks = 0;
    }

    @Override
    public Status tick(Npc npc) {
        Location to = target.get();
        if (to == null) return fail("Ich weiss nicht mehr, wo ich hin soll.");
        if (++ticks > 20 * 180) return fail("Ich komme da nicht hin.");
        npc.mover().go(to, reach, false, teleport);
        Mover.Status s = npc.mover().status();
        if (s == Mover.Status.ARRIVED) return Status.DONE;
        if (s == Mover.Status.FAILED) return fail("Ich finde keinen Weg dorthin.");
        return Status.RUNNING;
    }

    @Override
    public void stop(Npc npc) {
        npc.mover().stop();
    }
}
