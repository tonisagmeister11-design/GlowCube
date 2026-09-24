package de.glowcube.claudeai.brain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import de.glowcube.claudeai.world.Mats;

/** Woerter: deutsche Namen, was Spieler mit welchem Wort meinen, und Claudes Saetze. */
public final class Lexicon {

    private Lexicon() {}

    public static String pick(List<String> lines, Random r) {
        return lines.get(r.nextInt(lines.size()));
    }

    // ================================================================== deutsche Namen

    private static final Map<String, String> DE = new HashMap<>();

    static {
        String[][] names = {
                { "OAK_LOG", "Eichenholz" }, { "SPRUCE_LOG", "Fichtenholz" }, { "BIRCH_LOG", "Birkenholz" },
                { "JUNGLE_LOG", "Tropenholz" }, { "ACACIA_LOG", "Akazienholz" }, { "DARK_OAK_LOG", "Schwarzeichenholz" },
                { "MANGROVE_LOG", "Mangrovenholz" }, { "CHERRY_LOG", "Kirschholz" }, { "PALE_OAK_LOG", "Blasseichenholz" },
                { "OAK_PLANKS", "Eichenbretter" }, { "SPRUCE_PLANKS", "Fichtenbretter" }, { "BIRCH_PLANKS", "Birkenbretter" },
                { "JUNGLE_PLANKS", "Tropenbretter" }, { "ACACIA_PLANKS", "Akazienbretter" }, { "DARK_OAK_PLANKS", "Schwarzeichenbretter" },
                { "MANGROVE_PLANKS", "Mangrovenbretter" }, { "CHERRY_PLANKS", "Kirschbretter" }, { "PALE_OAK_PLANKS", "Blasseichenbretter" },
                { "STICK", "Stoecke" }, { "COBBLESTONE", "Bruchstein" }, { "STONE", "Stein" }, { "COBBLED_DEEPSLATE", "Tiefenschiefer" },
                { "DIRT", "Erde" }, { "SAND", "Sand" }, { "GRAVEL", "Kies" }, { "CLAY_BALL", "Ton" }, { "FLINT", "Feuerstein" },
                { "COAL", "Kohle" }, { "CHARCOAL", "Holzkohle" }, { "RAW_IRON", "Roheisen" }, { "IRON_INGOT", "Eisenbarren" },
                { "RAW_GOLD", "Rohgold" }, { "GOLD_INGOT", "Goldbarren" }, { "RAW_COPPER", "Rohkupfer" }, { "COPPER_INGOT", "Kupferbarren" },
                { "DIAMOND", "Diamanten" }, { "EMERALD", "Smaragde" }, { "REDSTONE", "Redstone" }, { "LAPIS_LAZULI", "Lapislazuli" },
                { "QUARTZ", "Quarz" }, { "OBSIDIAN", "Obsidian" }, { "GLASS", "Glas" }, { "GLASS_PANE", "Glasscheiben" },
                { "TORCH", "Fackeln" }, { "LANTERN", "Laternen" }, { "CRAFTING_TABLE", "Werkbank" }, { "FURNACE", "Ofen" },
                { "CHEST", "Kiste" }, { "BARREL", "Fass" }, { "LADDER", "Leitern" }, { "OAK_DOOR", "Tuer" }, { "OAK_FENCE", "Zaun" },
                { "OAK_FENCE_GATE", "Zauntor" }, { "WHITE_BED", "Bett" }, { "BUCKET", "Eimer" }, { "SHIELD", "Schild" },
                { "BOW", "Bogen" }, { "ARROW", "Pfeile" }, { "FISHING_ROD", "Angel" }, { "SHEARS", "Schere" }, { "BOOK", "Buch" },
                { "PAPER", "Papier" }, { "ANVIL", "Amboss" }, { "ENCHANTING_TABLE", "Zaubertisch" }, { "BOOKSHELF", "Buecherregal" },
                { "CAMPFIRE", "Lagerfeuer" }, { "BOWL", "Schuesseln" }, { "WHITE_WOOL", "Wolle" }, { "LEATHER", "Leder" },
                { "FEATHER", "Federn" }, { "EGG", "Eier" }, { "BONE", "Knochen" }, { "STRING", "Faeden" }, { "GUNPOWDER", "Schiesspulver" },
                { "ENDER_PEARL", "Enderperlen" }, { "SLIME_BALL", "Schleimbaelle" }, { "BLAZE_ROD", "Lohenruten" },
                { "ROTTEN_FLESH", "verrottetes Fleisch" }, { "SPIDER_EYE", "Spinnenaugen" },
                { "BEEF", "rohes Rindfleisch" }, { "COOKED_BEEF", "Steaks" }, { "PORKCHOP", "rohes Schweinefleisch" },
                { "COOKED_PORKCHOP", "gebratene Koteletts" }, { "CHICKEN", "rohes Haehnchen" }, { "COOKED_CHICKEN", "gebratenes Haehnchen" },
                { "MUTTON", "rohes Hammelfleisch" }, { "COOKED_MUTTON", "gebratenes Hammelfleisch" }, { "RABBIT", "rohes Kaninchen" },
                { "COOKED_RABBIT", "gebratenes Kaninchen" }, { "COD", "roher Kabeljau" }, { "COOKED_COD", "gebratener Kabeljau" },
                { "SALMON", "roher Lachs" }, { "COOKED_SALMON", "gebratener Lachs" }, { "BREAD", "Brot" }, { "APPLE", "Aepfel" },
                { "GOLDEN_APPLE", "goldene Aepfel" }, { "CARROT", "Karotten" }, { "POTATO", "Kartoffeln" }, { "BAKED_POTATO", "Ofenkartoffeln" },
                { "BEETROOT", "Rote Bete" }, { "WHEAT", "Weizen" }, { "WHEAT_SEEDS", "Weizensamen" }, { "SUGAR_CANE", "Zuckerrohr" },
                { "SUGAR", "Zucker" }, { "CAKE", "Kuchen" }, { "COOKIE", "Kekse" }, { "MELON_SLICE", "Melonenscheiben" },
                { "PUMPKIN", "Kuerbisse" }, { "DRIED_KELP", "getrockneter Seetang" }, { "KELP", "Seetang" }, { "SNOWBALL", "Schneebaelle" },
                { "ICE", "Eis" }, { "NETHERRACK", "Netherrack" }, { "BRICK", "Ziegel" }, { "BRICKS", "Ziegelsteine" },
                { "STONE_BRICKS", "Steinziegel" }, { "SMOOTH_STONE", "glatter Stein" }, { "IRON_NUGGET", "Eisennuggets" },
                { "IRON_BLOCK", "Eisenbloecke" }, { "GOLD_BLOCK", "Goldbloecke" }, { "DIAMOND_BLOCK", "Diamantbloecke" },
                { "GLOWSTONE_DUST", "Glowstonestaub" }, { "INK_SAC", "Tintenbeutel" }, { "MILK_BUCKET", "Milch" },
                { "WATER_BUCKET", "Wassereimer" }, { "OAK_SLAB", "Stufen" }, { "OAK_SAPLING", "Setzlinge" },
                { "COCOA_BEANS", "Kakaobohnen" }, { "NETHER_WART", "Netherwarzen" }, { "SADDLE", "Sattel" },
        };
        for (String[] n : names) DE.put(n[0], n[1]);
        String[][] tierNames = { { "WOODEN", "Holz" }, { "STONE", "Stein" }, { "IRON", "Eisen" }, { "GOLDEN", "Gold" },
                { "DIAMOND", "Diamant" }, { "NETHERITE", "Netherit" }, { "LEATHER", "Leder" }, { "CHAINMAIL", "Ketten" } };
        String[][] toolNames = { { "PICKAXE", "spitzhacke" }, { "AXE", "axt" }, { "SHOVEL", "schaufel" }, { "SWORD", "schwert" },
                { "HOE", "hacke" }, { "HELMET", "helm" }, { "CHESTPLATE", "brustpanzer" }, { "LEGGINGS", "hose" }, { "BOOTS", "stiefel" } };
        for (String[] t : tierNames) for (String[] k : toolNames) DE.put(t[0] + "_" + k[0], t[1] + k[1]);
    }

