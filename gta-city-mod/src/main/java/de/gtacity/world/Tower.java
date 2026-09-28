package de.gtacity.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A multi storey building made of stacked tiers (each tier smaller than the one below it), either rectangular or
 * round. Draws itself one column at a time.
 */
final class Tower {
    /** Facade pattern for wall blocks that are not corners or floor slabs. */
    interface Facade {
        BlockState at(Tower t, int rel, int f, int along, boolean frontSide);
    }

    static final Facade CURTAIN = (t, rel, f, along, front) -> Math.floorMod(along, 5) == 0 ? t.p.frame() : t.p.glass();
    static final Facade GRID = (t, rel, f, along, front) -> Math.floorMod(along, 3) == 0 ? t.p.frame() : t.p.glass();
    static final Facade BANDS = (t, rel, f, along, front) -> f == 1 ? t.p.accent() : t.p.glass();
    static final Facade DECO = (t, rel, f, along, front) -> Math.floorMod(along, 4) == 0 ? t.p.accent()
            : (f == 1 ? t.p.frame() : t.p.glass());
    static final Facade PUNCHED = (t, rel, f, along, front) -> (f >= 2 && Math.floorMod(along, 3) != 0) ? t.p.glass()
            : t.p.frame();
    static final Facade[] ALL = {CURTAIN, GRID, BANDS, DECO, PUNCHED};

    final int base;
    final int fh;
    final int floors;
    final int top;
    final Palette p;
    final Facade facade;
    final boolean round;
    final Direction door;
    private final List<int[]> rects = new ArrayList<>();
    private final List<Integer> starts = new ArrayList<>();

    boolean lobbyGlass = true;
    boolean elevator;
    boolean helipad;
    boolean antenna;
    boolean roofUnits = true;
    boolean skylights;
    int antennaHeight = 12;

    Tower(int base, int fh, int floors, Palette p, Facade facade, boolean round, Direction door, int x0, int z0,
          int x1, int z1) {
        this.base = base;
        this.fh = fh;
        this.floors = floors;
        this.top = base + floors * fh;
        this.p = p;
        this.facade = facade;
        this.round = round;
        this.door = door;
        rects.add(new int[]{x0, z0, x1, z1});
        starts.add(base);
    }

    /** Adds a smaller tier starting at the given floor, inset on every side. */
    Tower tier(int atFloor, int inset) {
        if (atFloor <= 0 || atFloor >= floors) {
            return this;
        }
        int[] r = rects.getFirst();
        int[] n = {r[0] + inset, r[1] + inset, r[2] - inset, r[3] - inset};
        if (n[2] - n[0] < 4 || n[3] - n[1] < 4) {
            return this;
        }
        int y = base + atFloor * fh;
        if (y <= starts.getLast()) {
            return this;
        }
        rects.add(n);
        starts.add(y);
        return this;
    }

    int tiers() {
        return rects.size();
    }

    int[] rect(int i) {
        return rects.get(i);
    }

    int topRect() {
        return rects.size() - 1;
    }

    int centerX() {
        int[] r = rects.getLast();
        return (r[0] + r[2]) / 2;
    }

    int centerZ() {
        int[] r = rects.getLast();
        return (r[1] + r[3]) / 2;
    }

    private int end(int i) {
        return i + 1 < starts.size() ? starts.get(i + 1) : top;
    }

    boolean inside(int i, int x, int z) {
        int[] r = rects.get(i);
        if (x < r[0] || x > r[2] || z < r[1] || z > r[3]) {
            return false;
        }
        if (!round) {
            return true;
        }
        double rx = (r[2] - r[0] + 1) / 2.0, rz = (r[3] - r[1] + 1) / 2.0;
        double dx = (x + 0.5 - (r[0] + rx)) / rx, dz = (z + 0.5 - (r[1] + rz)) / rz;
        return dx * dx + dz * dz <= 1.0;
    }

    boolean inside(int x, int z) {
        return inside(0, x, z);
    }

    /** True for columns on the flat roof of the top tier (not the parapet). */
    boolean roofInterior(int x, int z) {
        int k = rects.size() - 1;
        return inside(k, x, z) && !edge(k, x, z);
    }

    private boolean edge(int i, int x, int z) {
        return !inside(i, x - 1, z) || !inside(i, x + 1, z) || !inside(i, x, z - 1) || !inside(i, x, z + 1);
    }

    private boolean corner(int i, int x, int z) {
        if (round) {
            return false;
        }
        int[] r = rects.get(i);
        return (x == r[0] || x == r[2]) && (z == r[1] || z == r[3]);
    }

    private int along(int i, int x, int z) {
        if (round) {
            return x + z;
        }
        int[] r = rects.get(i);
        return (x == r[0] || x == r[2]) ? z - r[1] : x - r[0];
    }

