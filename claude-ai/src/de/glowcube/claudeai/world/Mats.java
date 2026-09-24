package de.glowcube.claudeai.world;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.Material;

/**
 * Materialien werden nur ueber ihren Namen angesprochen, nie als Konstante. So laeuft
 * das Plugin auch, wenn Mojang einmal einen Block umbenennt - der fehlt dann einfach.
 */
public final class Mats {

    private static final Map<String, Material> CACHE = new HashMap<>();

    private Mats() {}

    /** Material zum Namen, oder null wenn es das in dieser Version nicht gibt. */
    public static Material get(String name) {
        String key = name.toUpperCase(Locale.ROOT);
        if (CACHE.containsKey(key)) return CACHE.get(key);
        Material m = Material.getMaterial(key);
        CACHE.put(key, m);
        return m;
    }

    public static boolean is(Material m, String name) {
        return m != null && m.name().equals(name);
    }

    public static Set<Material> set(String... names) {
        Set<Material> out = new LinkedHashSet<>();
        for (String n : names) {
            Material m = get(n);
            if (m != null) out.add(m);
        }
        return out;
    }

    public static String n(Material m) {
        return m == null ? "" : m.name();
    }

    // ------------------------------------------------------------------ Gruppen

    public static boolean isLog(Material m) {
        String n = n(m);
        if (n.startsWith("STRIPPED_")) return false;
        return n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_HYPHAE")
                || n.equals("CRIMSON_STEM") || n.equals("WARPED_STEM");
    }

    public static boolean isPlanks(Material m) {
        return n(m).endsWith("_PLANKS");
    }

    public static boolean isLeaves(Material m) {
        return n(m).endsWith("_LEAVES");
    }

    public static boolean isOre(Material m) {
        String n = n(m);
        return n.endsWith("_ORE") || n.equals("ANCIENT_DEBRIS");
    }

    public static boolean isWoodenDoorLike(Material m) {
        String n = n(m);
        return (n.endsWith("_DOOR") || n.endsWith("_FENCE_GATE") || n.endsWith("_TRAPDOOR")) && !n.startsWith("IRON_");
    }

    public static boolean isFenceLike(Material m) {
        String n = n(m);
        return n.endsWith("_FENCE") || n.endsWith("_WALL") || n.endsWith("_FENCE_GATE");
    }

    /** Planke passend zum Stamm: OAK_LOG -> OAK_PLANKS, CRIMSON_STEM -> CRIMSON_PLANKS. */
    public static Material planksFor(Material log) {
        String n = n(log).replace("STRIPPED_", "");
        for (String suffix : new String[] { "_LOG", "_WOOD", "_STEM", "_HYPHAE" }) {
            if (n.endsWith(suffix)) {
                Material p = get(n.substring(0, n.length() - suffix.length()) + "_PLANKS");
                if (p != null) return p;
            }
        }
        return get("OAK_PLANKS");
    }

    /** Essbar und nicht giftig. */
    public static boolean isFood(Material m) {
        if (m == null || !m.isEdible()) return false;
        String n = m.name();
        return !n.equals("ROTTEN_FLESH") && !n.equals("SPIDER_EYE") && !n.equals("POISONOUS_POTATO")
                && !n.equals("PUFFERFISH") && !n.equals("CHORUS_FRUIT") && !n.equals("SUSPICIOUS_STEW");
    }

    public static Predicate<Material> named(Set<Material> set) {
        return set::contains;
    }

    /** Werkzeugart eines Items: "pickaxe", "axe", "shovel", "hoe", "sword" oder null. */
    public static String toolKind(Material m) {
        String n = n(m);
        if (n.endsWith("_PICKAXE")) return "pickaxe";
        if (n.endsWith("_AXE")) return "axe";
        if (n.endsWith("_SHOVEL")) return "shovel";
        if (n.endsWith("_HOE")) return "hoe";
        if (n.endsWith("_SWORD")) return "sword";
        return null;
    }

    /** 0 = keins, 1 Holz/Gold, 2 Stein, 3 Eisen, 4 Diamant, 5 Netherit. */
    public static int toolTier(Material m) {
        String n = n(m);
        if (n.startsWith("NETHERITE_")) return 5;
        if (n.startsWith("DIAMOND_")) return 4;
        if (n.startsWith("IRON_")) return 3;
        if (n.startsWith("STONE_")) return 2;
        if (n.startsWith("WOODEN_") || n.startsWith("GOLDEN_")) return 1;
        return 0;
    }

    /** Abbautempo-Faktor wie im Spiel. */
    public static double toolSpeed(Material m) {
        String n = n(m);
        if (n.startsWith("GOLDEN_")) return 12;
        if (n.startsWith("NETHERITE_")) return 9;
        if (n.startsWith("DIAMOND_")) return 8;
        if (n.startsWith("IRON_")) return 6;
        if (n.startsWith("STONE_")) return 4;
        if (n.startsWith("WOODEN_")) return 2;
        return 1;
    }

    public static final String[] TIER_PREFIX = { "", "WOODEN_", "STONE_", "IRON_", "DIAMOND_", "NETHERITE_" };
}
