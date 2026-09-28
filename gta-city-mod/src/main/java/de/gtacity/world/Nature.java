package de.gtacity.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static de.gtacity.world.CityLayout.BEACH;
import static de.gtacity.world.CityLayout.FLOOR;
import static de.gtacity.world.CityLayout.GROUND;
import static de.gtacity.world.CityLayout.INNER;
import static de.gtacity.world.CityLayout.PROMENADE;
import static de.gtacity.world.CityLayout.SEA_LEVEL;

/** Parks, trees, beach and ocean. */
final class Nature {
    private Nature() {
    }

    private static final BlockState RIM = B.s(Blocks.STONE_BRICKS);
    private static final BlockState PILLAR = B.s(Blocks.QUARTZ_PILLAR);

    // ------------------------------------------------------------------ trees

    /** Draws the part of a round tree that falls into this column. (dx, dz) = column minus trunk position. */
    static void tree(Column c, int dx, int dz, int base, int height, BlockState log, BlockState leaves) {
        int top = base + height - 1;
        int adx = Math.abs(dx), adz = Math.abs(dz);
        int m = Math.max(adx, adz);
        if (m > 2) {
            return;
        }
        if (m == 0) {
            c.fill(base, top, log);
            c.set(top + 1, leaves);
            return;
        }
        if (!(adx == 2 && adz == 2)) {
            setIfAir(c, top - 2, leaves);
            setIfAir(c, top - 1, leaves);
        }
        if (m <= 1) {
            setIfAir(c, top, leaves);
        }
        if (adx + adz <= 1) {
            setIfAir(c, top + 1, leaves);
        }
    }

    /** Palm tree for the beach. */
    static void palm(Column c, int dx, int dz, int base, int height) {
        int top = base + height - 1;
        int adx = Math.abs(dx), adz = Math.abs(dz);
        int m = Math.max(adx, adz);
        if (m > 3) {
            return;
        }
        if (m == 0) {
            c.fill(base, top, B.JUNGLE_LOG);
            c.set(top + 1, B.JUNGLE_LEAVES);
            return;
        }
        boolean ray = dx == 0 || dz == 0 || adx == adz;
        if (!ray) {
            return;
        }
        if (m <= 2) {
            setIfAir(c, top + 1, B.JUNGLE_LEAVES);
        } else {
            setIfAir(c, top, B.JUNGLE_LEAVES);
        }
    }

    private static void setIfAir(Column c, int y, BlockState s) {
        if (c.get(y) == null) {
            c.set(y, s);
        }
    }

    // ------------------------------------------------------------------ coast

    private static int coastTop(int d) {
        if (d <= PROMENADE) {
            return GROUND;
        }
        int bd = d - PROMENADE;
        if (bd <= BEACH) {
            return GROUND + 1 - (bd * 8) / BEACH;
        }
        return Math.max(36, GROUND + 1 - 8 - (bd - BEACH) / 3);
    }

    static void coast(int x, int z, int d, Column c) {
        int top = coastTop(d);
        c.fill(c.minY + 1, -1, B.DEEPSLATE);
        c.fill(0, top - 4, B.STONE);
        if (d <= PROMENADE) {
            c.fill(top - 3, top, B.STONE);
            c.set(GROUND + 1, d == PROMENADE ? B.CURB : B.SIDEWALK);
        } else {
            c.fill(top - 3, top - 2, B.SANDSTONE);
            c.fill(top - 1, top, B.SAND);
            if (top < SEA_LEVEL) {
                c.fill(top + 1, SEA_LEVEL, B.WATER);
            }
        }
        palms(x, z, c);
    }

    private static final int PALM_GRID = 11;

