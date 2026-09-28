package de.gtacity.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static de.gtacity.world.CityLayout.CORRIDOR;
import static de.gtacity.world.CityLayout.GROUND;
import static de.gtacity.world.CityLayout.PITCH;
import static de.gtacity.world.CityLayout.ROAD;
import static de.gtacity.world.CityLayout.SIDEWALK;

/** Roads, crossings, sidewalks and street furniture. */
final class Streets {
    private Streets() {
    }

    private static final int LAMP_SPACING = 26;
    private static final int LAMP_HEIGHT = 7;
    private static final BlockState TRAFFIC_RED = B.s(Blocks.RED_CONCRETE);
    private static final BlockState TRAFFIC_YELLOW = B.s(Blocks.YELLOW_CONCRETE);
    private static final BlockState TRAFFIC_GREEN = B.s(Blocks.LIME_CONCRETE);
    private static final BlockState TRAFFIC_BOX = B.s(Blocks.BLACK_CONCRETE);

    static void column(int x, int z, int lx, int lz, boolean rx, boolean rz, Column c) {
        boolean roadX = rx && lx >= SIDEWALK && lx < SIDEWALK + ROAD;
        boolean roadZ = rz && lz >= SIDEWALK && lz < SIDEWALK + ROAD;

        if (roadX || roadZ) {
            c.set(GROUND - 1, B.STONE);
            c.set(GROUND, roadSurface(lx, lz, rx, rz, roadX, roadZ));
            lampArmOverRoad(lx, lz, rx, rz, roadX, roadZ, c);
            return;
        }

        // sidewalk
        c.set(GROUND, B.STONE);
        boolean curb = (rx && (lx == SIDEWALK - 1 || lx == SIDEWALK + ROAD))
                || (rz && (lz == SIDEWALK - 1 || lz == SIDEWALK + ROAD));
        c.set(GROUND + 1, curb ? B.CURB : B.SIDEWALK);

        if (rx && rz) {
            // corner of an intersection: traffic lights at the inner corner
            if ((lx == SIDEWALK - 1 || lx == SIDEWALK + ROAD) && (lz == SIDEWALK - 1 || lz == SIDEWALK + ROAD)) {
                trafficLight(c);
            }
            return;
        }
        sidewalkFurniture(x, z, lx, lz, rx, c);
    }

    private static BlockState roadSurface(int lx, int lz, boolean rx, boolean rz, boolean roadX, boolean roadZ) {
        if (rx && rz) {
            if (roadX && !roadZ) {
                return (lx - SIDEWALK) % 2 == 0 ? B.LINE_WHITE : B.ASPHALT; // zebra crossing
            }
            if (roadZ && !roadX) {
                return (lz - SIDEWALK) % 2 == 0 ? B.LINE_WHITE : B.ASPHALT;
            }
            return B.ASPHALT;
        }
        if (rx) {
            // north-south road
            int r = lx - SIDEWALK;
            if (r == 5 || r == 6) {
                return B.LINE_YELLOW;
            }
            if ((lz == CORRIDOR && r >= 7) || (lz == PITCH - 1 && r <= 4)) {
                return B.LINE_WHITE; // stop lines before the next crossing
            }
            return B.ASPHALT;
        }
        // east-west road
        int r = lz - SIDEWALK;
        if (r == 5 || r == 6) {
            return B.LINE_YELLOW;
        }
        if ((lx == PITCH - 1 && r >= 7) || (lx == CORRIDOR && r <= 4)) {
            return B.LINE_WHITE;
        }
        return B.ASPHALT;
    }

    /** Distance from the curb inside a sidewalk: 0 = curb row, 2 = next to the buildings. */
    private static int curbDistance(int l) {
        return l < SIDEWALK ? SIDEWALK - 1 - l : l - (SIDEWALK + ROAD);
    }

    private static boolean lampAt(int along) {
        return along >= CORRIDOR && (along - CORRIDOR) % LAMP_SPACING == LAMP_SPACING / 2;
    }

    private static void sidewalkFurniture(int x, int z, int lx, int lz, boolean rx, Column c) {
        int across = rx ? lx : lz;   // position across the street
        int along = rx ? lz : lx;    // position along the street
        int dist = curbDistance(across);
        if (dist == 1 && lampAt(along)) {
            c.set(GROUND + 1, B.PLAZA_LIGHT);
            c.fill(GROUND + 2, GROUND + LAMP_HEIGHT, B.POLE);
            c.set(GROUND + LAMP_HEIGHT + 1, B.ARM);
            return;
        }
        if (dist == 0 && lampAt(along)) {
            c.set(GROUND + LAMP_HEIGHT + 1, B.ARM);
            return;
        }
        if (dist == 2) {
            int m = Math.floorMod(along - CORRIDOR, 48);
            if (m == 30 && Hash.range(Hash.of(x, z, 5), 3) == 0) {
                c.set(GROUND + 1, B.PLAZA_LIGHT);
                c.set(GROUND + 2, B.HYDRANT);
            } else if (m == 5) {
                c.set(GROUND + 1, B.PLAZA_LIGHT);
                c.set(GROUND + 2, B.TRASH);
            }
        }
    }

    private static void lampArmOverRoad(int lx, int lz, boolean rx, boolean rz, boolean roadX, boolean roadZ, Column c) {
        if (rx && rz) {
            return;
        }
        int across = rx ? lx : lz;
        int along = rx ? lz : lx;
        int r = across - SIDEWALK;
        if ((r == 0 || r == ROAD - 1) && lampAt(along)) {
            c.set(GROUND + LAMP_HEIGHT + 1, B.ARM);
            c.set(GROUND + LAMP_HEIGHT, B.STREET_LIGHT);
        }
    }

    private static void trafficLight(Column c) {
        c.set(GROUND + 1, B.PLAZA_LIGHT);
        c.fill(GROUND + 2, GROUND + 4, B.POLE);
        c.set(GROUND + 5, TRAFFIC_GREEN);
        c.set(GROUND + 6, TRAFFIC_YELLOW);
        c.set(GROUND + 7, TRAFFIC_RED);
        c.set(GROUND + 8, TRAFFIC_BOX);
    }
}
