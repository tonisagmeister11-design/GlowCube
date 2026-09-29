package de.gtacity.gameplay;

/** Tuning knobs for the city simulation. */
public final class CityRules {
    private CityRules() {
    }

    /** Wanted level also works in creative mode (handy for testing). */
    public static final boolean CREATIVE_WANTED = true;

    public static final int PEDESTRIANS_NEAR_PLAYER = 20;
    public static final int TRAFFIC_NEAR_PLAYER = 10;

    /** Officers on the job per star level. They arrive two at a time in patrol cars. */
    public static final int[] POLICE_PER_STAR = {0, 2, 4, 6, 8, 10};

    /** Ticks between two patrol cars sent after the same player. */
    public static final int PATROL_CAR_INTERVAL = 20 * 8;

    /** Patrol cars start this far away, out of sight, and drive to the player. */
    public static final int PATROL_CAR_MIN_DISTANCE = 55;
    public static final int PATROL_CAR_MAX_DISTANCE = 90;
}
