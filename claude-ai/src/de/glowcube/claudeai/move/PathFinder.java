package de.glowcube.claudeai.move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.bukkit.World;
import org.bukkit.block.Block;

import de.glowcube.claudeai.world.Blocks;

/**
 * A*-Wegsuche auf dem Blockraster. Ein Knoten ist der Block, in dem die Fuesse stehen.
 *
 * <p>Erlaubt: gehen (auch diagonal), einen Block hochsteigen, bis zu drei Bloecke
 * hinunterfallen, schwimmen, durch Holztueren gehen. Im Grabe-Modus darf der Weg auch
 * durch natuerliche Bloecke fuehren - die werden unterwegs abgebaut.
 */
public final class PathFinder {

    public record Node(int x, int y, int z, boolean dig) {}

    /** Wann ist ein Knoten gut genug? */
    public interface Goal {
        boolean reached(int x, int y, int z);
        double estimate(int x, int y, int z);
    }

    private static final int[][] DIRS = {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

    private final World world;
    private final boolean digging;
    private final int maxNodes;

    public PathFinder(World world, boolean digging, int maxNodes) {
        this.world = world;
        this.digging = digging;
        this.maxNodes = maxNodes;
    }

    /** Ziel: irgendwo, von wo der Mittelpunkt (tx,ty,tz) hoechstens reach entfernt ist (gemessen ab Augenhoehe-ish). */
    public static Goal near(double tx, double ty, double tz, double reach) {
        double r2 = reach * reach;
        return new Goal() {
            @Override
            public boolean reached(int x, int y, int z) {
                double dx = x + 0.5 - tx;
                double dy = y + 1.0 - ty;
                double dz = z + 0.5 - tz;
                return dx * dx + dy * dy + dz * dz <= r2;
            }

            @Override
            public double estimate(int x, int y, int z) {
                double dx = x + 0.5 - tx;
                double dy = y + 0.5 - ty;
                double dz = z + 0.5 - tz;
                return Math.max(0, Math.sqrt(dx * dx + dy * dy + dz * dz) - reach);
            }
        };
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private final class Entry implements Comparable<Entry> {
        final int x, y, z;
        final boolean dig;
        final double g, f;
        final Entry parent;

        Entry(int x, int y, int z, boolean dig, double g, double h, Entry parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dig = dig;
            this.g = g;
            this.f = g + h * 1.2;
            this.parent = parent;
        }

        @Override
        public int compareTo(Entry o) {
            return Double.compare(f, o.f);
        }
    }

    /** Liefert den Weg ohne Startknoten, leere Liste = schon da, null = kein Weg. */
    public List<Node> find(int sx, int sy, int sz, Goal goal) {
        if (goal.reached(sx, sy, sz)) return List.of();
        PriorityQueue<Entry> open = new PriorityQueue<>();
        Map<Long, Double> best = new HashMap<>();
        Entry start = new Entry(sx, sy, sz, false, 0, goal.estimate(sx, sy, sz), null);
        open.add(start);
        best.put(key(sx, sy, sz), 0.0);
        Entry closest = start;
        double closestH = goal.estimate(sx, sy, sz);
        int expanded = 0;

        while (!open.isEmpty() && expanded < maxNodes) {
            Entry cur = open.poll();
            Double known = best.get(key(cur.x, cur.y, cur.z));
            if (known != null && known < cur.g - 1e-9) continue;
            expanded++;
            if (goal.reached(cur.x, cur.y, cur.z)) return build(cur);
            double h = goal.estimate(cur.x, cur.y, cur.z);
            if (h < closestH) {
                closestH = h;
                closest = cur;
            }
            expand(cur, goal, open, best);
        }
        // Kein vollstaendiger Weg: zumindest naeher heran, falls das deutlich hilft
        if (closest != start && closestH < goal.estimate(sx, sy, sz) - 3) return build(closest);
        return null;
    }

    private void expand(Entry cur, Goal goal, PriorityQueue<Entry> open, Map<Long, Double> best) {
        boolean inWater = water(cur.x, cur.y, cur.z);
        for (int[] d : DIRS) {
            int nx = cur.x + d[0];
            int nz = cur.z + d[1];
            boolean diagonal = d[0] != 0 && d[1] != 0;
            if (diagonal && !(clear(cur.x + d[0], cur.y, cur.z) && clear(cur.x, cur.y, cur.z + d[1]))) continue;
            double base = diagonal ? 1.414 : 1.0;

            // gleiche Hoehe
            int dig = bodyCost(nx, cur.y, nz);
            if (dig >= 0 && canStand(nx, cur.y, nz)) {
                push(cur, nx, cur.y, nz, dig > 0, base + dig * 2.5 + waterCost(nx, cur.y, nz), goal, open, best);
                continue;
            }
            // eine Stufe hoch: ueber dem Kopf muss Platz sein
            int upDig = bodyCost(nx, cur.y + 1, nz);
            int headDig = digCost(cur.x, cur.y + 2, cur.z);
            if (!diagonal && upDig >= 0 && headDig >= 0 && canStand(nx, cur.y + 1, nz)) {
                push(cur, nx, cur.y + 1, nz, upDig + headDig > 0, base + 0.6 + (upDig + headDig) * 2.5, goal, open, best);
            }
            // hinunter: Luft vor uns, dann bis 3 tief fallen
            if (!diagonal && bodyCost(nx, cur.y, nz) == 0) {
                for (int drop = 1; drop <= 3; drop++) {
                    int ny = cur.y - drop;
                    if (!passableOrWater(nx, ny + 1, nz)) break;
                    if (canStand(nx, ny, nz) && clear(nx, ny, nz)) {
                        push(cur, nx, ny, nz, false, base + drop * 0.4, goal, open, best);
                        break;
                    }
                    if (!clear(nx, ny, nz)) break;
                }
            }
        }
        // senkrecht schwimmen
        if (inWater) {
            if (clear(cur.x, cur.y + 1, cur.z) && clear(cur.x, cur.y + 2, cur.z) && canStand(cur.x, cur.y + 1, cur.z)) {
                push(cur, cur.x, cur.y + 1, cur.z, false, 1.5, goal, open, best);
            }
            if (water(cur.x, cur.y - 1, cur.z)) push(cur, cur.x, cur.y - 1, cur.z, false, 1.5, goal, open, best);
        }
        // im Grabe-Modus: senkrecht nach unten graben (Treppe waere schoener, das hier ist robust)
        if (digging && cur.y > world.getMinHeight() + 5) {
            Block below = Blocks.at(world, cur.x, cur.y - 1, cur.z);
            if (Blocks.diggable(below) && canStand(cur.x, cur.y - 1, cur.z)) {
                push(cur, cur.x, cur.y - 1, cur.z, true, 3.5, goal, open, best);
            }
        }
    }

    private void push(Entry cur, int x, int y, int z, boolean dig, double cost, Goal goal,
            PriorityQueue<Entry> open, Map<Long, Double> best) {
        double g = cur.g + cost;
        long k = key(x, y, z);
        Double old = best.get(k);
        if (old != null && old <= g) return;
        best.put(k, g);
        open.add(new Entry(x, y, z, dig, g, goal.estimate(x, y, z), cur));
    }

    private List<Node> build(Entry end) {
        List<Node> out = new ArrayList<>();
        for (Entry e = end; e.parent != null; e = e.parent) out.add(new Node(e.x, e.y, e.z, e.dig));
        Collections.reverse(out);
        return out;
    }

    // ------------------------------------------------------------------ Blockabfragen

    private boolean clear(int x, int y, int z) {
        return Blocks.passable(Blocks.at(world, x, y, z));
    }

    private boolean water(int x, int y, int z) {
        Block b = Blocks.at(world, x, y, z);
        return b != null && Blocks.isWater(b.getType());
    }

    private boolean passableOrWater(int x, int y, int z) {
        return clear(x, y, z) || water(x, y, z);
    }

    private double waterCost(int x, int y, int z) {
        return water(x, y, z) ? 1.5 : 0;
    }

    /** 0 = frei, n = so viele Bloecke muessen weg, -1 = geht nicht. */
    private int digCost(int x, int y, int z) {
        Block b = Blocks.at(world, x, y, z);
        if (b == null) return -1;
        if (Blocks.passable(b)) return 0;
        if (digging && Blocks.diggable(b)) return 1;
        return -1;
    }

    private int bodyCost(int x, int y, int z) {
        int feet = digCost(x, y, z);
        int head = digCost(x, y + 1, z);
        if (feet < 0 || head < 0) return -1;
        return feet + head;
    }

    private boolean canStand(int x, int y, int z) {
        Block below = Blocks.at(world, x, y - 1, z);
        if (below == null) return false;
        if (Blocks.floor(below)) return true;
        Block feet = Blocks.at(world, x, y, z);
        return feet != null && Blocks.isWater(feet.getType());
    }
}
