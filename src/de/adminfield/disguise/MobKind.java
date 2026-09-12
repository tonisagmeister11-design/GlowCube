package de.adminfield.disguise;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Die Verkleidungen, die im Menue angeboten werden.
 *
 * <p>Entity- und Material-Namen werden absichtlich erst zur Laufzeit aufgeloest. So faellt ein
 * Mob, den die jeweilige Serverversion noch nicht kennt, einfach aus der Liste heraus, statt
 * das ganze Menue mit einem Fehler zu zerlegen.
 */
public enum MobKind {

    // ----- Monster -----
    CREEPER(Group.MONSTER, "CREEPER", "Creeper", "CREEPER_SPAWN_EGG"),
    BABY_ZOMBIE(Group.MONSTER, "ZOMBIE", "Baby-Zombie", "ZOMBIE_SPAWN_EGG", true),
    ZOMBIE(Group.MONSTER, "ZOMBIE", "Zombie", "ZOMBIE_SPAWN_EGG"),
    HUSK(Group.MONSTER, "HUSK", "Wüstenzombie", "HUSK_SPAWN_EGG"),
    DROWNED(Group.MONSTER, "DROWNED", "Ertrunkener", "DROWNED_SPAWN_EGG"),
    SKELETON(Group.MONSTER, "SKELETON", "Skelett", "SKELETON_SPAWN_EGG"),
    STRAY(Group.MONSTER, "STRAY", "Eiswanderer", "STRAY_SPAWN_EGG"),
    BOGGED(Group.MONSTER, "BOGGED", "Sumpfskelett", "BOGGED_SPAWN_EGG"),
    WITHER_SKELETON(Group.MONSTER, "WITHER_SKELETON", "Witherskelett", "WITHER_SKELETON_SPAWN_EGG"),
    SPIDER(Group.MONSTER, "SPIDER", "Spinne", "SPIDER_SPAWN_EGG"),
    CAVE_SPIDER(Group.MONSTER, "CAVE_SPIDER", "Höhlenspinne", "CAVE_SPIDER_SPAWN_EGG"),
    ENDERMAN(Group.MONSTER, "ENDERMAN", "Enderman", "ENDERMAN_SPAWN_EGG"),
    ENDERMITE(Group.MONSTER, "ENDERMITE", "Endermilbe", "ENDERMITE_SPAWN_EGG"),
    SILVERFISH(Group.MONSTER, "SILVERFISH", "Silberfischchen", "SILVERFISH_SPAWN_EGG"),
    WITCH(Group.MONSTER, "WITCH", "Hexe", "WITCH_SPAWN_EGG"),
    BLAZE(Group.MONSTER, "BLAZE", "Lohe", "BLAZE_SPAWN_EGG"),
    GHAST(Group.MONSTER, "GHAST", "Ghast", "GHAST_SPAWN_EGG"),
    SLIME(Group.MONSTER, "SLIME", "Schleim", "SLIME_SPAWN_EGG"),
    MAGMA_CUBE(Group.MONSTER, "MAGMA_CUBE", "Magmawürfel", "MAGMA_CUBE_SPAWN_EGG"),
    PHANTOM(Group.MONSTER, "PHANTOM", "Phantom", "PHANTOM_SPAWN_EGG"),
    VEX(Group.MONSTER, "VEX", "Plagegeist", "VEX_SPAWN_EGG"),
    SHULKER(Group.MONSTER, "SHULKER", "Shulker", "SHULKER_SPAWN_EGG"),
    GUARDIAN(Group.MONSTER, "GUARDIAN", "Wächter", "GUARDIAN_SPAWN_EGG"),
    ELDER_GUARDIAN(Group.MONSTER, "ELDER_GUARDIAN", "Großer Wächter", "ELDER_GUARDIAN_SPAWN_EGG"),
    PIGLIN(Group.MONSTER, "PIGLIN", "Piglin", "PIGLIN_SPAWN_EGG"),
    PIGLIN_BRUTE(Group.MONSTER, "PIGLIN_BRUTE", "Piglin-Barbar", "PIGLIN_BRUTE_SPAWN_EGG"),
    ZOMBIFIED_PIGLIN(Group.MONSTER, "ZOMBIFIED_PIGLIN", "Zombifizierter Piglin", "ZOMBIFIED_PIGLIN_SPAWN_EGG"),
    HOGLIN(Group.MONSTER, "HOGLIN", "Hoglin", "HOGLIN_SPAWN_EGG"),
    ZOGLIN(Group.MONSTER, "ZOGLIN", "Zoglin", "ZOGLIN_SPAWN_EGG"),
    PILLAGER(Group.MONSTER, "PILLAGER", "Plünderer", "PILLAGER_SPAWN_EGG"),
    VINDICATOR(Group.MONSTER, "VINDICATOR", "Diener", "VINDICATOR_SPAWN_EGG"),
    EVOKER(Group.MONSTER, "EVOKER", "Magier", "EVOKER_SPAWN_EGG"),
    RAVAGER(Group.MONSTER, "RAVAGER", "Verwüster", "RAVAGER_SPAWN_EGG"),
    BREEZE(Group.MONSTER, "BREEZE", "Breeze", "BREEZE_SPAWN_EGG"),
    CREAKING(Group.MONSTER, "CREAKING", "Knarzer", "CREAKING_SPAWN_EGG"),
    WARDEN(Group.MONSTER, "WARDEN", "Wärter", "WARDEN_SPAWN_EGG"),
    WITHER(Group.MONSTER, "WITHER", "Wither", "NETHER_STAR"),
    ENDER_DRAGON(Group.MONSTER, "ENDER_DRAGON", "Enderdrache", "DRAGON_EGG"),

