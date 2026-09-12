/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import java.util.List;
import org.bukkit.Material;

public enum DeathFate {
    NACHTWACHE("Nachtwache", Material.ZOMBIE_HEAD, "Ein paar Zombies mehr als üblich finden ihn.", "Nachts, unter freiem Himmel"),
    SKELETT_PATROUILLE("Skelett-Patrouille", Material.SKELETON_SKULL, "Bogenschützen aus der Dunkelheit.", "Nachts oder im Dunkeln"),
    LEISER_CREEPER("Leiser Creeper", Material.CREEPER_HEAD, "Einer steht plötzlich hinter ihm.", "Im Dunkeln"),
    SPINNENNEST("Spinnennest", Material.SPIDER_EYE, "Höhlenspinnen aus einem Nest in der Nähe.", "Unter Tage"),
    ENDERMANN("Endermann", Material.ENDER_PEARL, "Er hat wohl zu lange hingeschaut.", "Nachts oder unter Tage"),
    ERTRUNKENE("Ertrunkene", Material.TRIDENT, "Drowned steigen aus dem Wasser.", "Im Wasser"),
    PHANTOME("Phantome", Material.PHANTOM_MEMBRANE, "Wer nicht schläft, wird geholt.", "Nachts, unter freiem Himmel"),
    GEWITTERSCHLAG("Gewitterschlag", Material.LIGHTNING_ROD, "Der Blitz sucht sich den höchsten Punkt.", "Bei Gewitter, unter freiem Himmel"),
    KIESRUTSCH("Kiesrutsch", Material.GRAVEL, "Die Decke gibt nach.", "Unter Tage, mit Decke über dem Kopf"),
    ZEHRENDER_HUNGER("Zehrender Hunger", Material.ROTTEN_FLESH, "Der Hunger frisst schneller als sonst.", "Wenn der Hungerbalken nicht voll ist"),
    LUFT_WIRD_KNAPP("Luft wird knapp", Material.KELP, "Die Luft reicht plötzlich nicht mehr.", "Unter Wasser"),
    HEXENBESUCH("Hexenbesuch", Material.POTION, "Eine Hexe mit vollem Beutel.", "Nachts oder im Sumpf"),
    WOLFSRUDEL("Wolfsrudel", Material.BONE, "Das Rudel hält ihn für Beute.", "In Wald oder Taiga"),
    BIENENSCHWARM("Bienenschwarm", Material.HONEYCOMB, "Er ist einem Nest zu nahe gekommen.", "Tagsüber, in Blumen- und Waldbiomen"),
    WUESTENWACHE("Wüstenwache", Material.SAND, "Husks aus dem Sand.", "Nachts in der Wüste"),
    NETHER_HINTERHALT("Nether-Hinterhalt", Material.CRIMSON_FUNGUS, "Hoglins und ein Brute, der schlechte Laune hat.", "Im Nether"),
    FESTUNGSWACHE("Festungswache", Material.NETHER_BRICK, "Witherskelette und ein Blaze.", "Im Nether, in dunkler Umgebung"),
    ILLAGER_PATROUILLE("Illager-Patrouille", Material.CROSSBOW, "Eine Patrouille kreuzt seinen Weg.", "Draußen in der Oberwelt"),
    SILBERFISCHSCHWARM("Silberfischschwarm", Material.INFESTED_STONE, "Er hat den falschen Stein erwischt.", "Unter Tage im Stein"),
    WAECHTER_DER_TIEFE("Wächter der Tiefe", Material.PRISMARINE_SHARD, "Guardians aus dem tiefen Wasser.", "In tiefem Wasser"),
    FROSTNACHT("Frostnacht", Material.POWDER_SNOW_BUCKET, "Die Kälte kriecht schneller unter die Haut.", "In verschneiten Biomen"),
    MAGMAWUERFEL("Magmawürfel", Material.MAGMA_CREAM, "Sie kommen aus den Spalten.", "Im Nether");

    private final String label;
    private final Material icon;
    private final String description;
    private final String condition;

    private DeathFate(String string2, Material material, String string3, String string4) {
        this.label = string2;
        this.icon = material;
        this.description = string3;
        this.condition = string4;
    }

    public String label() {
        return this.label;
    }

    public Material icon() {
        return this.icon;
    }

    public String description() {
        return this.description;
    }

    public String condition() {
        return this.condition;
    }

    public static DeathFate byName(String string) {
        if (string == null) {
            return null;
        }
        for (DeathFate deathFate : DeathFate.values()) {
            if (!deathFate.name().equalsIgnoreCase(string)) continue;
            return deathFate;
        }
        return null;
    }

    public static List<DeathFate> all() {
        return List.of(DeathFate.values());
    }
}

