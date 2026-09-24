package de.glowcube.claudeai.world;

import java.util.Set;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Was ist begehbar, was ist gefaehrlich, was ist Natur, wie lange dauert Abbauen. */
public final class Blocks {

    private Blocks() {}

    private static final Set<String> DANGER = Set.of(
            "LAVA", "FIRE", "SOUL_FIRE", "CAMPFIRE", "SOUL_CAMPFIRE", "MAGMA_BLOCK", "CACTUS",
            "SWEET_BERRY_BUSH", "WITHER_ROSE", "POWDER_SNOW", "COBWEB", "POINTED_DRIPSTONE");

    /** Bloecke, die in der Natur vorkommen - die darf Claude fuer Bauplaetze wegraeumen und beim Graben durchbrechen. */
    private static final Set<String> NATURAL = Set.of(
            "GRASS_BLOCK", "DIRT", "COARSE_DIRT", "ROOTED_DIRT", "PODZOL", "MYCELIUM", "MUD", "CLAY", "MOSS_BLOCK",
            "STONE", "DEEPSLATE", "GRANITE", "DIORITE", "ANDESITE", "TUFF", "CALCITE", "DRIPSTONE_BLOCK", "COBBLED_DEEPSLATE",
            "SAND", "RED_SAND", "GRAVEL", "SANDSTONE", "RED_SANDSTONE", "TERRACOTTA", "SNOW", "SNOW_BLOCK", "ICE",
            "NETHERRACK", "SOUL_SAND", "SOUL_SOIL", "BASALT", "BLACKSTONE", "END_STONE", "COBBLESTONE",
            "SHORT_GRASS", "GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH", "BUSH", "SHORT_DRY_GRASS",
            "TALL_DRY_GRASS", "LEAF_LITTER", "DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET",
            "RED_TULIP", "ORANGE_TULIP", "WHITE_TULIP", "PINK_TULIP", "OXEYE_DAISY", "CORNFLOWER",
            "LILY_OF_THE_VALLEY", "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY", "BROWN_MUSHROOM", "RED_MUSHROOM",
            "VINE", "GLOW_LICHEN", "MOSS_CARPET", "PINK_PETALS", "WILDFLOWERS", "SUGAR_CANE", "PUMPKIN", "MELON",
            "SEAGRASS", "TALL_SEAGRASS", "KELP", "KELP_PLANT", "HANGING_ROOTS", "SPORE_BLOSSOM", "AZALEA",
            "FLOWERING_AZALEA", "SMALL_DRIPLEAF", "BIG_DRIPLEAF", "FIREFLY_BUSH", "CAVE_VINES", "CAVE_VINES_PLANT");

    public static boolean isNatural(Material m) {
        if (m == null || m.isAir()) return true;
        String n = m.name();
        return NATURAL.contains(n) || Mats.isLeaves(m) || Mats.isLog(m) || Mats.isOre(m)
                || n.endsWith("_SAPLING") || n.endsWith("_TERRACOTTA") && !n.contains("GLAZED");
    }

    public static boolean isDanger(Material m) {
        return m != null && DANGER.contains(m.name());
    }

    public static boolean isWater(Material m) {
        String n = Mats.n(m);
        return n.equals("WATER") || n.equals("BUBBLE_COLUMN") || n.equals("SEAGRASS") || n.equals("TALL_SEAGRASS")
                || n.equals("KELP") || n.equals("KELP_PLANT");
    }

    public static boolean loaded(World w, int x, int z) {
        return w.isChunkLoaded(x >> 4, z >> 4);
    }

    /** Block oder null, ohne je einen Chunk zu laden. */
    public static Block at(World w, int x, int y, int z) {
        if (y < w.getMinHeight() || y >= w.getMaxHeight() || !loaded(w, x, z)) return null;
        return w.getBlockAt(x, y, z);
    }

    /** Kann der Koerper hindurch? Holztueren und Tore zaehlen - die macht Claude auf. */
    public static boolean passable(Block b) {
        if (b == null) return false;
        Material m = b.getType();
        if (isDanger(m)) return false;
        if (Mats.isWoodenDoorLike(m)) return !Mats.n(m).endsWith("_TRAPDOOR");
        return b.isPassable();
    }

    /** Kann man darauf stehen? */
    public static boolean floor(Block b) {
        if (b == null) return false;
        Material m = b.getType();
        if (Mats.isFenceLike(m) || isDanger(m)) return false;
        return m.isSolid() || Mats.n(m).equals("DIRT_PATH") || Mats.n(m).equals("FARMLAND");
    }

