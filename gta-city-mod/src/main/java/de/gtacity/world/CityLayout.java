package de.gtacity.world;

/**
 * The whole city as a pure function of the block position.
 *
 * <p>The city is a grid of {@link #PITCH}-wide cells. Every cell starts with an 18 block street corridor
 * (3 sidewalk + 12 road + 3 sidewalk) followed by a 78x78 block of buildings. The city is surrounded by a
 * promenade, a beach and the ocean; the world border sits in the ocean.
 */
public final class CityLayout {
    private CityLayout() {
    }

    public static final int GROUND = 64;          // y of the road surface block
    public static final int FLOOR = GROUND + 2;   // first free y on building lots
    public static final int SEA_LEVEL = 62;
    public static final int PITCH = 96;
    public static final int SIDEWALK = 3;
    public static final int ROAD = 12;
    public static final int CORRIDOR = SIDEWALK * 2 + ROAD;
    public static final int INNER = PITCH - CORRIDOR;
    public static final int HALF_CELLS = 16;
    public static final int CITY_MIN = -HALF_CELLS * PITCH;
    public static final int CITY_MAX = HALF_CELLS * PITCH + CORRIDOR; // exclusive
    public static final int CENTER = (CITY_MIN + CITY_MAX) / 2;
    public static final int PROMENADE = 6;
    public static final int BEACH = 44;
    public static final int BORDER_MARGIN = 110;
    public static final int BORDER_SIZE = (CITY_MAX - CITY_MIN) + 2 * BORDER_MARGIN;

    public enum District {
        DOWNTOWN("Downtown"), MIDTOWN("Midtown"), RESIDENTIAL("Wohngebiet"), HILLS("Hills"), INDUSTRIAL("Hafen");

        public final String label;

        District(String label) {
            this.label = label;
        }
    }

    public enum LotType {
        SKYSCRAPER, OFFICE, HOUSE, VILLA, WAREHOUSE, CONTAINERS, PARKING, POCKET_PARK, COURTYARD,
        BANK, AMMU_NATION, STORE, POLICE, HOSPITAL, GAS_STATION, CAR_DEALER
    }

    // ------------------------------------------------------------------ grid

    public static int cell(int coord) {
        return Math.floorDiv(coord, PITCH);
    }

    public static int local(int coord) {
        return Math.floorMod(coord, PITCH);
    }

    public static boolean insideCity(int x, int z) {
        return x >= CITY_MIN && x < CITY_MAX && z >= CITY_MIN && z < CITY_MAX;
    }

    /** Distance (in blocks) outside the city rectangle, 0 when inside. Corners are rounded. */
    public static int outsideDistance(int x, int z) {
        int dx = x < CITY_MIN ? CITY_MIN - x : (x >= CITY_MAX ? x - CITY_MAX + 1 : 0);
        int dz = z < CITY_MIN ? CITY_MIN - z : (z >= CITY_MAX ? z - CITY_MAX + 1 : 0);
        if (dx == 0 && dz == 0) {
            return 0;
        }
        return (int) Math.ceil(Math.sqrt((double) dx * dx + (double) dz * dz));
    }

    public static boolean isCorridor(int x, int z) {
        return local(x) < CORRIDOR || local(z) < CORRIDOR;
    }

    /** True if the column is asphalt (driving surface). */
    public static boolean isRoad(int x, int z) {
        if (!insideCity(x, z)) {
            return false;
        }
        int lx = local(x), lz = local(z);
        boolean roadX = lx < CORRIDOR && lx >= SIDEWALK && lx < SIDEWALK + ROAD;
        boolean roadZ = lz < CORRIDOR && lz >= SIDEWALK && lz < SIDEWALK + ROAD;
        return roadX || roadZ;
    }

    /** True if the column is a sidewalk (walking surface for pedestrians). */
    public static boolean isSidewalk(int x, int z) {
        if (!insideCity(x, z)) {
            int d = outsideDistance(x, z);
            return d > 0 && d <= PROMENADE;
        }
        return isCorridor(x, z) && !isRoad(x, z);
    }

    public static boolean isIntersection(int x, int z) {
        return insideCity(x, z) && local(x) < CORRIDOR && local(z) < CORRIDOR;
    }

    // ------------------------------------------------------------------ districts

    public static District district(int gx, int gz) {
        double cx = gx * PITCH + CORRIDOR + INNER / 2.0;
        double cz = gz * PITCH + CORRIDOR + INNER / 2.0;
        double d = Math.sqrt(cx * cx + cz * cz) + (Hash.range(Hash.of(gx, gz, 1), 160) - 80);
        if (d < 430) {
            return District.DOWNTOWN;
        }
        if (cz < -950) {
            return District.HILLS;
        }
        if (cx > 650 && cz > 450) {
            return District.INDUSTRIAL;
        }
        if (d < 880) {
            return District.MIDTOWN;
        }
        return District.RESIDENTIAL;
    }

    public static District districtAt(int x, int z) {
        return district(cell(x), cell(z));
    }

    public static boolean isParkCell(int gx, int gz) {
        if (gx == 2 && gz == -1) {
            return true; // central park right next to the spawn
        }
        if (Math.abs(gx) <= 1 && Math.abs(gz) <= 1) {
            return false;
        }
        int chance = switch (district(gx, gz)) {
            case DOWNTOWN -> 3;
            case MIDTOWN, RESIDENTIAL -> 7;
            case HILLS -> 10;
            case INDUSTRIAL -> 0;
        };
        return Hash.range(Hash.of(gx, gz, 2), 100) < chance;
    }

