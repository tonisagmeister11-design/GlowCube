package de.glowcube.claudeai.task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.world.Blocks;
import de.glowcube.claudeai.world.Mats;

/**
 * Sucht Bloecke einer Sorte, laeuft hin, baut sie ab und sammelt die Drops ein - bis
 * genug vom gewuenschten Item im Inventar ist. Baeume werden komplett gefaellt, Erze
 * duerfen freigegraben werden.
 */
public final class GatherTask extends Task {

    private enum State { SEARCH, WALK, BREAK }

    private final String what;
    private final Predicate<Material> blockMatch;
    private final Predicate<Material> itemMatch;
    private final int wanted;
    private final boolean trees;
    private final boolean dig;

    private int baseline = -1;
    private State state = State.SEARCH;
    private final Deque<Block> candidates = new ArrayDeque<>();
    private final Deque<Block> treeLogs = new ArrayDeque<>();
    private final Set<Long> failed = new HashSet<>();
    private Block target;
    private int radius;
    private int stateTicks;
    private boolean saidProtected;

    public GatherTask(String what, Predicate<Material> blockMatch, Predicate<Material> itemMatch, int wanted,
            boolean trees, boolean dig) {
        this.what = what;
        this.blockMatch = blockMatch;
        this.itemMatch = itemMatch;
        this.wanted = Math.max(1, wanted);
        this.trees = trees;
        this.dig = dig;
    }

    private int collected(Npc npc) {
        return npc.count(itemMatch) - baseline;
    }

    @Override
    public String label() {
        return "sammle " + what;
    }

    @Override
    public void start(Npc npc) {
        if (baseline < 0) baseline = npc.count(itemMatch);
        state = State.SEARCH;
        candidates.clear();
        radius = 12;
        target = null;
    }

    @Override
    public Status tick(Npc npc) {
        if (collected(npc) >= wanted) {
            if (target != null) npc.pickupNear(target.getLocation(), 4);
            return Status.DONE;
        }
        stateTicks++;
        switch (state) {
            case SEARCH -> {
                target = next(npc);
                if (target == null) {
                    int have = Math.max(0, collected(npc));
                    return fail(have > 0
                            ? "Mehr " + what + " finde ich hier nicht - ich hab " + have + " von " + wanted + "."
                            : "Ich finde hier kein " + what + ". Bring mich woanders hin!");
                }
                state = State.WALK;
                stateTicks = 0;
            }
            case WALK -> {
                if (!blockMatch.test(target.getType())) {
                    state = State.SEARCH;
                    return Status.RUNNING;
                }
                Location center = target.getLocation().add(0.5, 0.5, 0.5);
                if (inReach(npc, center)) {
                    npc.mover().stop();
                    state = State.BREAK;
                    stateTicks = 0;
                    return Status.RUNNING;
                }
                npc.mover().go(center, trees ? 5.5 : 4.0, dig, false);
                if (npc.mover().status() == de.glowcube.claudeai.move.Mover.Status.FAILED || stateTicks > 20 * 40) {
                    failed.add(key(target));
                    npc.mover().stop();
                    state = State.SEARCH;
                }
            }
            case BREAK -> {
                if (!blockMatch.test(target.getType())) {
                    state = State.SEARCH;
                    return Status.RUNNING;
                }
                // Nicht durch Waende greifen: was im Weg ist, wird erst weggegraben
                Block obstacle = npc.obstacleTo(target);
                if (obstacle != null) {
                    boolean mayDig = Blocks.diggable(obstacle) || Mats.isLeaves(obstacle.getType())
                            || blockMatch.test(obstacle.getType());
                    if (!mayDig || !dig && !trees && !blockMatch.test(obstacle.getType())) {
                        failed.add(key(target));
                        state = State.SEARCH;
                        return Status.RUNNING;
                    }
                    if (npc.work(obstacle)) {
                        if (!Blocks.passable(obstacle)) {
                            failed.add(key(target));
                            state = State.SEARCH;
                        } else {
                            npc.pickupNear(obstacle.getLocation().add(0.5, 0.5, 0.5), 3.5);
                        }
                    }
                    return Status.RUNNING;
                }
                if (!npc.work(target)) return Status.RUNNING;
                if (blockMatch.test(target.getType())) {
                    // Schutzgebiet: nicht abbaubar
                    failed.add(key(target));
                    if (!saidProtected) {
                        saidProtected = true;
                        npc.say("Hier darf ich nichts abbauen - das ist geschuetzt.");
                    }
                } else {
                    npc.pickupNear(target.getLocation().add(0.5, 0.5, 0.5), 3.5);
                    if (trees) collectTree(npc, target);
                }
                state = State.SEARCH;
            }
        }
        return Status.RUNNING;
    }

