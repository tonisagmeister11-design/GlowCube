package de.glowcube.claudeai.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.Material;

import de.glowcube.claudeai.world.Mats;

/**
 * Claudes Rezeptbuch: was man craften kann, was man schmelzen kann und wo Rohstoffe
 * herkommen (abbauen, jagen, ernten). Alles ueber Namen, damit fehlende Items einfach
 * wegfallen.
 */
public final class Recipes {

    /** Eine Zutat: welche Items passen, wie viele, und was man notfalls beschafft. */
    public record Ingredient(Predicate<Material> match, int count, Material fallback, String label) {}

    public record Recipe(Material output, int amount, List<Ingredient> ingredients, boolean table) {}

    public record Smelt(Material output, Material input) {}

    /** Wie man an einen Rohstoff kommt. */
    public enum Method { MINE, TREE, HUNT, HARVEST }

    public record Source(Material item, Method method, Predicate<Material> blocks, List<String> mobs, int pickaxeTier,
            String label) {}

    private static final Map<Material, Recipe> CRAFT = new LinkedHashMap<>();
    private static final Map<Material, Smelt> SMELT = new LinkedHashMap<>();
    private static final Map<Material, Source> SOURCES = new LinkedHashMap<>();

    /** Bretter aus irgendeinem Stamm - die Sorte ergibt sich erst beim Craften. */
    public static final Recipe ANY_PLANKS = new Recipe(Mats.get("OAK_PLANKS"), 4,
            List.of(new Ingredient(Mats::isLog, 1, Mats.get("OAK_LOG"), "LOG")), false);

    private Recipes() {}

    public static Recipe recipe(Material m) {
        return CRAFT.get(m);
    }

    public static Smelt smelt(Material m) {
        return SMELT.get(m);
    }

    public static Source source(Material m) {
        return SOURCES.get(m);
    }

    public static Map<Material, Recipe> allRecipes() {
        return CRAFT;
    }

    // ================================================================== Aufbau

    private static Ingredient ing(String name, int count) {
        Material m = Mats.get(name);
        return new Ingredient(x -> x == m, count, m, name);
    }

    private static final Predicate<Material> PLANKS_MATCH = Mats::isPlanks;
    private static final Predicate<Material> ANY_LOG = Mats::isLog;
    private static final Predicate<Material> COAL_LIKE = m -> Mats.is(m, "COAL") || Mats.is(m, "CHARCOAL");
    private static final Predicate<Material> COBBLE_LIKE = m -> Mats.is(m, "COBBLESTONE") || Mats.is(m, "COBBLED_DEEPSLATE")
            || Mats.is(m, "BLACKSTONE");

    private static Ingredient planks(int n) {
        return new Ingredient(PLANKS_MATCH, n, Mats.get("OAK_PLANKS"), "PLANKS");
    }

    private static Ingredient sticks(int n) {
        return ing("STICK", n);
    }

    private static void craft(String out, int amount, boolean table, Ingredient... ingredients) {
        Material m = Mats.get(out);
        if (m == null) return;
        for (Ingredient i : ingredients) if (i.fallback() == null) return;
        CRAFT.put(m, new Recipe(m, amount, List.of(ingredients), table));
    }

    private static void smelt(String out, String in) {
        Material o = Mats.get(out), i = Mats.get(in);
        if (o != null && i != null) SMELT.put(o, new Smelt(o, i));
    }

    private static void mine(String item, int tier, String label, String... blocks) {
        Material m = Mats.get(item);
        if (m == null) return;
        var set = Mats.set(blocks);
        SOURCES.put(m, new Source(m, Method.MINE, set::contains, List.of(), tier, label));
    }

    private static void hunt(String item, String label, String... mobs) {
        Material m = Mats.get(item);
        if (m == null) return;
        SOURCES.put(m, new Source(m, Method.HUNT, x -> false, List.of(mobs), 0, label));
    }

    private static void harvest(String item, String label, String... crops) {
        Material m = Mats.get(item);
        if (m == null) return;
        var set = Mats.set(crops);
        SOURCES.put(m, new Source(m, Method.HARVEST, set::contains, List.of(), 0, label));
    }

