package org.bukkit;
import java.util.*;
/** Testwelt: jedes Material existiert, Eigenschaften grob wie im Spiel. */
public final class Material {
    private static final Map<String, Material> ALL = new HashMap<>();
    public static Material AIR = getMaterial("AIR");
    private final String name;
    private Material(String n) { name = n; }
    public static Material getMaterial(String n) {
        if (n == null) return null;
        String k = n.toUpperCase(Locale.ROOT);
        if (!k.matches("[A-Z0-9_]+") || k.equals("NOT_A_BLOCK")) return null;
        return ALL.computeIfAbsent(k, Material::new);
    }
    public static Material matchMaterial(String n) { return getMaterial(n.replace("minecraft:", "")); }
    public String name() { return name; }
    public String toString() { return name; }
    public boolean isAir() { return name.endsWith("AIR"); }
    private static final Set<String> SOFT = Set.of("WATER","LAVA","SHORT_GRASS","TALL_GRASS","FERN","DANDELION","POPPY",
        "TORCH","WALL_TORCH","LADDER","WHEAT","CARROTS","POTATOES","BEETROOTS","SNOW","VINE","SUGAR_CANE","DEAD_BUSH","FIRE","RED_CARPET","FLOWER_POT");
    public boolean isSolid() { return !isAir() && !SOFT.contains(name) && !name.endsWith("_SAPLING") && !name.endsWith("_CARPET") && !name.endsWith("_BUTTON"); }
    public float getHardness() {
        if (name.equals("BEDROCK")) return -1;
        if (name.equals("OBSIDIAN")) return 50;
        if (isAir() || SOFT.contains(name)) return name.equals("WATER") || name.equals("LAVA") ? 100 : 0;
        if (name.endsWith("_LEAVES")) return 0.2f;
        if (name.endsWith("_ORE")) return name.startsWith("DEEPSLATE") ? 4.5f : 3f;
        if (name.equals("STONE")) return 1.5f;
        if (name.equals("COBBLESTONE") || name.endsWith("_LOG") || name.endsWith("_PLANKS")) return 2f;
        if (name.equals("DIRT") || name.equals("SAND")) return 0.5f;
        if (name.equals("GRASS_BLOCK") || name.equals("GRAVEL")) return 0.6f;
        return 1.5f;
    }
    private static final Set<String> FOOD = Set.of("COOKED_BEEF","BEEF","BREAD","APPLE","COOKED_PORKCHOP","PORKCHOP","CARROT","POTATO","BAKED_POTATO","ROTTEN_FLESH","COOKED_CHICKEN","CHICKEN","COOKED_MUTTON","MUTTON");
    public boolean isEdible() { return FOOD.contains(name); }
    public int getMaxStackSize() {
        if (name.endsWith("_PICKAXE")||name.endsWith("_AXE")||name.endsWith("_SWORD")||name.endsWith("_SHOVEL")||name.endsWith("_HOE")
            ||name.endsWith("_HELMET")||name.endsWith("_CHESTPLATE")||name.endsWith("_LEGGINGS")||name.endsWith("_BOOTS")||name.endsWith("_BED")||name.endsWith("BUCKET")) return 1;
        return 64;
    }
    public boolean isItem() { return !isAir() && !name.equals("WATER") && !name.equals("LAVA") && !name.equals("WALL_TORCH") && !name.equals("CARROTS") && !name.equals("POTATOES"); }
    public boolean isBlock() { return true; }
    public org.bukkit.block.data.BlockData createBlockData() { return FakeWorld.data(this, ""); }
}
