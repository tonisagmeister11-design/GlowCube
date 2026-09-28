package de.gtacity.world;

/** Small deterministic hash helpers so the city looks the same every time a chunk is generated. */
public final class Hash {
    private Hash() {
    }

    public static long of(long a, long b, long salt) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L + 0x27D4EB2F165667C5L;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return h;
    }

    public static long of(long a, long b, long c, long salt) {
        return of(of(a, b, salt), c, salt + 17);
    }

    /** Uniform int in [0, bound). */
    public static int range(long hash, int bound) {
        return (int) Math.floorMod(hash, (long) bound);
    }

    /** Uniform int in [min, max]. */
    public static int between(long hash, int min, int max) {
        return min + range(hash, max - min + 1);
    }

    public static long next(long hash) {
        return of(hash, 0x5DEECE66DL, 99);
    }
}
