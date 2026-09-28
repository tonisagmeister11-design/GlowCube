package de.gtacity.gameplay;

/** Tuning knobs for the city simulation. */
public final class CityRules {
    private CityRules() {
    }

    /** Wanted level also works in creative mode (handy for testing). */
    public static final boolean CREATIVE_WANTED = true;

    public static final int PEDESTRIANS_NEAR_PLAYER = 20;
    public static final int TRAFFIC_NEAR_PLAYER = 10;

    public static final int[] POLICE_PER_STAR = {0, 2, 4, 6, 9, 12};
}