    private boolean inReach(Npc npc, Location center) {
        Location me = npc.location();
        double dx = me.getX() - center.getX();
        // Gleiches Mass wie der Mover (Hueft-/Brusthoehe), nur etwas grosszuegiger - sonst
        // meldet der Mover "angekommen", waehrend hier "zu weit" herauskommt.
        double dy = me.getY() + 1.0 - center.getY();
        double dz = me.getZ() - center.getZ();
        double reach = trees ? 6.5 : 4.6;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    /** Naechster Block: erst der Rest des aktuellen Baums, dann aus der Kandidatenliste. */
    private Block next(Npc npc) {
        while (!treeLogs.isEmpty()) {
            Block b = treeLogs.poll();
            if (blockMatch.test(b.getType()) && !failed.contains(key(b))) return b;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            while (!candidates.isEmpty()) {
                Block b = candidates.poll();
                if (blockMatch.test(b.getType()) && !failed.contains(key(b))) return b;
            }
            if (radius > npc.settings().searchRadius) return null;
            scan(npc, radius);
            radius += 12;
        }
        return null;
    }

    private void scan(Npc npc, int r) {
        Location me = npc.location();
        World w = me.getWorld();
        int cx = me.getBlockX(), cy = me.getBlockY(), cz = me.getBlockZ();
        int down = trees ? 6 : dig ? 24 : 8;
        int up = trees ? 14 : dig ? 6 : 8;
        List<Block> found = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!Blocks.loaded(w, cx + dx, cz + dz)) continue;
                for (int dy = -down; dy <= up; dy++) {
                    Block b = Blocks.at(w, cx + dx, cy + dy, cz + dz);
                    if (b == null || !blockMatch.test(b.getType()) || failed.contains(key(b))) continue;
                    if (!dig && !trees && !exposed(w, b)) continue;
                    found.add(b);
                }
            }
        }
        found.sort((a, b) -> Double.compare(cost(me, a), cost(me, b)));
        candidates.clear();
        for (int i = 0; i < Math.min(found.size(), 96); i++) candidates.add(found.get(i));
    }

    private double cost(Location me, Block b) {
        double dx = b.getX() + 0.5 - me.getX();
        double dy = b.getY() - me.getY();
        double dz = b.getZ() + 0.5 - me.getZ();
        // Baeume: lieber unten anfangen; Erze: tief unten ist teurer
        double vertical = trees ? Math.abs(dy) * 0.5 : Math.abs(dy) * (dy < 0 ? 2.0 : 1.5);
        return Math.sqrt(dx * dx + dz * dz) + vertical;
    }

    private boolean exposed(World w, Block b) {
        int[][] n = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (int[] d : n) {
            Block o = Blocks.at(w, b.getX() + d[0], b.getY() + d[1], b.getZ() + d[2]);
            if (o != null && Blocks.passable(o)) return true;
        }
        return false;
    }

    /** Alle Staemme, die am gerade gefaellten haengen - von unten nach oben. */
    private void collectTree(Npc npc, Block start) {
        World w = start.getWorld();
        Deque<Block> todo = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        List<Block> logs = new ArrayList<>();
        todo.add(start);
        seen.add(key(start));
        while (!todo.isEmpty() && logs.size() < 48) {
            Block b = todo.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block o = Blocks.at(w, b.getX() + dx, b.getY() + dy, b.getZ() + dz);
                        if (o == null || !seen.add(key(o))) continue;
                        if (Mats.isLog(o.getType()) && blockMatch.test(o.getType())) {
                            logs.add(o);
                            todo.add(o);
                        }
                    }
                }
            }
        }
        logs.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        treeLogs.clear();
        treeLogs.addAll(logs);
    }

    private static long key(Block b) {
        return ((long) (b.getX() & 0x3FFFFFF) << 38) | ((long) (b.getZ() & 0x3FFFFFF) << 12) | (b.getY() & 0xFFF);
    }

    @Override
    public void stop(Npc npc) {
        npc.mover().stop();
        npc.resetWork();
    }
}
