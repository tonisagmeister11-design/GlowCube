package de.glowcube.claudeai.task;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

import de.glowcube.claudeai.npc.Npc;

/** Herumliegende Items einsammeln. */
public final class CollectItemsTask extends Task {

    private final int radius;
    private final Set<UUID> skip = new HashSet<>();
    private Item target;
    private int picked;
    private int ticks;
    private int targetTicks;

    public CollectItemsTask(int radius) {
        this.radius = radius;
    }

    @Override
    public String label() {
        return "sammle Items ein";
    }

    @Override
    public Status tick(Npc npc) {
        if (++ticks > 20 * 120) return Status.DONE;
        Location me = npc.location();
        if (target == null || !target.isValid()) {
            target = null;
            double best = radius * radius;
            for (Entity e : me.getWorld().getNearbyEntities(me, radius, 8, radius)) {
                if (!(e instanceof Item item) || !e.isValid() || skip.contains(e.getUniqueId())) continue;
                double d = e.getLocation().distanceSquared(me);
                if (d < best) {
                    best = d;
                    target = item;
                }
            }
            targetTicks = 0;
            if (target == null) {
                if (picked == 0) return fail("Hier liegt nichts herum.");
                npc.say("Alles eingesammelt (" + picked + " Items).");
                return Status.DONE;
            }
        }
        if (++targetTicks > 20 * 20) {
            skip.add(target.getUniqueId());
            target = null;
            return Status.RUNNING;
        }
        Location tl = target.getLocation();
        if (me.distance(tl) < 1.6) {
            picked += npc.pickupNear(tl, 1.6);
            target = null;
            return Status.RUNNING;
        }
        npc.mover().go(tl, 1.2, false, false);
        if (npc.mover().status() == de.glowcube.claudeai.move.Mover.Status.FAILED) {
            skip.add(target.getUniqueId());
            target = null;
        }
        return Status.RUNNING;
    }

    @Override
    public void stop(Npc npc) {
        npc.mover().stop();
    }
}
