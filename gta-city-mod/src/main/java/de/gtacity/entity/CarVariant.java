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
    TAXI(Shape.SEDAN, "taxi");

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

    /** Car body types with their driving stats (speeds in blocks per tick). */
    public enum Shape {
        SEDAN("Limousine", 1.0F, 0.020F, 4.2F, 60.0F, 1.7F),
        SPORTS("Sportwagen", 1.55F, 0.032F, 4.8F, 50.0F, 1.5F),
        SUV("SUV", 0.95F, 0.018F, 3.6F, 90.0F, 1.9F);

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