    // ----- Tiere -----
    COW(Group.ANIMAL, "COW", "Kuh", "COW_SPAWN_EGG"),
    BABY_COW(Group.ANIMAL, "COW", "Baby-Kuh", "COW_SPAWN_EGG", true),
    MOOSHROOM(Group.ANIMAL, "MOOSHROOM", "Pilzkuh", "MOOSHROOM_SPAWN_EGG"),
    PIG(Group.ANIMAL, "PIG", "Schwein", "PIG_SPAWN_EGG"),
    BABY_PIG(Group.ANIMAL, "PIG", "Baby-Schwein", "PIG_SPAWN_EGG", true),
    SHEEP(Group.ANIMAL, "SHEEP", "Schaf", "SHEEP_SPAWN_EGG"),
    CHICKEN(Group.ANIMAL, "CHICKEN", "Huhn", "CHICKEN_SPAWN_EGG"),
    RABBIT(Group.ANIMAL, "RABBIT", "Kaninchen", "RABBIT_SPAWN_EGG"),
    HORSE(Group.ANIMAL, "HORSE", "Pferd", "HORSE_SPAWN_EGG"),
    DONKEY(Group.ANIMAL, "DONKEY", "Esel", "DONKEY_SPAWN_EGG"),
    MULE(Group.ANIMAL, "MULE", "Maultier", "MULE_SPAWN_EGG"),
    LLAMA(Group.ANIMAL, "LLAMA", "Lama", "LLAMA_SPAWN_EGG"),
    WOLF(Group.ANIMAL, "WOLF", "Wolf", "WOLF_SPAWN_EGG"),
    CAT(Group.ANIMAL, "CAT", "Katze", "CAT_SPAWN_EGG"),
    OCELOT(Group.ANIMAL, "OCELOT", "Ozelot", "OCELOT_SPAWN_EGG"),
    FOX(Group.ANIMAL, "FOX", "Fuchs", "FOX_SPAWN_EGG"),
    PANDA(Group.ANIMAL, "PANDA", "Panda", "PANDA_SPAWN_EGG"),
    POLAR_BEAR(Group.ANIMAL, "POLAR_BEAR", "Eisbär", "POLAR_BEAR_SPAWN_EGG"),
    BEE(Group.ANIMAL, "BEE", "Biene", "BEE_SPAWN_EGG"),
    TURTLE(Group.ANIMAL, "TURTLE", "Schildkröte", "TURTLE_SPAWN_EGG"),
    AXOLOTL(Group.ANIMAL, "AXOLOTL", "Axolotl", "AXOLOTL_SPAWN_EGG"),
    GOAT(Group.ANIMAL, "GOAT", "Ziege", "GOAT_SPAWN_EGG"),
    FROG(Group.ANIMAL, "FROG", "Frosch", "FROG_SPAWN_EGG"),
    SNIFFER(Group.ANIMAL, "SNIFFER", "Schnüffler", "SNIFFER_SPAWN_EGG"),
    CAMEL(Group.ANIMAL, "CAMEL", "Kamel", "CAMEL_SPAWN_EGG"),
    ARMADILLO(Group.ANIMAL, "ARMADILLO", "Gürteltier", "ARMADILLO_SPAWN_EGG"),
    PARROT(Group.ANIMAL, "PARROT", "Papagei", "PARROT_SPAWN_EGG"),
    BAT(Group.ANIMAL, "BAT", "Fledermaus", "BAT_SPAWN_EGG"),
    SQUID(Group.ANIMAL, "SQUID", "Tintenfisch", "SQUID_SPAWN_EGG"),
    GLOW_SQUID(Group.ANIMAL, "GLOW_SQUID", "Leuchttintenfisch", "GLOW_SQUID_SPAWN_EGG"),
    DOLPHIN(Group.ANIMAL, "DOLPHIN", "Delfin", "DOLPHIN_SPAWN_EGG"),
    STRIDER(Group.ANIMAL, "STRIDER", "Schreiter", "STRIDER_SPAWN_EGG"),