    static {
        // Holz: jeder Stamm gibt Bretter seiner Sorte
        for (String wood : new String[] { "OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY",
                "PALE_OAK", "CRIMSON", "WARPED", "BAMBOO" }) {
            Material planks = Mats.get(wood + "_PLANKS");
            if (planks == null) continue;
            String logName = wood.equals("CRIMSON") || wood.equals("WARPED") ? wood + "_STEM"
                    : wood.equals("BAMBOO") ? "BAMBOO_BLOCK" : wood + "_LOG";
            Material log = Mats.get(logName);
            if (log == null) continue;
            String prefix = wood;
            Predicate<Material> sameWood = m -> Mats.isLog(m) && m.name().startsWith(prefix + "_")
                    || m.name().equals(logName);
            CRAFT.put(planks, new Recipe(planks, 4, List.of(new Ingredient(sameWood, 1, log, logName)), false));
            SOURCES.put(log, new Source(log, Method.TREE, x -> x == log, List.of(), 0, logName));
        }
        craft("STICK", 4, false, planks(2));
        craft("CRAFTING_TABLE", 1, false, planks(4));
        craft("CHEST", 1, true, planks(8));
        craft("BARREL", 1, true, planks(6), new Ingredient(m -> Mats.n(m).endsWith("_SLAB"), 2, Mats.get("OAK_SLAB"), "OAK_SLAB"));
        craft("OAK_SLAB", 6, true, planks(3));
        craft("FURNACE", 1, true, new Ingredient(COBBLE_LIKE, 8, Mats.get("COBBLESTONE"), "COBBLESTONE"));
        craft("TORCH", 4, false, new Ingredient(COAL_LIKE, 1, Mats.get("COAL"), "COAL"), sticks(1));
        craft("LADDER", 3, true, sticks(7));
        craft("OAK_DOOR", 3, true, planks(6));
        craft("OAK_FENCE", 3, true, planks(4), sticks(2));
        craft("OAK_FENCE_GATE", 1, true, sticks(4), planks(2));
        craft("BOWL", 4, true, planks(3));
        craft("BREAD", 1, true, ing("WHEAT", 3));
        craft("WHITE_BED", 1, true, ing("WHITE_WOOL", 3), planks(3));
        craft("BOOK", 1, false, ing("PAPER", 3), ing("LEATHER", 1));
        craft("PAPER", 3, true, ing("SUGAR_CANE", 3));
        craft("BUCKET", 1, true, ing("IRON_INGOT", 3));
        craft("SHIELD", 1, true, planks(6), ing("IRON_INGOT", 1));
        craft("BOW", 1, true, sticks(3), ing("STRING", 3));
        craft("ARROW", 4, true, ing("FLINT", 1), sticks(1), ing("FEATHER", 1));
        craft("FISHING_ROD", 1, true, sticks(3), ing("STRING", 2));
        craft("SHEARS", 1, false, ing("IRON_INGOT", 2));
        craft("STONE_BRICKS", 4, false, ing("STONE", 4));
        craft("GLASS_PANE", 16, true, ing("GLASS", 6));
        craft("IRON_BLOCK", 1, true, ing("IRON_INGOT", 9));
        craft("GOLD_BLOCK", 1, true, ing("GOLD_INGOT", 9));
        craft("DIAMOND_BLOCK", 1, true, ing("DIAMOND", 9));
        craft("ANVIL", 1, true, ing("IRON_BLOCK", 3), ing("IRON_INGOT", 4));
        craft("ENCHANTING_TABLE", 1, true, ing("BOOK", 1), ing("DIAMOND", 2), ing("OBSIDIAN", 4));
        craft("BOOKSHELF", 1, true, planks(6), ing("BOOK", 3));
        craft("CAMPFIRE", 1, true, sticks(3), new Ingredient(COAL_LIKE, 1, Mats.get("COAL"), "COAL"),
                new Ingredient(ANY_LOG, 3, Mats.get("OAK_LOG"), "OAK_LOG"));
        craft("LANTERN", 1, true, ing("IRON_NUGGET", 8), ing("TORCH", 1));
        craft("IRON_NUGGET", 9, false, ing("IRON_INGOT", 1));
        craft("COOKIE", 8, true, ing("WHEAT", 2), ing("COCOA_BEANS", 1));
        craft("CAKE", 1, true, ing("MILK_BUCKET", 3), ing("SUGAR", 2), ing("EGG", 1), ing("WHEAT", 3));
        craft("SUGAR", 1, false, ing("SUGAR_CANE", 1));

        // Werkzeuge und Ruestung in allen Stufen
        Object[][] tiers = {
                { "WOODEN", new Ingredient(PLANKS_MATCH, 1, Mats.get("OAK_PLANKS"), "PLANKS") },
                { "STONE", new Ingredient(COBBLE_LIKE, 1, Mats.get("COBBLESTONE"), "COBBLESTONE") },
                { "IRON", ing("IRON_INGOT", 1) },
                { "GOLDEN", ing("GOLD_INGOT", 1) },
                { "DIAMOND", ing("DIAMOND", 1) } };
        for (Object[] t : tiers) {
            String p = (String) t[0];
            Ingredient base = (Ingredient) t[1];
            craft(p + "_PICKAXE", 1, true, times(base, 3), sticks(2));
            craft(p + "_AXE", 1, true, times(base, 3), sticks(2));
            craft(p + "_SHOVEL", 1, true, times(base, 1), sticks(2));
            craft(p + "_SWORD", 1, true, times(base, 2), sticks(1));
            craft(p + "_HOE", 1, true, times(base, 2), sticks(2));
        }
        Object[][] armor = {
                { "LEATHER", ing("LEATHER", 1) }, { "IRON", ing("IRON_INGOT", 1) },
                { "GOLDEN", ing("GOLD_INGOT", 1) }, { "DIAMOND", ing("DIAMOND", 1) } };
        for (Object[] a : armor) {
            String p = (String) a[0];
            Ingredient base = (Ingredient) a[1];
            craft(p + "_HELMET", 1, true, times(base, 5));
            craft(p + "_CHESTPLATE", 1, true, times(base, 8));
            craft(p + "_LEGGINGS", 1, true, times(base, 7));
            craft(p + "_BOOTS", 1, true, times(base, 4));
        }

        // Schmelzen
        smelt("IRON_INGOT", "RAW_IRON");
        smelt("GOLD_INGOT", "RAW_GOLD");
        smelt("COPPER_INGOT", "RAW_COPPER");
        smelt("GLASS", "SAND");
        smelt("STONE", "COBBLESTONE");
        smelt("SMOOTH_STONE", "STONE");
        smelt("CHARCOAL", "OAK_LOG");
        smelt("COOKED_BEEF", "BEEF");
        smelt("COOKED_PORKCHOP", "PORKCHOP");
        smelt("COOKED_CHICKEN", "CHICKEN");
        smelt("COOKED_MUTTON", "MUTTON");
        smelt("COOKED_RABBIT", "RABBIT");
        smelt("COOKED_COD", "COD");
        smelt("COOKED_SALMON", "SALMON");
        smelt("BAKED_POTATO", "POTATO");
        smelt("BRICK", "CLAY_BALL");
        smelt("DRIED_KELP", "KELP");

        // Rohstoffe aus der Welt
        mine("COBBLESTONE", 1, "Stein", "STONE", "COBBLESTONE");
        mine("COBBLED_DEEPSLATE", 1, "Tiefenschiefer", "DEEPSLATE", "COBBLED_DEEPSLATE");
        mine("DIRT", 0, "Erde", "DIRT", "GRASS_BLOCK", "COARSE_DIRT", "ROOTED_DIRT");
        mine("SAND", 0, "Sand", "SAND");
        mine("RED_SAND", 0, "roten Sand", "RED_SAND");
        mine("GRAVEL", 0, "Kies", "GRAVEL");
        mine("FLINT", 0, "Feuerstein", "GRAVEL");
        mine("CLAY_BALL", 0, "Ton", "CLAY");
        mine("COAL", 1, "Kohle", "COAL_ORE", "DEEPSLATE_COAL_ORE");
        mine("RAW_IRON", 2, "Eisenerz", "IRON_ORE", "DEEPSLATE_IRON_ORE");
        mine("RAW_COPPER", 2, "Kupfererz", "COPPER_ORE", "DEEPSLATE_COPPER_ORE");
        mine("RAW_GOLD", 3, "Golderz", "GOLD_ORE", "DEEPSLATE_GOLD_ORE");
        mine("DIAMOND", 3, "Diamanten", "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE");
        mine("REDSTONE", 3, "Redstone", "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE");
        mine("LAPIS_LAZULI", 2, "Lapislazuli", "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE");
        mine("EMERALD", 3, "Smaragde", "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE");
        mine("QUARTZ", 1, "Quarz", "NETHER_QUARTZ_ORE");
        mine("OBSIDIAN", 4, "Obsidian", "OBSIDIAN");
        mine("NETHERRACK", 1, "Netherrack", "NETHERRACK");
        mine("SNOWBALL", 0, "Schnee", "SNOW_BLOCK", "SNOW");
        mine("ICE", 0, "Eis", "ICE");
        mine("SUGAR_CANE", 0, "Zuckerrohr", "SUGAR_CANE");
        mine("WHEAT_SEEDS", 0, "Samen", "SHORT_GRASS", "TALL_GRASS", "GRASS");
        mine("PUMPKIN", 0, "Kuerbisse", "PUMPKIN");
        mine("MELON_SLICE", 0, "Melonen", "MELON");
        mine("APPLE", 0, "Aepfel", "OAK_LEAVES", "DARK_OAK_LEAVES");
        mine("STICK", 0, "Stoecke", "DEAD_BUSH");
        mine("KELP", 0, "Seetang", "KELP", "KELP_PLANT");
        mine("GLOWSTONE_DUST", 0, "Glowstone", "GLOWSTONE");

        hunt("BEEF", "Kuehe", "COW");
        hunt("LEATHER", "Kuehe", "COW");
        hunt("PORKCHOP", "Schweine", "PIG");
        hunt("CHICKEN", "Huehner", "CHICKEN");
        hunt("FEATHER", "Huehner", "CHICKEN");
        hunt("EGG", "Huehner", "CHICKEN");
        hunt("MUTTON", "Schafe", "SHEEP");
        hunt("WHITE_WOOL", "Schafe", "SHEEP");
        hunt("RABBIT", "Hasen", "RABBIT");
        hunt("BONE", "Skelette", "SKELETON", "STRAY", "BOGGED");
        hunt("ARROW", "Skelette", "SKELETON", "STRAY");
        hunt("STRING", "Spinnen", "SPIDER", "CAVE_SPIDER");
        hunt("SPIDER_EYE", "Spinnen", "SPIDER", "CAVE_SPIDER");
        hunt("GUNPOWDER", "Creeper", "CREEPER");
        hunt("ROTTEN_FLESH", "Zombies", "ZOMBIE", "HUSK", "DROWNED");
        hunt("ENDER_PEARL", "Endermen", "ENDERMAN");
        hunt("SLIME_BALL", "Schleime", "SLIME");
        hunt("BLAZE_ROD", "Lohen", "BLAZE");
        hunt("COD", "Kabeljau", "COD");
        hunt("SALMON", "Lachs", "SALMON");
        hunt("INK_SAC", "Tintenfische", "SQUID");

        harvest("WHEAT", "Weizen", "WHEAT");
        harvest("CARROT", "Karotten", "CARROTS");
        harvest("POTATO", "Kartoffeln", "POTATOES");
        harvest("BEETROOT", "Rote Bete", "BEETROOTS");
        harvest("NETHER_WART", "Netherwarzen", "NETHER_WART");
    }

