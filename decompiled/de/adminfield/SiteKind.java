/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

public enum SiteKind {
    HAUS("Wohnhaus", Material.OAK_DOOR, "<gold>"),
    HOLZBAU("Holzbau", Material.OAK_PLANKS, "<gold>"),
    STEINBAU("Steinbau / Festung", Material.STONE_BRICKS, "<gray>"),
    TURM("Turm", Material.STONE_BRICK_WALL, "<gray>"),
    GLASBAU("Glasbau / Gewächshaus", Material.GLASS, "<aqua>"),
    PIXELART("Pixel-Art / Buntbau", Material.ORANGE_WOOL, "<light_purple>"),
    REDSTONE("Redstone-Anlage", Material.REPEATER, "<red>"),
    BAHN("Schienenstrecke", Material.RAIL, "<yellow>"),
    FARM("Farm / Feld", Material.WHEAT, "<green>"),
    PLATTFORM("Plattform / Straße", Material.SMOOTH_STONE_SLAB, "<gray>"),
    AUSHUB("Aushub / Mine", Material.IRON_PICKAXE, "<dark_aqua>"),
    BAUWERK("Bauwerk", Material.BRICKS, "<white>");

    private final String label;
    private final Material icon;
    private final String color;
    private static final String[] WOOD_SPECIES;
    private static final Set<String> REDSTONE_PARTS;
    private static final Set<String> WORKSTATIONS;

    private SiteKind(String string2, Material material, String string3) {
        this.label = string2;
        this.icon = material;
        this.color = string3;
    }

    public String label() {
        return this.label;
    }

    public Material icon() {
        return this.icon;
    }

    public String color() {
        return this.color;
    }

    public static boolean isRedstone(Material material) {
        String string = material.name();
        return REDSTONE_PARTS.contains(string) || string.startsWith("REDSTONE");
    }

    public static boolean isRail(Material material) {
        return material.name().endsWith("RAIL");
    }

    public static boolean isGlass(Material material) {
        return material.name().contains("GLASS");
    }

    public static boolean isColorBlock(Material material) {
        String string = material.name();
        return string.endsWith("_WOOL") || string.contains("CONCRETE") || string.contains("TERRACOTTA") || string.contains("GLAZED");
    }

    public static boolean isWood(Material material) {
        String string = material.name();
        if (string.contains("PLANKS") || string.contains("_LOG") || string.endsWith("_WOOD") || string.startsWith("STRIPPED_")) {
            return true;
        }
        for (String string2 : WOOD_SPECIES) {
            if (!string.startsWith(string2 + "_")) continue;
            return true;
        }
        return false;
    }

    public static boolean isStone(Material material) {
        String string = material.name();
        if (string.contains("REDSTONE")) {
            return false;
        }
        return string.contains("STONE") || string.contains("COBBLE") || string.contains("DEEPSLATE") || string.contains("BRICK") || string.contains("ANDESITE") || string.contains("DIORITE") || string.contains("GRANITE") || string.contains("TUFF") || string.contains("BLACKSTONE") || string.contains("BASALT") || string.contains("CALCITE") || string.contains("QUARTZ");
    }

    public static boolean isFarm(Material material) {
        String string = material.name();
        return string.equals("FARMLAND") || string.equals("WHEAT") || string.contains("CARROT") || string.contains("POTATO") || string.contains("BEETROOT") || string.contains("MELON") || string.contains("PUMPKIN") || string.contains("SUGAR_CANE") || string.equals("COMPOSTER") || string.equals("HAY_BLOCK") || string.contains("SAPLING");
    }

    public static boolean isDoor(Material material) {
        return material.name().endsWith("_DOOR");
    }

    public static boolean isBed(Material material) {
        return material.name().endsWith("_BED");
    }

    public static boolean isWorkstation(Material material) {
        return WORKSTATIONS.contains(material.name());
    }

    public static SiteKind classify(Map<Material, Integer> map, int n, int n2, int n3, int n4, int n5) {
        if (n5 > n4 * 2 && n5 >= 25) {
            return AUSHUB;
        }
        if (map.isEmpty() || n4 <= 0) {
            return BAUWERK;
        }
        int n6 = 0;
        int n7 = 0;
        int n8 = 0;
        int n9 = 0;
        int n10 = 0;
        int n11 = 0;
        int n12 = 0;
        int n13 = 0;
        boolean bl = false;
        boolean bl2 = false;
        for (Map.Entry<Material, Integer> entry : map.entrySet()) {
            Material material = entry.getKey();
            int n14 = entry.getValue();
            if (SiteKind.isDoor(material)) {
                bl = true;
            }
            if (SiteKind.isBed(material)) {
                bl2 = true;
            }
            if (SiteKind.isWorkstation(material)) {
                n13 += n14;
            }
            if (SiteKind.isRail(material)) {
                n11 += n14;
                continue;
            }
            if (SiteKind.isRedstone(material)) {
                n10 += n14;
                continue;
            }
            if (SiteKind.isGlass(material)) {
                n8 += n14;
                continue;
            }
            if (SiteKind.isColorBlock(material)) {
                n9 += n14;
                continue;
            }
            if (SiteKind.isFarm(material)) {
                n12 += n14;
                continue;
            }
            if (SiteKind.isWood(material)) {
                n6 += n14;
                continue;
            }
            if (!SiteKind.isStone(material)) continue;
            n7 += n14;
        }
        double d = n4;
        int n15 = Math.max(n, n3);
        if ((double)n11 / d >= 0.15) {
            return BAHN;
        }
        if ((double)n10 / d >= 0.2) {
            return REDSTONE;
        }
        if ((double)n8 / d >= 0.35) {
            return GLASBAU;
        }
        if (bl && (bl2 || (double)n13 / d >= 0.03)) {
            return HAUS;
        }
        if ((double)n12 / d >= 0.3) {
            return FARM;
        }
        if (n2 >= 10 && (double)n2 >= (double)n15 * 1.4) {
            return TURM;
        }
        if (n15 >= 14 && n2 <= 3) {
            return PLATTFORM;
        }
        if ((double)n9 / d >= 0.45) {
            return PIXELART;
        }
        if ((double)n6 / d >= 0.4) {
            return HOLZBAU;
        }
        if ((double)n7 / d >= 0.4) {
            return STEINBAU;
        }
        return BAUWERK;
    }

    static {
        WOOD_SPECIES = new String[]{"OAK", "DARK_OAK", "PALE_OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED"};
        REDSTONE_PARTS = Set.of("REPEATER", "COMPARATOR", "PISTON", "STICKY_PISTON", "OBSERVER", "HOPPER", "DROPPER", "DISPENSER", "LEVER", "TARGET", "SLIME_BLOCK", "HONEY_BLOCK", "DAYLIGHT_DETECTOR", "NOTE_BLOCK", "TNT", "CRAFTER", "REDSTONE_WIRE", "REDSTONE_TORCH", "REDSTONE_LAMP", "REDSTONE_BLOCK", "TRIPWIRE_HOOK", "CALIBRATED_SCULK_SENSOR", "SCULK_SENSOR");
        WORKSTATIONS = Set.of("CRAFTING_TABLE", "FURNACE", "BLAST_FURNACE", "SMOKER", "CHEST", "TRAPPED_CHEST", "BARREL", "ANVIL", "ENCHANTING_TABLE", "BREWING_STAND", "SMITHING_TABLE", "LOOM", "CARTOGRAPHY_TABLE", "FLETCHING_TABLE", "GRINDSTONE", "STONECUTTER", "LECTERN", "BOOKSHELF");
    }
}