    public static int lotsPerSide(int gx, int gz) {
        return district(gx, gz) == District.RESIDENTIAL ? 3 : 2;
    }

    /** Hand placed buildings around the spawn intersection so the important shops are close. */
    private static LotType fixedLot(int gx, int gz, int qx, int qz) {
        if (gx == 0 && gz == 0 && qz == 0) {
            return qx == 0 ? LotType.STORE : LotType.AMMU_NATION;
        }
        if (gx == -1 && gz == 0 && qx == 1 && qz == 0) {
            return LotType.BANK;
        }
        if (gx == -1 && gz == 0 && qx == 1 && qz == 1) {
            return LotType.CAR_DEALER;
        }
        if (gx == 0 && gz == -1 && qx == 0 && qz == 1) {
            return LotType.POLICE;
        }
        if (gx == -1 && gz == -1 && qx == 1 && qz == 1) {
            return LotType.HOSPITAL;
        }
        if (gx == 0 && gz == -1 && qx == 1 && qz == 1) {
            return LotType.GAS_STATION;
        }
        return null;
    }

    public static LotType lotType(int gx, int gz, int qx, int qz) {
        LotType fixed = fixedLot(gx, gz, qx, qz);
        if (fixed != null) {
            return fixed;
        }
        int r = Hash.range(Hash.of(gx * 7L + qx, gz * 7L + qz, 3), 100);
        return switch (district(gx, gz)) {
            case DOWNTOWN -> r < 3 ? LotType.BANK
                    : r < 6 ? LotType.AMMU_NATION
                    : r < 10 ? LotType.STORE
                    : r < 12 ? LotType.POCKET_PARK
                    : r < 13 ? LotType.HOSPITAL
                    : LotType.SKYSCRAPER;
            case MIDTOWN -> r < 4 ? LotType.BANK
                    : r < 9 ? LotType.AMMU_NATION
                    : r < 16 ? LotType.STORE
                    : r < 18 ? LotType.POLICE
                    : r < 19 ? LotType.HOSPITAL
                    : r < 27 ? LotType.PARKING
                    : r < 32 ? LotType.GAS_STATION
                    : r < 34 ? LotType.CAR_DEALER
                    : r < 38 ? LotType.POCKET_PARK
                    : LotType.OFFICE;
            case RESIDENTIAL -> (qx == 1 && qz == 1) ? LotType.COURTYARD
                    : r < 5 ? LotType.STORE
                    : r < 7 ? LotType.GAS_STATION
                    : r < 8 ? LotType.POLICE
                    : r < 9 ? LotType.AMMU_NATION
                    : LotType.HOUSE;
            case HILLS -> r < 10 ? LotType.POCKET_PARK : LotType.VILLA;
            case INDUSTRIAL -> r < 40 ? LotType.WAREHOUSE
                    : r < 72 ? LotType.CONTAINERS
                    : r < 84 ? LotType.PARKING
                    : r < 87 ? LotType.POLICE
                    : r < 90 ? LotType.GAS_STATION
                    : LotType.WAREHOUSE;
        };
    }

    /** The lot containing the given column, or null for streets, parks and everything outside the city. */
    public static Lot lotAt(int x, int z) {
        if (!insideCity(x, z) || isCorridor(x, z)) {
            return null;
        }
        int gx = cell(x), gz = cell(z);
        if (isParkCell(gx, gz)) {
            return null;
        }
        int n = lotsPerSide(gx, gz);
        int size = INNER / n;
        int bx = local(x) - CORRIDOR, bz = local(z) - CORRIDOR;
        return new Lot(gx, gz, Math.min(bx / size, n - 1), Math.min(bz / size, n - 1), n);
    }

    // ------------------------------------------------------------------ generation

    public static void fillColumn(int x, int z, Column c) {
        c.set(c.minY, B.BEDROCK);
        int d = outsideDistance(x, z);
        if (d > 0) {
            Nature.coast(x, z, d, c);
            return;
        }
        c.fill(c.minY + 1, Math.min(-1, GROUND - 1), B.DEEPSLATE);
        c.fill(0, GROUND - 1, B.STONE);

        int lx = local(x), lz = local(z);
        boolean rx = lx < CORRIDOR, rz = lz < CORRIDOR;
        if (rx || rz) {
            Streets.column(x, z, lx, lz, rx, rz, c);
            return;
        }
        int gx = cell(x), gz = cell(z);
        if (isParkCell(gx, gz)) {
            Nature.park(gx, gz, x, z, c);
            return;
        }
        Lot lot = lotAt(x, z);
        Buildings.column(lot, x, z, c);
    }

    public static String describe(int x, int z) {
        if (!insideCity(x, z)) {
            return outsideDistance(x, z) <= PROMENADE ? "Promenade" : "Strand / Meer";
        }
        int gx = cell(x), gz = cell(z);
        String street = "Block " + gx + "/" + gz + " (" + district(gx, gz).label + ")";
        if (isCorridor(x, z)) {
            return street + " - Strasse";
        }
        if (isParkCell(gx, gz)) {
            return street + " - Park";
        }
        Lot lot = lotAt(x, z);
        return street + " - " + (lot == null ? "?" : lot.type.name());
    }
}
