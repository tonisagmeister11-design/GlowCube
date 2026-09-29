package de.gtacity.entity;

/** All car paint jobs. The texture name maps to textures/entity/car/&lt;texture&gt;.png. */
public enum CarVariant {
    SEDAN_RED(Shape.SEDAN, "sedan_red"),
    SEDAN_BLUE(Shape.SEDAN, "sedan_blue"),
    SEDAN_BLACK(Shape.SEDAN, "sedan_black"),
    SEDAN_WHITE(Shape.SEDAN, "sedan_white"),
    SEDAN_SILVER(Shape.SEDAN, "sedan_silver"),
    SEDAN_GREEN(Shape.SEDAN, "sedan_green"),
    SEDAN_PURPLE(Shape.SEDAN, "sedan_purple"),
    SPORTS_RED(Shape.SPORTS, "sports_red"),
    SPORTS_YELLOW(Shape.SPORTS, "sports_yellow"),
    SPORTS_ORANGE(Shape.SPORTS, "sports_orange"),
    SPORTS_BLUE(Shape.SPORTS, "sports_blue"),
    SPORTS_BLACK(Shape.SPORTS, "sports_black"),
    SPORTS_LIME(Shape.SPORTS, "sports_lime"),
    SUV_BLACK(Shape.SUV, "suv_black"),
    SUV_WHITE(Shape.SUV, "suv_white"),
    SUV_SILVER(Shape.SUV, "suv_silver"),
    SUV_DARKGREEN(Shape.SUV, "suv_darkgreen"),
    SUV_NAVY(Shape.SUV, "suv_navy"),
    POLICE(Shape.SEDAN, "police"),
    TAXI(Shape.SEDAN, "taxi"),
    // New variants go at the end: saved cars store the ordinal.
    SUPER_RED(Shape.SUPER, "super_red"),
    SUPER_ORANGE(Shape.SUPER, "super_orange"),
    SUPER_LIME(Shape.SUPER, "super_lime"),
    SUPER_PEARL(Shape.SUPER, "super_pearl"),
    SUPER_CARBON(Shape.SUPER, "super_carbon"),
    SUPER_MAGENTA(Shape.SUPER, "super_magenta");

    public final Shape shape;
    public final String texture;

    CarVariant(Shape shape, String texture) {
        this.shape = shape;
        this.texture = texture;
    }

    public boolean hasLightBar() {
        return this == POLICE || this == TAXI;
    }

    public static CarVariant byId(int id) {
        CarVariant[] all = values();
        return all[Math.floorMod(id, all.length)];
    }

    /**
     * Car body types with their driving stats. Speeds are in blocks per tick: 1 block/tick = 72 km/h, so the
     * supercar's 4.2 is about 300 km/h.
     */
    public enum Shape {
        SEDAN("Limousine", 1.4F, 0.024F, 4.2F, 60.0F, 1.7F),
        SPORTS("Sportwagen", 2.5F, 0.045F, 4.8F, 55.0F, 1.5F),
        SUV("SUV", 1.3F, 0.022F, 3.6F, 90.0F, 1.9F),
        SUPER("Supersportwagen", 4.2F, 0.075F, 5.0F, 65.0F, 1.3F);

        public final String label;
        public final float maxSpeed;
        public final float accel;
        public final float turn;
        public final float health;
        public final float height;

        Shape(String label, float maxSpeed, float accel, float turn, float health, float height) {
            this.label = label;
            this.maxSpeed = maxSpeed;
            this.accel = accel;
            this.turn = turn;
            this.health = health;
            this.height = height;
        }
    }
}
