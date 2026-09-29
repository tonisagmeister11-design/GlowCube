package de.gtacity.client.map;

import org.jetbrains.annotations.Nullable;

/** The player's own map marker (set by clicking on the big map). Client only, like in GTA. */
public final class Waypoint {
    private Waypoint() {
    }

    private static double @Nullable [] target;

    public static double @Nullable [] get() {
        return target;
    }

    public static void set(double x, double z) {
        target = new double[]{x, z};
    }

    public static void clear() {
        target = null;
    }
}