    private static Ingredient times(Ingredient base, int n) {
        return new Ingredient(base.match(), n, base.fallback(), base.label());
    }

    /** Welche Items sind Brennstoff und wie viele Items schmilzt eins davon? */
    public static double fuelValue(Material m) {
        String n = Mats.n(m);
        return switch (n) {
            case "COAL", "CHARCOAL" -> 8;
            case "COAL_BLOCK" -> 80;
            case "BLAZE_ROD" -> 12;
            case "LAVA_BUCKET" -> 100;
            case "DRIED_KELP_BLOCK" -> 20;
            case "STICK" -> 0.5;
            default -> Mats.isPlanks(m) || Mats.isLog(m) ? 1.5 : n.endsWith("_SLAB") && n.contains("OAK") ? 0.75 : 0;
        };
    }

    /** Menschenlesbares Rezept fuer "wie crafte ich ...?" */
    public static List<String> describe(Material m, java.util.function.Function<Material, String> name) {
        List<String> out = new ArrayList<>();
        Recipe r = CRAFT.get(m);
        if (r != null) {
            List<String> parts = new ArrayList<>();
            for (Ingredient i : r.ingredients()) {
                String label = i.label().equals("PLANKS") ? "Bretter" : name.apply(i.fallback());
                parts.add(i.count() + "x " + label);
            }
            out.add(String.join(" + ", parts) + " ergibt " + r.amount() + "x " + name.apply(m)
                    + (r.table() ? " (an der Werkbank)" : " (geht auch im Inventar)"));
        }
        Smelt s = SMELT.get(m);
        if (s != null) out.add(name.apply(s.input()) + " im Ofen schmelzen ergibt " + name.apply(m));
        Source src = SOURCES.get(m);
        if (src != null && r == null) {
            switch (src.method()) {
                case MINE, TREE -> out.add("Bekommst du beim Abbauen von " + src.label()
                        + (src.pickaxeTier() > 1 ? " (mindestens " + tierName(src.pickaxeTier()) + "-Spitzhacke)" : ""));
                case HUNT -> out.add("Lassen " + src.label() + " fallen");
                case HARVEST -> out.add("Waechst auf dem Feld - ernten");
            }
        }
        return out;
    }

    public static String tierName(int tier) {
        return switch (tier) {
            case 1 -> "Holz";
            case 2 -> "Stein";
            case 3 -> "Eisen";
            case 4 -> "Diamant";
            case 5 -> "Netherit";
            default -> "keine";
        };
    }
}