    // ----- Besondere -----
    VILLAGER(Group.SPECIAL, "VILLAGER", "Dorfbewohner", "VILLAGER_SPAWN_EGG"),
    BABY_VILLAGER(Group.SPECIAL, "VILLAGER", "Baby-Dorfbewohner", "VILLAGER_SPAWN_EGG", true),
    WANDERING_TRADER(Group.SPECIAL, "WANDERING_TRADER", "Wandernder Händler", "WANDERING_TRADER_SPAWN_EGG"),
    IRON_GOLEM(Group.SPECIAL, "IRON_GOLEM", "Eisengolem", "IRON_BLOCK"),
    SNOW_GOLEM(Group.SPECIAL, "SNOW_GOLEM", "Schneegolem", "CARVED_PUMPKIN"),
    ALLAY(Group.SPECIAL, "ALLAY", "Allay", "ALLAY_SPAWN_EGG"),
    ARMOR_STAND(Group.SPECIAL, "ARMOR_STAND", "Rüstungsständer", "ARMOR_STAND");

    public enum Group {
        MONSTER("Monster", "<red>"),
        ANIMAL("Tiere", "<green>"),
        SPECIAL("Besondere", "<aqua>");

        private final String label;
        private final String color;

        Group(String label, String color) {
            this.label = label;
            this.color = color;
        }

        public String label() {
            return this.label;
        }

        public String color() {
            return this.color;
        }
    }

    private final Group group;
    private final String entityName;
    private final String label;
    private final String iconName;
    private final boolean baby;

    private EntityType resolvedType;
    private Material resolvedIcon;
    private boolean resolved;

    MobKind(Group group, String entityName, String label, String iconName) {
        this(group, entityName, label, iconName, false);
    }

    MobKind(Group group, String entityName, String label, String iconName, boolean baby) {
        this.group = group;
        this.entityName = entityName;
        this.label = label;
        this.iconName = iconName;
        this.baby = baby;
    }

    public Group group() {
        return this.group;
    }

    public String label() {
        return this.label;
    }

    public boolean baby() {
        return this.baby;
    }

    /** Der Mob-Typ dieser Verkleidung, oder {@code null}, wenn der Server ihn nicht kennt. */
    public EntityType entityType() {
        this.resolve();
        return this.resolvedType;
    }

    /** Das Menue-Symbol. Faellt auf ein Ei zurueck, wenn es das Spawn-Ei nicht gibt. */
    public Material icon() {
        this.resolve();
        return this.resolvedIcon;
    }

    public boolean available() {
        return this.entityType() != null;
    }

    private void resolve() {
        if (this.resolved) {
            return;
        }
        this.resolved = true;
        try {
            this.resolvedType = EntityType.valueOf(this.entityName);
        } catch (Throwable ignored) {
            this.resolvedType = null;
        }
        this.resolvedIcon = material(this.iconName);
        if (this.resolvedIcon == null) {
            this.resolvedIcon = material("EGG");
        }
        if (this.resolvedIcon == null) {
            this.resolvedIcon = material("STONE");
        }
    }

    private static Material material(String name) {
        try {
            return Material.valueOf(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Alle Verkleidungen einer Gruppe, die auf diesem Server wirklich existieren. */
    public static List<MobKind> available(Group group) {
        List<MobKind> out = new ArrayList<>();
        for (MobKind kind : values()) {
            if (group != null && kind.group != group) {
                continue;
            }
            if (kind.available()) {
                out.add(kind);
            }
        }
        return out;
    }

    public static MobKind byName(String name) {
        if (name == null) {
            return null;
        }
        for (MobKind kind : values()) {
            if (kind.name().equalsIgnoreCase(name)) {
                return kind;
            }
        }
        return null;
    }
}