    private boolean frontSide(int x, int z) {
        if (door == null) {
            return false;
        }
        int[] r = rects.getFirst();
        return switch (door) {
            case NORTH -> z <= r[1] + (round ? 2 : 0);
            case SOUTH -> z >= r[3] - (round ? 2 : 0);
            case WEST -> x <= r[0] + (round ? 2 : 0);
            default -> x >= r[2] - (round ? 2 : 0);
        };
    }

    private boolean doorColumn(int x, int z) {
        if (door == null || !frontSide(x, z)) {
            return false;
        }
        int[] r = rects.getFirst();
        int cx = (r[0] + r[2]) / 2, cz = (r[1] + r[3]) / 2;
        return (door.getAxis() == Direction.Axis.Z) ? Math.abs(x - cx) <= 1 : Math.abs(z - cz) <= 1;
    }

    private boolean lightSpot(int x, int z) {
        int[] r = rects.getFirst();
        return Math.floorMod(x - r[0], 6) == 3 && Math.floorMod(z - r[1], 6) == 3;
    }

    /** Draws this column. Returns false if the column is not part of the building. */
    boolean column(Column c, int x, int z) {
        int k = -1;
        for (int i = 0; i < rects.size(); i++) {
            if (inside(i, x, z)) {
                k = i;
            } else {
                break;
            }
        }
        if (k < 0) {
            return false;
        }
        boolean isFront = frontSide(x, z);
        boolean isDoor = doorColumn(x, z);
        for (int j = 0; j <= k; j++) {
            int yA = starts.get(j);
            int yB = j < k ? starts.get(j + 1) - 1 : end(j);
            boolean edge = edge(j, x, z);
            boolean corner = corner(j, x, z);
            int along = along(j, x, z);
            for (int y = yA; y <= yB; y++) {
                int rel = y - base;
                int f = Math.floorMod(rel, fh);
                BlockState s;
                if (f == 0) {
                    s = edge ? p.frame() : (lightSpot(x, z) && rel > 0 ? p.light() : p.floor());
                } else if (edge) {
                    if (corner) {
                        s = p.frame();
                    } else if (rel < fh && isDoor && f <= 3) {
                        s = null;
                    } else if (rel < fh && lobbyGlass) {
                        s = f == fh - 1 && fh > 4 ? p.frame() : p.glass();
                    } else {
                        s = facade.at(this, rel, f, along, isFront);
                    }
                } else {
                    s = null;
                }
                c.set(y, s);
            }
        }
        int roofY = end(k);
        boolean edge = edge(k, x, z);
        c.set(roofY, edge ? p.frame() : p.roof());
        if (edge) {
            c.set(roofY + 1, p.frame());
        } else if (k == rects.size() - 1) {
            roof(c, x, z, roofY);
        }
        if (elevator && x == centerX() && z == centerZ()) {
            c.set(base + 1, ModBlocksRef.elevator());
            c.set(top + 1, ModBlocksRef.elevator());
        }
        return true;
    }

    private void roof(Column c, int x, int z, int roofY) {
        if (skylights && lightSpot(x, z)) {
            c.set(roofY, B.GLASS);
            c.set(roofY - 1, null);
            return;
        }
        int ex = centerX(), ez = centerZ();
        int dx = x - ex, dz = z - ez;
        if (helipad && Math.abs(dx) <= 4 && Math.abs(dz) <= 4) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            BlockState s = B.s(Blocks.GRAY_CONCRETE);
            if (ax == 4 || az == 4) {
                s = B.s(Blocks.YELLOW_CONCRETE);
            } else if ((ax == 2 && az <= 2) || (dz == 0 && ax <= 2)) {
                s = B.s(Blocks.WHITE_CONCRETE);
            }
            c.set(roofY, s);
            return;
        }
        if (antenna && dx == 2 && dz == 2) {
            c.fill(roofY + 1, roofY + antennaHeight, B.END_ROD);
            c.set(roofY + antennaHeight + 1, B.s(Blocks.REDSTONE_BLOCK));
            return;
        }
        if (roofUnits && !helipad && (Math.abs(dx) > 2 || Math.abs(dz) > 2)) {
            int[] r = rects.getLast();
            int mx = Math.floorMod(x - r[0], 7), mz = Math.floorMod(z - r[1], 7);
            if ((mx == 2 || mx == 3) && (mz == 2 || mz == 3)) {
                c.set(roofY + 1, B.s(Blocks.LIGHT_GRAY_CONCRETE));
                c.set(roofY + 2, B.s(Blocks.IRON_TRAPDOOR));
            }
        }
    }
}