    /** Darf beim Graben durchbrochen werden? */
    public static boolean diggable(Block b) {
        if (b == null) return false;
        Material m = b.getType();
        if (m.isAir() || b.isLiquid()) return false;
        float h = m.getHardness();
        return h >= 0 && h < 30 && isNatural(m) && !Mats.isLog(m);
    }

    /** Braucht der Block eine Spitzhacke, und welche Stufe mindestens? 0 = geht mit der Hand. */
    public static int pickaxeTier(Material m) {
        String n = Mats.n(m);
        if (n.equals("OBSIDIAN") || n.equals("CRYING_OBSIDIAN") || n.equals("ANCIENT_DEBRIS")
                || n.equals("RESPAWN_ANCHOR") || n.equals("NETHERITE_BLOCK")) return 4;
        if (n.endsWith("DIAMOND_ORE") || n.endsWith("EMERALD_ORE") || n.endsWith("REDSTONE_ORE")
                || n.equals("GOLD_ORE") || n.equals("DEEPSLATE_GOLD_ORE") || n.equals("DIAMOND_BLOCK")
                || n.equals("GOLD_BLOCK") || n.equals("EMERALD_BLOCK")) return 3;
        if (n.endsWith("IRON_ORE") || n.endsWith("COPPER_ORE") || n.endsWith("LAPIS_ORE")
                || n.equals("IRON_BLOCK") || n.equals("LAPIS_BLOCK")) return 2;
        if (n.equals("GLOWSTONE") || n.startsWith("REDSTONE_") && !n.equals("REDSTONE_BLOCK")
                || n.equals("REDSTONE") || n.endsWith("CONCRETE_POWDER")) return 0;
        for (String part : PICKAXE_PARTS) {
            if (n.contains(part)) return 1;
        }
        return 0;
    }

    private static final String[] PICKAXE_PARTS = {
            "STONE", "DEEPSLATE", "BRICK", "_ORE", "NETHERRACK", "ANDESITE", "DIORITE", "GRANITE", "TUFF",
            "CALCITE", "TERRACOTTA", "CONCRETE", "BASALT", "FURNACE", "QUARTZ", "PRISMARINE", "PURPUR",
            "DRIPSTONE_BLOCK", "AMETHYST", "COPPER", "ANVIL", "IRON_", "SMOKER", "OBSERVER", "DISPENSER",
            "DROPPER", "HOPPER", "CAULDRON", "BELL", "LANTERN", "CHAIN", "RAW_" };

    /** Welches Werkzeug baut diesen Block am schnellsten ab? */
    public static String bestToolKind(Material m) {
        String n = Mats.n(m);
        if (Mats.isLog(m) || Mats.isPlanks(m) || n.contains("_FENCE") || n.contains("CHEST") || n.equals("CRAFTING_TABLE")
                || n.contains("BOOKSHELF") || n.contains("_DOOR") && !n.startsWith("IRON") || n.equals("PUMPKIN")
                || n.equals("MELON") || n.equals("LADDER")) return "axe";
        if (n.equals("DIRT") || n.equals("GRASS_BLOCK") || n.contains("SAND") && !n.contains("STONE") || n.equals("GRAVEL")
                || n.equals("CLAY") || n.contains("SNOW") || n.equals("MUD") || n.equals("PODZOL") || n.equals("MYCELIUM")
                || n.equals("SOUL_SAND") || n.equals("SOUL_SOIL") || n.equals("FARMLAND") || n.equals("COARSE_DIRT")
                || n.equals("ROOTED_DIRT") || n.equals("DIRT_PATH")) return "shovel";
        if (Mats.isLeaves(m) || n.contains("HAY") || n.contains("SCULK") || n.equals("MOSS_BLOCK")) return "hoe";
        if (pickaxeTier(m) > 0) return "pickaxe";
        return null;
    }

    /** Ticks bis der Block bricht - grob wie im Spiel. */
    public static int breakTicks(Material block, Material tool) {
        float hardness = block.getHardness();
        if (hardness <= 0) return 2;
        String best = bestToolKind(block);
        boolean right = best != null && best.equals(Mats.toolKind(tool));
        double speed = right ? Mats.toolSpeed(tool) : 1;
        boolean canHarvest = pickaxeTier(block) == 0 || right && Mats.toolTier(tool) >= pickaxeTier(block);
        double damage = speed / hardness / (canHarvest ? 30 : 100);
        return (int) Math.max(2, Math.min(400, Math.ceil(1 / damage)));
    }
}