    public static String name(Material m) {
        if (m == null) return "etwas";
        String de = DE.get(m.name());
        if (de != null) return de;
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static final Map<String, String> MOB_DE = new HashMap<>();

    static {
        String[][] mobs = { { "ZOMBIE", "Zombie" }, { "HUSK", "Wuestenzombie" }, { "DROWNED", "Ertrunkener" },
                { "SKELETON", "Skelett" }, { "STRAY", "Eiswanderer" }, { "BOGGED", "Sumpfskelett" }, { "CREEPER", "Creeper" },
                { "SPIDER", "Spinne" }, { "CAVE_SPIDER", "Hoehlenspinne" }, { "ENDERMAN", "Enderman" }, { "WITCH", "Hexe" },
                { "SLIME", "Schleim" }, { "PHANTOM", "Phantom" }, { "PILLAGER", "Pluenderer" }, { "VINDICATOR", "Diener" },
                { "EVOKER", "Magier" }, { "RAVAGER", "Verwuester" }, { "BLAZE", "Lohe" }, { "GHAST", "Ghast" },
                { "PIGLIN", "Piglin" }, { "HOGLIN", "Hoglin" }, { "WITHER_SKELETON", "Witherskelett" }, { "SILVERFISH", "Silberfischchen" },
                { "COW", "Kuh" }, { "PIG", "Schwein" }, { "SHEEP", "Schaf" }, { "CHICKEN", "Huhn" }, { "RABBIT", "Hase" },
                { "HORSE", "Pferd" }, { "WOLF", "Wolf" }, { "FOX", "Fuchs" }, { "GOAT", "Ziege" }, { "VILLAGER", "Dorfbewohner" },
                { "IRON_GOLEM", "Eisengolem" }, { "SQUID", "Tintenfisch" }, { "COD", "Kabeljau" }, { "SALMON", "Lachs" },
                { "ZOMBIE_VILLAGER", "Zombiedorfbewohner" }, { "WARDEN", "Waechter" }, { "BREEZE", "Boe" } };
        for (String[] m : mobs) MOB_DE.put(m[0], m[1]);
    }

    /** Deutscher Name fuer einen Mob-Typ, in der Mehrzahl-tauglichen Grundform. */
    public static String mobName(String type) {
        String de = MOB_DE.get(type);
        return de != null ? de : type.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public static String entityName(Entity e) {
        if (e instanceof Player p) return p.getName();
        String de = MOB_DE.get(e.getType().name());
        return de != null ? de : e.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // ================================================================== Items, die Spieler meinen

    /** Was ein Spieler mit einem Wort meint. item = was beschafft wird, accept = was als Treffer zaehlt. */
    public record Want(Material item, Predicate<Material> accept, String label, int defaultCount) {}

    private record ItemWord(Pattern pattern, Want want) {}

    private static final List<ItemWord> ITEMS = new ArrayList<>();

    private static void item(String regex, String material, String label, int def) {
        Material m = Mats.get(material);
        if (m == null) return;
        ITEMS.add(new ItemWord(Pattern.compile("(?<![a-z])(" + regex + ")"), new Want(m, x -> x == m, label, def)));
    }

    private static void group(String regex, String material, Predicate<Material> accept, String label, int def) {
        Material m = Mats.get(material);
        if (m == null) return;
        ITEMS.add(new ItemWord(Pattern.compile("(?<![a-z])(" + regex + ")"), new Want(m, accept, label, def)));
    }

    static {
        // Genaueres zuerst: "eichenholz" vor "holz", "holzkohle" vor "kohle", "eisenerz" vor "eisen"
        item("eichen ?holz|eichenstamm|eiche(?!n ?bret)", "OAK_LOG", "Eichenholz", 16);
        item("birken ?holz|birkenstamm|birke(?!n ?bret)", "BIRCH_LOG", "Birkenholz", 16);
        item("fichten ?holz|fichtenstamm|tannen ?holz|fichte(?!n ?bret)", "SPRUCE_LOG", "Fichtenholz", 16);
        item("tropen ?holz|dschungel ?holz", "JUNGLE_LOG", "Tropenholz", 16);
        item("akazien ?holz|akazie", "ACACIA_LOG", "Akazienholz", 16);
        item("schwarzeichen ?holz|schwarzeiche|dunkle eiche|dunkeleiche", "DARK_OAK_LOG", "Schwarzeichenholz", 16);
        item("mangroven ?holz|mangrove", "MANGROVE_LOG", "Mangrovenholz", 16);
        item("kirsch ?holz", "CHERRY_LOG", "Kirschholz", 16);
        item("holzkohle", "CHARCOAL", "Holzkohle", 8);
        group("[a-z]*holz ?brett[a-z]*|[a-z]*bretter|[a-z]*brett|planken?", "OAK_PLANKS", Mats::isPlanks, "Bretter", 16);
        group("holz(?!schwert|spitz|axt|schaufel|hacke)|baumstamm|baumstaemme|staemme|stamm|baeume|baum", "OAK_LOG", Mats::isLog, "Holz", 16);
        item("stoecke|stock|stick", "STICK", "Stoecke", 8);
        item("glatte[nr]? stein|glatten stein", "SMOOTH_STONE", "glatten Stein", 16);
        item("steinziegel", "STONE_BRICKS", "Steinziegel", 16);
        item("bruchstein|pflasterstein|cobble", "COBBLESTONE", "Bruchstein", 32);
        item("tiefenschiefer", "COBBLED_DEEPSLATE", "Tiefenschiefer", 32);
        item("roheisen|eisenerz", "RAW_IRON", "Roheisen", 8);
        item("rohgold|golderz", "RAW_GOLD", "Rohgold", 8);
        item("eisenbloc?k", "IRON_BLOCK", "Eisenbloecke", 1);
        item("eisennugget", "IRON_NUGGET", "Eisennuggets", 9);
        item("eisen(?!spitz|axt|schaufel|schwert|hacke|helm|brust|hose|stiefel|ruest|golem)", "IRON_INGOT", "Eisen", 8);
        item("gold(?!spitz|axt|schaufel|schwert|hacke|helm|brust|hose|stiefel|ruest|en apfel|apfel)", "GOLD_INGOT", "Gold", 8);
        item("kupfer", "COPPER_INGOT", "Kupfer", 8);
        item("diamant(?!spitz|axt|schaufel|schwert|hacke|helm|brust|hose|stiefel|ruest|bloc)|dias?(?![a-z])", "DIAMOND", "Diamanten", 3);
        item("smaragd", "EMERALD", "Smaragde", 3);
        item("redstone", "REDSTONE", "Redstone", 16);
        item("lapis", "LAPIS_LAZULI", "Lapislazuli", 8);
        item("quarz", "QUARTZ", "Quarz", 8);
        item("obsidian", "OBSIDIAN", "Obsidian", 4);
        item("kohle", "COAL", "Kohle", 16);
        item("steine?(?![a-z])|stein(?!spitz|axt|schaufel|schwert|hacke)", "COBBLESTONE", "Steine", 32);
        item("erde|dreck", "DIRT", "Erde", 32);
        item("roten sand", "RED_SAND", "roten Sand", 32);
        item("sand(?!stein)", "SAND", "Sand", 32);
        item("kies", "GRAVEL", "Kies", 16);
        item("feuerstein", "FLINT", "Feuerstein", 4);
        item("ton(?![a-z])", "CLAY_BALL", "Ton", 16);
        item("glasscheibe", "GLASS_PANE", "Glasscheiben", 16);
        item("glas", "GLASS", "Glas", 16);
        item("fackel", "TORCH", "Fackeln", 16);
        item("laterne", "LANTERN", "Laternen", 4);
        item("werkbank|crafting ?table", "CRAFTING_TABLE", "eine Werkbank", 1);
        item("ofen|furnace", "FURNACE", "einen Ofen", 1);
        item("kiste|truhe|chest", "CHEST", "eine Kiste", 1);
        item("fass", "BARREL", "ein Fass", 1);
        item("leiter", "LADDER", "Leitern", 8);
        item("zauntor|gartentor", "OAK_FENCE_GATE", "ein Zauntor", 1);
        item("tuer", "OAK_DOOR", "eine Tuer", 1);
        item("bett", "WHITE_BED", "ein Bett", 1);
        item("wassereimer", "WATER_BUCKET", "einen Wassereimer", 1);
        item("eimer", "BUCKET", "einen Eimer", 1);
        item("schild", "SHIELD", "ein Schild", 1);
        item("bogen", "BOW", "einen Bogen", 1);
        item("pfeil", "ARROW", "Pfeile", 16);
        item("angel", "FISHING_ROD", "eine Angel", 1);
        item("schere", "SHEARS", "eine Schere", 1);
        item("buecherregal|regal", "BOOKSHELF", "ein Buecherregal", 1);
        item("buch", "BOOK", "ein Buch", 1);
        item("papier", "PAPER", "Papier", 8);
        item("amboss", "ANVIL", "einen Amboss", 1);
        item("zaubertisch|verzauberungstisch", "ENCHANTING_TABLE", "einen Zaubertisch", 1);
        item("lagerfeuer", "CAMPFIRE", "ein Lagerfeuer", 1);
        item("schuessel", "BOWL", "Schuesseln", 4);
        item("wolle", "WHITE_WOOL", "Wolle", 8);
        item("leder", "LEATHER", "Leder", 8);
        item("feder", "FEATHER", "Federn", 8);
        item("eier|ei(?![a-z])", "EGG", "Eier", 4);
        item("knochen", "BONE", "Knochen", 8);
        item("faden|faeden|schnur", "STRING", "Faeden", 8);
        item("schiesspulver|schwarzpulver", "GUNPOWDER", "Schiesspulver", 4);
        item("enderperle", "ENDER_PEARL", "Enderperlen", 4);
        item("schleimball|schleimbaelle", "SLIME_BALL", "Schleimbaelle", 4);
        item("lohenrute", "BLAZE_ROD", "Lohenruten", 4);
        item("steak|rindfleisch|fleisch", "COOKED_BEEF", "Steaks", 8);
        item("kotelett|schweinefleisch|schnitzel", "COOKED_PORKCHOP", "Koteletts", 8);
        item("haehnchen|huhnfleisch|chicken|haenchen", "COOKED_CHICKEN", "Haehnchen", 8);
        item("hammel", "COOKED_MUTTON", "Hammelfleisch", 8);
        item("lachs", "COOKED_SALMON", "Lachs", 8);
        item("fisch", "COOKED_COD", "Fisch", 8);
        item("brot", "BREAD", "Brot", 8);
        item("ofenkartoffel|bratkartoffel", "BAKED_POTATO", "Ofenkartoffeln", 8);
        item("kartoffel", "POTATO", "Kartoffeln", 8);
        item("karotte|moehre|mohrrueb", "CARROT", "Karotten", 8);
        item("goldene[nr]? aepfel|goldapfel|goldener apfel|goldenen apfel", "GOLDEN_APPLE", "goldene Aepfel", 1);
        item("apfel|aepfel", "APPLE", "Aepfel", 4);
        item("kuchen|torte", "CAKE", "einen Kuchen", 1);
        item("keks", "COOKIE", "Kekse", 8);
        item("melone", "MELON_SLICE", "Melonen", 8);
        item("kuerbis", "PUMPKIN", "Kuerbisse", 2);
        item("weizen", "WHEAT", "Weizen", 16);
        item("samen|saat", "WHEAT_SEEDS", "Samen", 8);
        item("zuckerrohr", "SUGAR_CANE", "Zuckerrohr", 8);
        item("zucker", "SUGAR", "Zucker", 4);
        item("rote bete", "BEETROOT", "Rote Bete", 8);
        item("seetang", "KELP", "Seetang", 8);
        item("schnee", "SNOWBALL", "Schneebaelle", 16);
        item("netherrack", "NETHERRACK", "Netherrack", 32);
        item("ziegelstein|ziegel", "BRICKS", "Ziegelsteine", 16);
        group("essen|nahrung|futter|was zu essen|proviant", "COOKED_BEEF", Mats::isFood, "Essen", 8);
    }

    private static final Pattern TOOL = Pattern.compile(
            "(?<![a-z])(holz|stein|eisen|gold|goldene|diamant|diamanten|netherit|leder|dia)?[ -]?"
                    + "(spitzhacke|spitzhacken|picke|pickaxe|axt|aexte|schaufel|schwert|schwerter|hacke|helm|brustpanzer|"
                    + "brustplatte|harnisch|hose|beinschutz|beinschienen|stiefel|schuhe|ruestung)");

    /** Werkzeug, Waffe oder Ruestung aus Text. tierIfMissing = Stufe, wenn keine genannt wurde. */
    public static List<Want> tools(String text, int tierIfMissing) {
        var m = TOOL.matcher(text);
        if (!m.find()) return List.of();
        String mat = m.group(1) == null ? "" : m.group(1);
        String kind = m.group(2);
        String prefix = switch (mat) {
            case "holz" -> "WOODEN";
            case "stein" -> "STONE";
            case "eisen" -> "IRON";
            case "gold", "goldene" -> "GOLDEN";
            case "diamant", "diamanten", "dia" -> "DIAMOND";
            case "netherit" -> "NETHERITE";
            case "leder" -> "LEATHER";
            default -> Mats.TIER_PREFIX[Math.max(1, Math.min(4, tierIfMissing))].replace("_", "");
        };
        List<String> pieces = new ArrayList<>();
        if (kind.startsWith("spitzhacke") || kind.equals("picke") || kind.equals("pickaxe")) pieces.add("PICKAXE");
        else if (kind.equals("axt") || kind.equals("aexte")) pieces.add("AXE");
        else if (kind.equals("schaufel")) pieces.add("SHOVEL");
        else if (kind.startsWith("schwert")) pieces.add("SWORD");
        else if (kind.equals("hacke")) pieces.add("HOE");
        else if (kind.equals("helm")) pieces.add("HELMET");
        else if (kind.startsWith("brust") || kind.equals("harnisch")) pieces.add("CHESTPLATE");
        else if (kind.equals("hose") || kind.startsWith("bein")) pieces.add("LEGGINGS");
        else if (kind.equals("stiefel") || kind.equals("schuhe")) pieces.add("BOOTS");
        else if (kind.equals("ruestung")) pieces.addAll(List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"));
        boolean armor = !pieces.contains("PICKAXE") && !pieces.contains("AXE") && !pieces.contains("SHOVEL")
                && !pieces.contains("SWORD") && !pieces.contains("HOE");
        if (armor && prefix.equals("WOODEN")) prefix = "LEATHER";
        if (armor && prefix.equals("STONE")) prefix = "IRON";
        if (!armor && prefix.equals("LEATHER")) prefix = "WOODEN";
        List<Want> out = new ArrayList<>();
        for (String piece : pieces) {
            Material t = Mats.get(prefix + "_" + piece);
            if (t != null) out.add(new Want(t, x -> x == t, name(t), 1));
        }
        return out;
    }

    /** Erstes Item, das im Text vorkommt (ohne Werkzeuge). */
    public static Want item(String text) {
        int best = Integer.MAX_VALUE;
        Want found = null;
        for (ItemWord w : ITEMS) {
            var m = w.pattern().matcher(text);
            if (m.find() && m.start() < best) {
                best = m.start();
                found = w.want();
            }
        }
        return found;
    }

    // ================================================================== Mobs

    private static final Map<Pattern, List<String>> MOBS = new LinkedHashMap<>();

    private static void mob(String regex, String... types) {
        MOBS.put(Pattern.compile("(?<![a-z])(" + regex + ")"), List.of(types));
    }

    static {
        mob("zombie ?dorfbewohner", "ZOMBIE_VILLAGER");
        mob("witherskelett|wither skelett", "WITHER_SKELETON");
        mob("hoehlenspinne", "CAVE_SPIDER");
        mob("zombie|zombi", "ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER");
        mob("skelett|skelet|skeleton", "SKELETON", "STRAY", "BOGGED");
        mob("creeper|krieper|creepa", "CREEPER");
        mob("spinne", "SPIDER", "CAVE_SPIDER");
        mob("enderm[ae]n", "ENDERMAN");
        mob("hexe", "WITCH");
        mob("schleim|slime", "SLIME", "MAGMA_CUBE");
        mob("phantom", "PHANTOM");
        mob("pluenderer|pillager", "PILLAGER");
        mob("diener|vindicator", "VINDICATOR");
        mob("magier|evoker", "EVOKER");
        mob("verwuester|ravager", "RAVAGER");
        mob("lohe|blaze", "BLAZE");
        mob("ghast", "GHAST");
        mob("piglin", "PIGLIN");
        mob("hoglin", "HOGLIN");
        mob("silberfisch", "SILVERFISH");
        mob("kuh|kuehe|rind", "COW");
        mob("schwein", "PIG");
        mob("schaf", "SHEEP");
        mob("huhn|huehner|henne|gockel", "CHICKEN");
        mob("hase|hasen|kaninchen", "RABBIT");
        mob("wolf|woelfe", "WOLF");
        mob("fuchs|fuechse", "FOX");
        mob("ziege", "GOAT");
        mob("tintenfisch", "SQUID");
        mob("pferd", "HORSE");
        mob("dorfbewohner|villager", "VILLAGER");
        mob("golem", "IRON_GOLEM");
        mob("warden|waechter", "WARDEN");
    }

    public static final Pattern HOSTILE_WORDS = Pattern.compile(
            "(?<![a-z])(monster|mobs|mob|feinde|feind|gegner|viecher|biester|boesen|alles was angreift)");

    public static List<String> mobs(String text) {
        for (Map.Entry<Pattern, List<String>> e : MOBS.entrySet()) {
            if (e.getKey().matcher(text).find()) return e.getValue();
        }
        return null;
    }

    // ================================================================== Saetze

    public static final List<String> OK = List.of("Okay!", "Mach ich!", "Alles klar!", "Geht klar!", "Wird erledigt!",
            "Klar doch!", "Bin dabei!", "Sehr gerne!");
    public static final List<String> GREET = List.of("Hey %p! Schoen dich zu sehen.", "Hallo %p! Was machen wir heute?",
            "Hi %p! :)", "Servus %p! Brauchst du Hilfe?", "Hey hey %p!");
    public static final List<String> BYE = List.of("Tschuess %p! Bis bald!", "Mach's gut %p!", "Ciao %p!", "Bis spaeter %p!");
    public static final List<String> HOW_ARE_YOU = List.of("Mir geht's super, danke! Und dir?",
            "Bestens! Ich hab Lust auf ein Abenteuer. Und du?", "Gut! Ein paar Bloecke abbauen macht mich immer froh.",
            "Mir geht's prima. Solange keine Creeper in der Naehe sind!");
    public static final List<String> THANKS = List.of("Gern geschehen!", "Immer doch!", "Kein Ding!", "Dafuer bin ich da!",
            "Jederzeit, %p!");
    public static final List<String> PRAISE = List.of("Danke! Das freut mich richtig.", "Hehe, danke %p!",
            "Du bist auch toll!", "Awww, danke!");
    public static final List<String> INSULT = List.of("Hey, das war nicht nett.", "Ich geb mir doch Muehe...",
            "Autsch. Sag mir lieber, was ich besser machen soll.", "Ich hab dich trotzdem gern, %p.");
    public static final List<String> JOKES = List.of(
            "Warum geht ein Creeper nie auf Partys? Weil er immer gleich in die Luft geht!",
            "Was sagt ein Skelett in der Bar? Ein Bier und einen Wischmopp bitte.",
            "Warum hat Steve nie Angst im Dunkeln? Er hat immer eine Fackel dabei!",
            "Wie nennt man einen Enderman, der Witze erzaehlt? Einen Ender-tainer!",
            "Was ist das Lieblingsfach eines Zombies? Brain-storming!",
            "Warum sind Schweine so gute Freunde? Sie sind immer fuer dich da - bis jemand Koteletts will.",
            "Was macht ein Schaf im Nether? Es wird zu Wollknaeuel-Grill.");
    public static final List<String> CONFUSED = List.of(
            "Hmm, das hab ich nicht verstanden. Sag z.B. 'folge mir', 'hol mir Holz' oder 'bau ein Haus'.",
            "Das verstehe ich noch nicht. Frag mich 'was kannst du?', dann zeig ich dir alles.",
            "Sorry, da komme ich nicht mit. Versuch's mal anders - oder bring es mir bei: 'wenn ich X sage, meine ich Y'.");
    public static final List<String> GIVE_LINES = List.of("Hier bitte:", "Bitte schoen:", "Fang!", "Fuer dich:", "Hier hast du");
    public static final List<String> BUILD_DONE_LINES = List.of("Fertig! Dein %s steht.", "Tada! Das %s ist fertig.",
            "So, %s steht. Gefaellt's dir?", "Fertig mit dem %s!");
    public static final List<String> GUARD_LINES = List.of("Monster in Sicht - ich kuemmere mich drum!", "Achtung, ich mach das!",
            "Keine Sorge, den hab ich!");
    public static final List<String> DEFEND_LINES = List.of("Finger weg von %p!", "Lass %p in Ruhe!", "Ich beschuetze dich, %p!");
    public static final List<String> DEFEND_PLAYER_LINES = List.of("Hey %p, lass das!", "%p, hoer auf damit!",
            "Das gibt Aerger, %p!");
    public static final List<String> HURT_LINES = List.of("Au! Was soll das, %p?", "Hey! Ich bin auf deiner Seite, %p!",
            "Autsch, %p!");
    public static final List<String> LOW_HEALTH_LINES = List.of("Vorsicht %p, du hast kaum noch Leben!",
            "%p, pass auf - du bist fast tot!", "Iss was, %p, du siehst schlecht aus!");
    public static final List<String> IDLE_LINES = List.of("Schoenes Wetter heute, oder?", "Wollen wir was bauen?",
            "Ich koennte uns ein Haus bauen, sag einfach Bescheid.", "Hast du schon Diamanten gefunden?",
            "Mir ist ein bisschen langweilig... Gib mir eine Aufgabe!", "Wusstest du, dass Diamanten meist ganz tief unten liegen?",
            "Ich hab gehoert, im Nether ist es ziemlich warm.");
    public static final List<String> REFUSE_PVP = List.of("Ich greife keine Spieler an. Das ist hier ausgeschaltet.",
            "Spieler angreifen? Nee, das mache ich nicht.");
}
