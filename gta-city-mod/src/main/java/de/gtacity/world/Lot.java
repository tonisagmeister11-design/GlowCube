package de.gtacity.world;

import net.minecraft.core.Direction;

/** One building plot inside a city cell. All coordinates are world coordinates, bounds inclusive. */
public final class Lot {
    public final int gx, gz, qx, qz, n, size;
    public final int x0, z0, x1, z1;
    public final long seed;
    public final CityLayout.LotType type;
    public final CityLayout.District district;
    /** Side of the lot that faces a street (the entrance side). Null for inner courtyards. */
    public final Direction front;

    public Lot(int gx, int gz, int qx, int qz, int n) {
        this.gx = gx;
        this.gz = gz;
        this.qx = qx;
        this.qz = qz;
        this.n = n;
        this.size = CityLayout.INNER / n;
        this.x0 = gx * CityLayout.PITCH + CityLayout.CORRIDOR + qx * size;
        this.z0 = gz * CityLayout.PITCH + CityLayout.CORRIDOR + qz * size;
        this.x1 = x0 + size - 1;
        this.z1 = z0 + size - 1;
        this.seed = Hash.of(gx * 31L + qx, gz * 31L + qz, 7);
        this.type = CityLayout.lotType(gx, gz, qx, qz);
        this.district = CityLayout.district(gx, gz);
        this.front = pickFront();
    }

    private Direction pickFront() {
        boolean north = qz == 0, south = qz == n - 1, west = qx == 0, east = qx == n - 1;
        Direction[] options = new Direction[4];
        int count = 0;
        if (north) options[count++] = Direction.NORTH;
        if (south) options[count++] = Direction.SOUTH;
        if (west) options[count++] = Direction.WEST;
        if (east) options[count++] = Direction.EAST;
        if (count == 0) {
            return null;
        }
        return options[Hash.range(seed >>> 5, count)];
    }

    public int centerX() {
        return (x0 + x1) / 2;
    }

    public int centerZ() {
        return (z0 + z1) / 2;
    }

    public long seed(int salt) {
        return Hash.of(seed, salt, 11);
    }

    /** Local coordinate frame with the street in front, see {@link Frame}. */
    public Frame frame() {
        return new Frame(x0, z0, x1, z1, front == null ? Direction.SOUTH : front);
    }

    /**
     * Rotated view of a rectangle so building code can be written once. {@code v = 0} is the row touching the
     * street, {@code u} grows from left to right as seen by someone standing on the street and looking at the lot.
     */
    public static final class Frame {
        public final int x0, z0, x1, z1;
        public final Direction front;
        public final int width, depth;

        public Frame(int x0, int z0, int x1, int z1, Direction front) {
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.front = front;
            boolean alongX = front == Direction.NORTH || front == Direction.SOUTH;
            this.width = alongX ? x1 - x0 + 1 : z1 - z0 + 1;
            this.depth = alongX ? z1 - z0 + 1 : x1 - x0 + 1;
        }

        public int u(int x, int z) {
            return switch (front) {
                case NORTH -> x1 - x;
                case SOUTH -> x - x0;
                case WEST -> z - z0;
                default -> z1 - z;
            };
        }

        public int v(int x, int z) {
            return switch (front) {
                case NORTH -> z - z0;
                case SOUTH -> z1 - z;
                case WEST -> x - x0;
                default -> x1 - x;
            };
        }

        /** World x of local (u, v). */
        public int x(int u, int v) {
            return switch (front) {
                case NORTH -> x1 - u;
                case SOUTH -> x0 + u;
                case WEST -> x0 + v;
                default -> x1 - v;
            };
        }

        /** World z of local (u, v). */
        public int z(int u, int v) {
            return switch (front) {
                case NORTH -> z0 + v;
                case SOUTH -> z1 - v;
                case WEST -> z0 + u;
                default -> z1 - u;
            };
        }

        /** World direction pointing from the lot to the street. */
        public Direction out() {
            return front;
        }

        /** World direction of growing u. */
        public Direction right() {
            return front.getCounterClockWise();
        }
    }
}
