package de.gtacity.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static de.gtacity.world.B.s;
import static de.gtacity.world.CityLayout.FLOOR;
import static de.gtacity.world.CityLayout.GROUND;

/** All building types of the city, drawn column by column. */
final class Buildings {
    private Buildings() {
    }

    private static final Palette BANK = new Palette(s(Blocks.QUARTZ_BLOCK), s(Blocks.LIGHT_GRAY_STAINED_GLASS),
            s(Blocks.POLISHED_ANDESITE), B.SEA_LANTERN, s(Blocks.SMOOTH_QUARTZ), s(Blocks.GOLD_BLOCK));
    private static final Palette AMMU = new Palette(s(Blocks.GRAY_CONCRETE), s(Blocks.GLASS),
            s(Blocks.POLISHED_ANDESITE), B.SEA_LANTERN, s(Blocks.GRAY_CONCRETE), s(Blocks.RED_CONCRETE));
    private static final Palette STORE = new Palette(s(Blocks.WHITE_CONCRETE), s(Blocks.GLASS),
            s(Blocks.WHITE_TERRACOTTA), B.SEA_LANTERN, s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.LIME_CONCRETE));
    private static final Palette POLICE = new Palette(s(Blocks.WHITE_CONCRETE), s(Blocks.LIGHT_BLUE_STAINED_GLASS),
            s(Blocks.POLISHED_ANDESITE), B.SEA_LANTERN, s(Blocks.GRAY_CONCRETE), s(Blocks.BLUE_CONCRETE));
    private static final Palette HOSPITAL = new Palette(s(Blocks.WHITE_CONCRETE), s(Blocks.LIGHT_BLUE_STAINED_GLASS),
            s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.WHITE_CONCRETE), s(Blocks.RED_CONCRETE));
    private static final Palette DEALER = new Palette(s(Blocks.WHITE_CONCRETE), s(Blocks.GLASS),
            s(Blocks.POLISHED_DIORITE), B.SEA_LANTERN, s(Blocks.WHITE_CONCRETE), s(Blocks.BLACK_CONCRETE));
    private static final Palette[] VILLAS = {
            new Palette(s(Blocks.SMOOTH_QUARTZ), s(Blocks.GLASS), s(Blocks.POLISHED_DIORITE), B.SEA_LANTERN, s(Blocks.SMOOTH_QUARTZ), s(Blocks.GRAY_CONCRETE)),
            new Palette(s(Blocks.WHITE_CONCRETE), s(Blocks.LIGHT_GRAY_STAINED_GLASS), s(Blocks.BIRCH_PLANKS), B.SEA_LANTERN, s(Blocks.WHITE_CONCRETE), s(Blocks.STRIPPED_DARK_OAK_LOG)),
            new Palette(s(Blocks.SMOOTH_SANDSTONE), s(Blocks.GLASS), s(Blocks.SPRUCE_PLANKS), B.GLOWSTONE, s(Blocks.SMOOTH_SANDSTONE), s(Blocks.DARK_OAK_PLANKS)),
            new Palette(s(Blocks.BLACK_CONCRETE), s(Blocks.GLASS), s(Blocks.POLISHED_ANDESITE), B.SEA_LANTERN, s(Blocks.GRAY_CONCRETE), s(Blocks.WHITE_CONCRETE)),
    };
    private static final Palette[] WAREHOUSES = {
            new Palette(s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.GRAY_CONCRETE), s(Blocks.SMOOTH_STONE)),
            new Palette(s(Blocks.CYAN_TERRACOTTA), s(Blocks.GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.GRAY_CONCRETE), s(Blocks.LIGHT_GRAY_CONCRETE)),
            new Palette(s(Blocks.BRICKS), s(Blocks.GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.STONE_BRICKS), s(Blocks.STONE_BRICKS)),
            new Palette(s(Blocks.WHITE_CONCRETE), s(Blocks.GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.BLUE_CONCRETE)),
    };
    private static final Block[] ROOF_STAIRS = {Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.BRICK_STAIRS,
            Blocks.DEEPSLATE_TILE_STAIRS, Blocks.MUD_BRICK_STAIRS, Blocks.STONE_BRICK_STAIRS};
    private static final Block[] ROOF_FULL = {Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BRICKS,
            Blocks.DEEPSLATE_TILES, Blocks.MUD_BRICKS, Blocks.STONE_BRICKS};
    private static final Block[] CONTAINER_A = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.GREEN_CONCRETE,
            Blocks.ORANGE_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.BROWN_CONCRETE};
    private static final Block[] CONTAINER_B = {Blocks.RED_TERRACOTTA, Blocks.BLUE_TERRACOTTA,
            Blocks.GREEN_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.WHITE_TERRACOTTA,
            Blocks.BROWN_TERRACOTTA};

    private static final Tower.Facade SHOP = (t, rel, f, along, front) -> f == t.fh - 2 ? t.p.accent()
            : (front && f >= 1 && f <= 3 ? t.p.glass() : t.p.frame());
    private static final Tower.Facade SHOWROOM = (t, rel, f, along, front) -> f == t.fh - 1 ? t.p.accent()
            : (Math.floorMod(along, 6) == 0 ? t.p.frame() : t.p.glass());
    private static final Tower.Facade WAREHOUSE = (t, rel, f, along, front) -> {
        if (Math.floorMod(along, 5) == 0) {
            return t.p.accent();
        }
        return f == t.fh - 2 && Math.floorMod(along, 2) == 0 ? t.p.glass() : t.p.frame();
    };

    static void column(Lot lot, int x, int z, Column c) {
        c.set(GROUND, B.STONE);
        switch (lot.type) {
            case SKYSCRAPER -> skyscraper(lot, x, z, c);
            case OFFICE -> office(lot, x, z, c);
            case HOUSE -> house(lot, x, z, c);
            case VILLA -> villa(lot, x, z, c);
            case WAREHOUSE -> warehouse(lot, x, z, c);
            case CONTAINERS -> containers(lot, x, z, c);
            case PARKING -> parking(lot, x, z, c);
            case POCKET_PARK, COURTYARD -> pocketPark(lot, x, z, c);
            case BANK -> bank(lot, x, z, c);
            case AMMU_NATION -> ammuNation(lot, x, z, c);
            case STORE -> store(lot, x, z, c);
            case POLICE -> police(lot, x, z, c);
            case HOSPITAL -> hospital(lot, x, z, c);
            case GAS_STATION -> gasStation(lot, x, z, c);
            case CAR_DEALER -> carDealer(lot, x, z, c);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Tower tower(Lot.Frame f, int u0, int v0, int u1, int v1, int fh, int floors, Palette p,
                               Tower.Facade facade, Direction door) {
        int ax = f.x(u0, v0), az = f.z(u0, v0), bx = f.x(u1, v1), bz = f.z(u1, v1);
        return new Tower(GROUND + 1, fh, floors, p, facade, false, door, Math.min(ax, bx), Math.min(az, bz),
                Math.max(ax, bx), Math.max(az, bz));
    }

    private static void plaza(Lot lot, int x, int z, Column c) {
        c.set(GROUND + 1, B.PLAZA);
        int dx = Math.min(x - lot.x0, lot.x1 - x), dz = Math.min(z - lot.z0, lot.z1 - z);
        if (dx >= 1 && dx <= 2 && dz >= 1 && dz <= 2) {
            c.set(GROUND + 1, B.GRASS);
            c.set(FLOOR, B.FLOWERING_AZALEA);
        }
    }

    private static void hedge(Column c) {
        c.set(GROUND + 1, B.GRASS);
        c.set(FLOOR, B.AZALEA_LEAVES);
    }

    private static void pool(Column c, int u, int v, int u0, int v0, int u1, int v1) {
        if (u < u0 || u > u1 || v < v0 || v > v1) {
            return;
        }
        if (u == u0 || u == u1 || v == v0 || v == v1) {
            c.set(GROUND + 1, B.s(Blocks.SMOOTH_QUARTZ));
            c.set(FLOOR, null);
            return;
        }
        c.set(GROUND - 1, B.s(Blocks.SMOOTH_QUARTZ));
        c.set(GROUND, B.WATER);
        c.set(GROUND + 1, B.WATER);
        c.set(FLOOR, null);
    }

    private static String fit(String text, String shorter, int width) {
        return Signs.width(text) + 2 <= width ? text : shorter;
    }

    // ------------------------------------------------------------------ downtown and midtown

    private static void skyscraper(Lot lot, int x, int z, Column c) {
        long h = lot.seed;
        int m = 4;
        int x0 = lot.x0 + m, z0 = lot.z0 + m, x1 = lot.x1 - m, z1 = lot.z1 - m;
        int shape = Hash.range(h >>> 3, 10);
        boolean round = shape >= 7;
        if (shape == 5 || shape == 6) {
            if (Hash.range(h >>> 40, 2) == 0) {
                x0 += 5;
                x1 -= 5;
            } else {
                z0 += 5;
                z1 -= 5;
            }
        }
        double dist = Math.sqrt((double) lot.centerX() * lot.centerX() + (double) lot.centerZ() * lot.centerZ());
        int bonus = (int) Math.max(0, (1 - dist / 450.0) * 26);
        int floors = 16 + Hash.range(h >>> 9, 26) + bonus;
        floors = Math.min(floors, (c.maxY - 30 - (GROUND + 1)) / 4);
        Palette p = Palette.TOWERS[Hash.range(h >>> 15, Palette.TOWERS.length)];
        Tower.Facade facade = round
                ? (Hash.range(h >>> 21, 2) == 0 ? Tower.CURTAIN : Tower.BANDS)
                : Tower.ALL[Hash.range(h >>> 21, 4)];
        Tower t = new Tower(GROUND + 1, 4, floors, p, facade, round, lot.front, x0, z0, x1, z1);
        if (shape < 5 || round) {
            t.tier((int) (floors * 0.55), 3).tier((int) (floors * 0.8), 6);
        }
        t.elevator = true;
        t.helipad = !round && floors > 32 && Hash.range(h >>> 27, 2) == 0;
        t.antenna = !t.helipad;
        t.antennaHeight = 8 + Hash.range(h >>> 33, 14);
        if (!t.column(c, x, z)) {
            plaza(lot, x, z, c);
        }
    }

    private static void office(Lot lot, int x, int z, Column c) {
        long h = lot.seed;
        int m = 3;
        Palette p = Palette.OFFICES[Hash.range(h >>> 15, Palette.OFFICES.length)];
        int floors = 4 + Hash.range(h >>> 9, 10);
        Tower.Facade facade = Tower.ALL[1 + Hash.range(h >>> 21, 4)];
        Tower t = new Tower(GROUND + 1, 4, floors, p, facade, false, lot.front, lot.x0 + m, lot.z0 + m,
                lot.x1 - m, lot.z1 - m);
        if (floors > 8 && Hash.range(h >>> 30, 2) == 0) {
            t.tier(floors - 3, 3);
        }
        t.elevator = floors > 4;
        if (!t.column(c, x, z)) {
            plaza(lot, x, z, c);
        }
    }

    // ------------------------------------------------------------------ living

    private static void house(Lot lot, int x, int z, Column c) {
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        int width = f.width, depth = f.depth;
        long h = lot.seed;
        Palette p = Palette.HOUSES[Hash.range(h, Palette.HOUSES.length)];
        int w = Hash.between(h >>> 4, 11, 14);
        int d = Hash.between(h >>> 8, 8, 11);
        int floors = 1 + Hash.range(h >>> 12, 2);
        boolean flat = Hash.range(h >>> 14, 3) == 0;
        boolean hasPool = Hash.range(h >>> 18, 5) < 2;
        int u0 = 2, u1 = u0 + w - 1;
        int v0 = 6, v1 = v0 + d - 1;
        int dw0 = 19, dw1 = 22;
        int doorU = u0 + w / 2;
        int base = GROUND + 1, fh = 4, top = base + floors * fh;

        // garden
        c.set(GROUND, B.DIRT);
        c.set(GROUND + 1, B.GRASS);
        boolean path = v < v0 && u == doorU;
        boolean drive = u >= dw0 && u <= dw1 && v <= v1;
        if (path) {
            c.set(GROUND + 1, s(Blocks.STONE_BRICKS));
        } else if (drive) {
            c.set(GROUND + 1, s(Blocks.LIGHT_GRAY_CONCRETE));
        } else if (u == 0 || u == width - 1 || v == depth - 1 || v == 0) {
            hedge(c);
        } else if (u < u0 || u > u1 || v < v0 || v > v1) {
            Nature.decorate(x, z, c);
            if (hasPool) {
                pool(c, u, v, u0, v1 + 3, u0 + 8, depth - 3);
            }
        }

        // body
        if (u >= u0 && u <= u1 && v >= v0 && v <= v1) {
            boolean edge = u == u0 || u == u1 || v == v0 || v == v1;
            boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
            int along = (u == u0 || u == u1) ? v : u;
            for (int y = base; y <= top; y++) {
                int rel = y - base, fl = rel % fh;
                BlockState st;
                if (fl == 0) {
                    boolean lamp = rel > 0 && u == (u0 + u1) / 2 && v == (v0 + v1) / 2;
                    st = edge ? p.accent() : (lamp ? p.light() : p.floor());
                } else if (edge) {
                    if (corner) {
                        st = p.accent();
                    } else if (v == v0 && u == doorU && rel == 1) {
                        st = B.door(Blocks.DARK_OAK_DOOR, f.out(), false);
                    } else if (v == v0 && u == doorU && rel == 2) {
                        st = B.door(Blocks.DARK_OAK_DOOR, f.out(), true);
                    } else if ((flat ? fl >= 1 : fl == 2) && along % 3 != 0 && Math.abs(u - doorU) > 1) {
                        st = p.glass();
                    } else {
                        st = p.frame();
                    }
                } else {
                    st = null;
                }
                c.set(y, st);
            }
            if (floors == 2 && u == u1 - 1 && v == v1 - 1) {
                c.fill(base + 1, base + fh, B.ladder(f.out()));
            }
            if (flat) {
                if (edge) {
                    c.set(top + 1, s(Blocks.SMOOTH_QUARTZ_SLAB));
                } else {
                    c.set(top, p.roof());
                }
            }
        }

        // gable roof
        if (!flat && u >= u0 && u <= u1 && v >= v0 - 1 && v <= v1 + 1) {
            int kind = Hash.range(h >>> 20, ROOF_STAIRS.length);
            int a = v - (v0 - 1), b = (v1 + 1) - v;
            int dist = Math.min(a, b);
            int roofY = top + dist;
            if (u == u0 || u == u1) {
                c.fill(top + 1, roofY - 1, p.frame());
            }
            if (a == b) {
                c.set(roofY, s(ROOF_FULL[kind]));
            } else {
                c.set(roofY, B.stairs(ROOF_STAIRS[kind], a < b ? f.out().getOpposite() : f.out()));
            }
        }

        // trees
        if (!drive && !path) {
            Nature.tree(c, u - (u0 + 1), v - 2, FLOOR, 5, B.OAK_LOG, B.OAK_LEAVES);
            if (!hasPool) {
                Nature.tree(c, u - 20, v - (depth - 5), FLOOR, 6, B.BIRCH_LOG, B.BIRCH_LEAVES);
            }
        }
    }

    private static void villa(Lot lot, int x, int z, Column c) {
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        int width = f.width, depth = f.depth;
        long h = lot.seed;
        Palette p = VILLAS[Hash.range(h, VILLAS.length)];
        int u0 = 4, u1 = u0 + Hash.between(h >>> 4, 20, 25) - 1;
        int v0 = 13, v1 = v0 + Hash.between(h >>> 8, 12, 15) - 1;
        int doorU = (u0 + u1) / 2;
        int dw0 = width - 7, dw1 = width - 3;

        c.set(GROUND, B.DIRT);
        c.set(GROUND + 1, B.GRASS);
        boolean path = v < v0 && Math.abs(u - doorU) <= 1;
        boolean drive = u >= dw0 && u <= dw1 && v <= v1;
        if (path) {
            c.set(GROUND + 1, B.PLAZA);
        } else if (drive) {
            c.set(GROUND + 1, s(Blocks.LIGHT_GRAY_CONCRETE));
        } else if (u == 0 || u == width - 1 || v == depth - 1 || v == 0) {
            hedge(c);
        } else {
            Nature.decorate(x, z, c);
            pool(c, u, v, u0 + 2, v1 + 2, u0 + 15, depth - 3);
        }

        Tower t = tower(f, u0, v0, u1, v1, 4, 2, p, Tower.CURTAIN, lot.front).tier(1, 2);
        t.roofUnits = false;
        if (t.column(c, x, z)) {
            return;
        }
        if (!drive && !path) {
            Nature.palm(c, u - 6, v - 5, FLOOR, 7);
            Nature.palm(c, u - (width - 12), v - 5, FLOOR, 8);
        }
    }

    private static void pocketPark(Lot lot, int x, int z, Column c) {
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        int width = f.width, depth = f.depth;
        c.set(GROUND, B.DIRT);
        c.set(GROUND + 1, B.GRASS);
        double cu = u - (width - 1) / 2.0, cv = v - (depth - 1) / 2.0;
        boolean pathway = Math.abs(cu) < 1.5 || Math.abs(cv) < 1.5;
        boolean withPool = lot.type == CityLayout.LotType.COURTYARD && Hash.range(lot.seed >>> 5, 2) == 0;
        if ((u == 0 || v == 0 || u == width - 1 || v == depth - 1) && !pathway) {
            hedge(c);
            return;
        }
        if (withPool) {
            pool(c, u, v, 4, 4, width - 5, depth - 5);
            if (u >= 4 && u <= width - 5 && v >= 4 && v <= depth - 5) {
                return;
            }
            c.set(GROUND + 1, B.PLAZA);
            Nature.palm(c, u - 2, v - 2, FLOOR, 6);
            Nature.palm(c, u - (width - 3), v - (depth - 3), FLOOR, 7);
            return;
        }
        double r = Math.sqrt(cu * cu + cv * cv);
        if (r <= 2.5) {
            c.set(GROUND + 1, r > 1.5 ? s(Blocks.STONE_BRICKS) : B.WATER);
            if (r > 1.5) {
                c.set(FLOOR, s(Blocks.STONE_BRICK_SLAB));
            }
            return;
        }
        if (pathway) {
            c.set(GROUND + 1, B.PLAZA);
            return;
        }
        Nature.decorate(x, z, c);
        int qu = width / 4, qv = depth / 4;
        Nature.tree(c, u - qu, v - qv, FLOOR, 5, B.OAK_LOG, B.OAK_LEAVES);
        Nature.tree(c, u - (width - 1 - qu), v - qv, FLOOR, 6, B.BIRCH_LOG, B.BIRCH_LEAVES);
        Nature.tree(c, u - qu, v - (depth - 1 - qv), FLOOR, 6, B.OAK_LOG, B.FLOWERING_AZALEA);
        Nature.tree(c, u - (width - 1 - qu), v - (depth - 1 - qv), FLOOR, 5, B.OAK_LOG, B.OAK_LEAVES);
    }

    // ------------------------------------------------------------------ industry

    private static void warehouse(Lot lot, int x, int z, Column c) {
        long h = lot.seed;
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        c.set(GROUND + 1, B.PLAZA_LIGHT);
        Palette p = WAREHOUSES[Hash.range(h >>> 3, WAREHOUSES.length)];
        int height = 9 + Hash.range(h >>> 9, 6);
        Tower t = tower(f, 3, 3, f.width - 4, f.depth - 4, height, 1, p, WAREHOUSE, lot.front);
        t.lobbyGlass = false;
        t.skylights = true;
        if (!t.column(c, x, z)) {
            return;
        }
        if (v == 3 && Math.abs(u - f.width / 2) <= 3) {
            c.fill(FLOOR, FLOOR + 4, null); // roller door
        }
        if (u > 4 && u < f.width - 5 && v > 8 && v < f.depth - 5) {
            int mu = Math.floorMod(u, 6), mv = Math.floorMod(v, 8);
            if ((mu == 1 || mu == 2) && mv >= 2 && mv <= 4) {
                int stack = 1 + Hash.range(Hash.of(u / 6, v / 8, h), 3);
                c.fill(FLOOR, FLOOR + stack - 1, s(Blocks.SPRUCE_PLANKS));
            }
        }
    }

    private static void containers(Lot lot, int x, int z, Column c) {
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        c.set(GROUND + 1, B.PLAZA_LIGHT);
        if (Hash.range(lot.seed >>> 7, 2) == 0 && crane(c, u, v, f.width, f.depth)) {
            return;
        }
        int su = u - 2, sv = v - 4;
        if (su < 0 || sv < 0) {
            return;
        }
        int iu = su / 4, iv = sv / 10, ou = su % 4, ov = sv % 10;
        if (ou == 3 || ov >= 8) {
            return;
        }
        if (2 + iu * 4 + 3 > f.width - 2 || 4 + iv * 10 + 8 > f.depth - 2) {
            return;
        }
        long ch = Hash.of(lot.seed, iu, iv, 8);
        int stack = Hash.range(ch, 4);
        for (int level = 0; level < stack; level++) {
            int color = Hash.range(Hash.of(ch, level, 3), CONTAINER_A.length);
            Block block = (ov % 2 == 0) ? CONTAINER_A[color] : CONTAINER_B[color];
            c.fill(FLOOR + level * 3, FLOOR + level * 3 + 2, s(block));
        }
    }

    private static boolean crane(Column c, int u, int v, int width, int depth) {
        BlockState yellow = s(Blocks.YELLOW_CONCRETE);
        int beamY = FLOOR + 18;
        boolean legU = u == 1 || u == 2 || u == width - 3 || u == width - 2;
        boolean legV = v == 1 || v == 2 || v == depth - 3 || v == depth - 2;
        boolean beamRow = v == 1 || v == 2 || v == depth - 3 || v == depth - 2;
        boolean trolley = (u == width / 2 || u == width / 2 + 1) && v >= 1 && v <= depth - 2;
        boolean any = false;
        if (legU && legV) {
            c.fill(FLOOR, beamY + 1, yellow);
            any = true;
        }
        if (beamRow && u >= 1 && u <= width - 2) {
            c.fill(beamY, beamY + 1, yellow);
            any = true;
        }
        if (trolley) {
            c.set(beamY + 2, yellow);
            if (v == depth / 2) {
                c.fill(beamY - 6, beamY + 1, s(Blocks.IRON_BARS));
                c.set(beamY - 7, s(Blocks.IRON_BLOCK));
            }
            any = true;
        }
        return any && legU && legV;
    }

    private static void parking(Lot lot, int x, int z, Column c) {
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        if (u == 0 || u == f.width - 1 || v == f.depth - 1) {
            hedge(c);
            return;
        }
        c.set(GROUND, B.ASPHALT);
        boolean bayRow = (v >= 3 && v <= 8) || (v >= f.depth - 9 && v <= f.depth - 4);
        if (bayRow && (u - 1) % 4 == 0) {
            c.set(GROUND, B.LINE_WHITE);
        }
        if (u == f.width / 2 && v == f.depth / 2) {
            c.fill(GROUND + 1, GROUND + 6, B.POLE);
            c.set(GROUND + 7, B.STREET_LIGHT);
        }
    }

    // ------------------------------------------------------------------ special buildings

    private static void bank(Lot lot, int x, int z, Column c) {
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        int u0 = 3, u1 = f.width - 4, v0 = 5, v1 = f.depth - 4;
        int uc = (u0 + u1) / 2;
        int fh = 5;
        Tower t = tower(f, u0, v0, u1, v1, fh, lot.size >= 39 ? 3 : 2, BANK, Tower.GRID, lot.front);
        t.elevator = lot.size >= 39;
        c.set(GROUND + 1, s(Blocks.SMOOTH_QUARTZ));
        if (t.column(c, x, z)) {
            if (u > u0 && u < u1 && v > v0 && v < v1) {
                int vr0 = v1 - 7;
                if (v == v0 + 6 && u > u0 + 2 && u < u1 - 3) {
                    c.set(FLOOR, s(Blocks.POLISHED_ANDESITE));
                    c.set(FLOOR + 1, s(Blocks.SMOOTH_STONE_SLAB));
                }
                if (v >= vr0 && Math.abs(u - uc) <= 4) {
                    boolean wall = v == vr0 || Math.abs(u - uc) == 4;
                    if (wall) {
                        if (!(v == vr0 && Math.abs(u - uc) <= 1)) {
                            c.fill(FLOOR, FLOOR + fh - 2, s(Blocks.IRON_BLOCK));
                        }
                    } else if (u == uc && v == v1 - 3) {
                        c.set(FLOOR, ModBlocksRef.vault());
                    } else if (v == v1 - 1) {
                        c.fill(FLOOR, FLOOR + 1, s(Blocks.GOLD_BLOCK));
                    }
                }
            }
            Signs.board(c, u, v, v0, uc, t.top + 2, "BANK", s(Blocks.GREEN_CONCRETE), B.SEA_LANTERN);
            return;
        }
        // portico with columns in front of the entrance
        if (v >= 1 && v < v0 && u >= u0 && u <= u1) {
            if (v == 2 && (u - u0) % 4 == 2) {
                c.fill(FLOOR, GROUND + fh, s(Blocks.QUARTZ_PILLAR));
            }
            c.set(GROUND + 1 + fh, s(Blocks.SMOOTH_QUARTZ));
            if (v == v0 - 1 && u == uc + 3) {
                c.set(FLOOR, ModBlocksRef.atm());
            }
        }
    }

    private static void ammuNation(Lot lot, int x, int z, Column c) {
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        int u0 = 3, u1 = f.width - 4, v0 = 4, v1 = Math.min(f.depth - 4, v0 + 17);
        int uc = (u0 + u1) / 2;
        c.set(GROUND + 1, B.PLAZA);
        Tower t = tower(f, u0, v0, u1, v1, 6, 1, AMMU, SHOP, lot.front);
        t.lobbyGlass = false;
        if (!t.column(c, x, z)) {
            return;
        }
        if (u > u0 && u < u1 && v > v0 && v < v1) {
            if (v == v1 - 4 && u > u0 + 2 && u < u1 - 2) {
                c.set(FLOOR, ModBlocksRef.weaponCounter());
            }
            if (v == v1 - 1 && Math.floorMod(u - u0, 3) != 0) {
                c.set(FLOOR + 1, s(Blocks.TARGET));
            }
        }
        Signs.board(c, u, v, v0, uc, t.top + 2, "AMMU", s(Blocks.WHITE_CONCRETE), s(Blocks.RED_CONCRETE));
    }

    private static void storeInterior(Column c, int u, int v, int u0, int v0, int u1, int v1) {
        if (u <= u0 || u >= u1 || v <= v0 || v >= v1) {
            return;
        }
        if (v == v0 + 2 && u >= u0 + 2 && u <= u0 + 4) {
            c.set(FLOOR, ModBlocksRef.storeCounter());
            return;
        }
        if (u > u0 + 6 && u < u1 - 1 && v > v0 + 3 && v < v1 - 2 && Math.floorMod(u - u0, 4) == 0) {
            c.fill(FLOOR, FLOOR + 1, s(Blocks.BOOKSHELF));
        }
        if (v == v1 - 1 && u > u0 + 6) {
            c.fill(FLOOR, FLOOR + 1, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
        }
    }

    private static void store(Lot lot, int x, int z, Column c) {
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        int u0 = 3, u1 = f.width - 4, v0 = 4, v1 = Math.min(f.depth - 4, v0 + 15);
        c.set(GROUND + 1, B.PLAZA);
        Tower t = tower(f, u0, v0, u1, v1, 5, 1, STORE, SHOP, lot.front);
        t.lobbyGlass = false;
        if (!t.column(c, x, z)) {
            return;
        }
        storeInterior(c, u, v, u0, v0, u1, v1);
        Signs.board(c, u, v, v0, (u0 + u1) / 2, t.top + 2, "24/7", s(Blocks.GREEN_CONCRETE), B.SEA_LANTERN);
    }

    private static void police(Lot lot, int x, int z, Column c) {
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        boolean big = lot.size >= 39;
        int u0 = 3, u1 = f.width - 4, v0 = big ? 11 : 4, v1 = f.depth - 4;
        Tower t = tower(f, u0, v0, u1, v1, 4, 3, POLICE, Tower.BANDS, lot.front);
        t.elevator = true;
        if (t.column(c, x, z)) {
            Signs.board(c, u, v, v0, (u0 + u1) / 2, t.top + 2, "LSPD", s(Blocks.BLUE_CONCRETE), B.SEA_LANTERN);
            return;
        }
        if (big && v >= 1 && v < v0 - 1) {
            c.set(GROUND + 1, (u - 1) % 5 == 0 && v >= 2 && v <= v0 - 3 ? B.LINE_WHITE : B.ASPHALT);
        } else {
            c.set(GROUND + 1, B.PLAZA);
        }
    }

    private static void hospital(Lot lot, int x, int z, Column c) {
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        boolean big = lot.size >= 39;
        int u0 = 3, u1 = f.width - 4, v0 = 4, v1 = f.depth - 4;
        int floors = big ? 6 + Hash.range(lot.seed >>> 3, 4) : 4;
        Tower t = tower(f, u0, v0, u1, v1, 4, floors, HOSPITAL, Tower.GRID, lot.front);
        t.elevator = true;
        t.roofUnits = false;
        c.set(GROUND + 1, B.PLAZA);
        if (!t.column(c, x, z)) {
            return;
        }
        int uc = (u0 + u1) / 2, vc = (v0 + v1) / 2;
        if (t.roofInterior(x, z)) {
            int du = Math.abs(u - uc), dv = Math.abs(v - vc);
            if ((du <= 1 && dv <= 5) || (dv <= 1 && du <= 5)) {
                c.set(t.top, s(Blocks.RED_CONCRETE));
            }
        }
        int boardWidth = u1 - u0 + 1;
        Signs.board(c, u, v, v0, uc, t.top + 2, fit("HOSPITAL", "ER", boardWidth), s(Blocks.WHITE_CONCRETE),
                s(Blocks.RED_CONCRETE));
    }

    private static void gasStation(Lot lot, int x, int z, Column c) {
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        int width = f.width, depth = f.depth;
        boolean big = lot.size >= 39;
        int cv0 = 3, cv1 = big ? 14 : 10;
        int cu0 = 3, cu1 = width - 4;
        int su0 = 3, su1 = width - 4, sv0 = big ? depth - 13 : depth - 10, sv1 = depth - 3;

        Tower shop = tower(f, su0, sv0, su1, sv1, 5, 1, STORE, SHOP, lot.front);
        shop.lobbyGlass = false;
        if (shop.column(c, x, z)) {
            storeInterior(c, u, v, su0, sv0, su1, sv1);
            return;
        }
        if (u == 0 || u == width - 1 || v == depth - 1) {
            hedge(c);
            return;
        }
        c.set(GROUND, B.ASPHALT);
        int canopyY = GROUND + 6;
        if (u >= cu0 && u <= cu1 && v >= cv0 && v <= cv1) {
            boolean rim = u == cu0 || u == cu1 || v == cv0 || v == cv1;
            boolean lamp = !rim && Math.floorMod(u - cu0, 4) == 2 && Math.floorMod(v - cv0, 4) == 2;
            c.set(canopyY, rim ? s(Blocks.RED_CONCRETE) : (lamp ? B.SEA_LANTERN : s(Blocks.WHITE_CONCRETE)));
            if ((u == cu0 + 1 || u == cu1 - 1) && (v == cv0 + 1 || v == cv1 - 1)) {
                c.fill(GROUND + 1, canopyY - 1, s(Blocks.WHITE_CONCRETE));
            }
            int uc = (cu0 + cu1) / 2;
            int vm = (cv0 + cv1) / 2;
            boolean island = (Math.abs(u - (uc - 5)) <= 1 || Math.abs(u - (uc + 5)) <= 1) && Math.abs(v - vm) <= 2;
            if (island) {
                c.set(GROUND + 1, B.PLAZA_LIGHT);
                if ((u == uc - 5 || u == uc + 5) && Math.abs(v - vm) <= 1 && v != vm) {
                    c.fill(GROUND + 2, GROUND + 3, ModBlocksRef.gasPump());
                }
            }
            Signs.board(c, u, v, cv0, uc, canopyY + 1, "GAS", s(Blocks.RED_CONCRETE), B.SEA_LANTERN);
        }
    }

    private static void carDealer(Lot lot, int x, int z, Column c) {
        Lot.Frame f = lot.frame();
        int u = f.u(x, z), v = f.v(x, z);
        int u0 = 3, u1 = f.width - 4, v0 = 4, v1 = Math.min(f.depth - 4, v0 + 18);
        int uc = (u0 + u1) / 2;
        Tower t = tower(f, u0, v0, u1, v1, 6, 1, DEALER, SHOWROOM, lot.front);
        t.lobbyGlass = false;
        if (t.column(c, x, z)) {
            if (v == v0 && Math.abs(u - uc) <= 3) {
                c.fill(FLOOR, FLOOR + 3, null); // wide glass door for cars
            }
            if (v == v1 - 2 && u == uc) {
                c.set(FLOOR, ModBlocksRef.carCounter());
            }
            Signs.board(c, u, v, v0, uc, t.top + 2, "CARS", s(Blocks.BLACK_CONCRETE), B.SEA_LANTERN);
            return;
        }
        c.set(GROUND + 1, B.ASPHALT);
        if (u == 0 || u == f.width - 1 || v == f.depth - 1) {
            hedge(c);
        }
    }
}