    private static void palms(int x, int z, Column c) {
        int ci = Math.floorDiv(x, PALM_GRID), cj = Math.floorDiv(z, PALM_GRID);
        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                long h = Hash.of(i, j, 404);
                if (Hash.range(h, 100) >= 55) {
                    continue;
                }
                int px = i * PALM_GRID + 2 + Hash.range(h >>> 8, PALM_GRID - 4);
                int pz = j * PALM_GRID + 2 + Hash.range(h >>> 16, PALM_GRID - 4);
                int pd = CityLayout.outsideDistance(px, pz);
                int ptop = coastTop(pd);
                boolean promenadeSpot = pd == 3;
                boolean beachSpot = pd > PROMENADE + 3 && ptop > SEA_LEVEL;
                if (!promenadeSpot && !beachSpot) {
                    continue;
                }
                int base = promenadeSpot ? GROUND + 2 : ptop + 1;
                if (px == x && pz == z && promenadeSpot) {
                    c.set(GROUND + 1, B.GRASS);
                }
                palm(c, x - px, z - pz, base, 7 + Hash.range(h >>> 24, 4));
            }
        }
    }

    // ------------------------------------------------------------------ parks

    static void park(int gx, int gz, int x, int z, Column c) {
        int bx = CityLayout.local(x) - CityLayout.CORRIDOR;
        int bz = CityLayout.local(z) - CityLayout.CORRIDOR;
        boolean central = gx == 2 && gz == -1;
        c.set(GROUND, B.DIRT);
        c.set(GROUND + 1, B.GRASS);

        double cx = bx - (INNER - 1) / 2.0, cz = bz - (INNER - 1) / 2.0;
        double r = Math.sqrt(cx * cx + cz * cz);

        // fountain in the middle
        if (r <= 7.5) {
            fountain(c, r);
            return;
        }
        // pond
        if (central) {
            double px = (bx - 58) / 11.0, pz = (bz - 18) / 8.0;
            double p = px * px + pz * pz;
            if (p <= 1.0) {
                c.set(GROUND - 1, B.SAND);
                c.set(GROUND, B.WATER);
                c.set(GROUND + 1, B.WATER);
                if (p < 0.5) {
                    c.set(GROUND - 1, B.WATER);
                    c.set(GROUND - 2, B.SAND);
                }
                return;
            }
            if (p <= 1.3) {
                c.set(GROUND + 1, B.SAND);
                return;
            }
        }
        boolean edge = bx == 0 || bz == 0 || bx == INNER - 1 || bz == INNER - 1;
        boolean mainPath = Math.abs(cx) < 3 || Math.abs(cz) < 3;
        int ringDist = Math.min(Math.min(bx, bz), Math.min(INNER - 1 - bx, INNER - 1 - bz));
        boolean ring = ringDist >= 6 && ringDist <= 8;

        if (edge && !mainPath) {
            c.set(FLOOR, B.AZALEA_LEAVES);
            return;
        }
        if (mainPath || ring) {
            c.set(GROUND + 1, B.PLAZA);
            if (mainPath && !ring && r > 9) {
                parkLamp(c, cx, cz);
            }
            return;
        }
        bench(c, cx, cz);
        if (c.get(FLOOR) == null) {
            decorate(x, z, c);
        }
        parkTrees(gx, gz, bx, bz, central, c);
    }

    private static void fountain(Column c, double r) {
        if (r > 6.5) {
            c.set(GROUND + 1, RIM);
            c.set(FLOOR, RIM);
            return;
        }
        c.set(GROUND, RIM);
        c.set(GROUND + 1, B.WATER);
        if (r < 1.5) {
            c.set(GROUND + 1, PILLAR);
            c.fill(FLOOR, FLOOR + 2, PILLAR);
            c.set(FLOOR + 3, B.WATER);
        }
    }

    private static void parkLamp(Column c, double cx, double cz) {
        // lamps along the main paths, on the path edge
        double along = Math.abs(cx) < 3 ? cz : cx;
        double across = Math.abs(cx) < 3 ? cx : cz;
        if (Math.abs(across) > 2.4 && Math.floorMod((int) Math.floor(along), 12) == 0) {
            c.fill(FLOOR, FLOOR + 2, B.POLE);
            c.set(FLOOR + 3, B.s(Blocks.LANTERN));
        }
    }

    private static void bench(Column c, double cx, double cz) {
        int ax = (int) Math.floor(cx), az = (int) Math.floor(cz);
        // benches right next to the main paths
        if ((ax == -4 || ax == 3) && Math.abs(cz) > 10 && Math.floorMod(az, 10) < 2) {
            c.set(FLOOR, B.stairs(Blocks.SPRUCE_STAIRS, ax < 0 ? Direction.WEST : Direction.EAST));
        } else if ((az == -4 || az == 3) && Math.abs(cx) > 10 && Math.floorMod(ax, 10) < 2) {
            c.set(FLOOR, B.stairs(Blocks.SPRUCE_STAIRS, az < 0 ? Direction.NORTH : Direction.SOUTH));
        }
    }

    static void decorate(int x, int z, Column c) {
        int r = Hash.range(Hash.of(x, z, 21), 100);
        if (r < 12) {
            c.set(FLOOR, B.SHORT_GRASS);
        } else if (r < 14) {
            c.set(FLOOR, B.POPPY);
        } else if (r < 16) {
            c.set(FLOOR, B.DANDELION);
        } else if (r < 17) {
            c.set(FLOOR, B.BLUET);
        }
    }

    private static final int TREE_GRID = 9;

    private static void parkTrees(int gx, int gz, int bx, int bz, boolean central, Column c) {
        int ci = bx / TREE_GRID, cj = bz / TREE_GRID;
        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                long h = Hash.of(gx * 100L + i, gz * 100L + j, 55);
                if (Hash.range(h, 100) >= 70) {
                    continue;
                }
                int tx = i * TREE_GRID + 2 + Hash.range(h >>> 8, TREE_GRID - 4);
                int tz = j * TREE_GRID + 2 + Hash.range(h >>> 16, TREE_GRID - 4);
                if (!treeSpotFree(tx, tz, central)) {
                    continue;
                }
                int kind = Hash.range(h >>> 24, 3);
                BlockState log = kind == 1 ? B.BIRCH_LOG : B.OAK_LOG;
                BlockState leaves = kind == 1 ? B.BIRCH_LEAVES : kind == 2 ? B.FLOWERING_AZALEA : B.OAK_LEAVES;
                if (bx - tx == 0 && bz - tz == 0) {
                    c.set(FLOOR, null);
                }
                tree(c, bx - tx, bz - tz, FLOOR, 5 + Hash.range(h >>> 32, 3), log, leaves);
            }
        }
    }

    private static boolean treeSpotFree(int bx, int bz, boolean central) {
        if (bx < 4 || bz < 4 || bx > INNER - 5 || bz > INNER - 5) {
            return false;
        }
        double cx = bx - (INNER - 1) / 2.0, cz = bz - (INNER - 1) / 2.0;
        if (Math.abs(cx) < 6 || Math.abs(cz) < 6 || Math.sqrt(cx * cx + cz * cz) < 11) {
            return false;
        }
        int ringDist = Math.min(Math.min(bx, bz), Math.min(INNER - 1 - bx, INNER - 1 - bz));
        if (ringDist >= 4 && ringDist <= 10) {
            return false;
        }
        if (central) {
            double px = (bx - 58) / 14.0, pz = (bz - 18) / 11.0;
            return px * px + pz * pz > 1.0;
        }
        return true;
    }
}
