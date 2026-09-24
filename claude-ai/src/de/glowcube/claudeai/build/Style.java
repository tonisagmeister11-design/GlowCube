package de.glowcube.claudeai.build;

/** Baustil: welche Bloecke fuer Wand, Ecken, Boden, Dach, Tuer. Alles als Blockdaten-Namen. */
public record Style(String name, String wall, String corner, String floor, String roofStairs, String roofSlab,
        String roofBlock, String window, String door, String fence) {

    public static final Style WOOD = new Style("Holz", "oak_planks", "oak_log", "spruce_planks", "spruce_stairs",
            "spruce_slab", "spruce_planks", "glass_pane", "oak_door", "oak_fence");
    public static final Style STONE = new Style("Stein", "stone_bricks", "cobblestone", "oak_planks", "dark_oak_stairs",
            "dark_oak_slab", "dark_oak_planks", "glass_pane", "spruce_door", "spruce_fence");
    public static final Style BRICK = new Style("Ziegel", "bricks", "stone_bricks", "oak_planks", "dark_oak_stairs",
            "dark_oak_slab", "dark_oak_planks", "glass_pane", "dark_oak_door", "dark_oak_fence");
    public static final Style SAND = new Style("Sandstein", "cut_sandstone", "chiseled_sandstone", "birch_planks",
            "sandstone_stairs", "sandstone_slab", "sandstone", "glass_pane", "birch_door", "birch_fence");
    public static final Style BIRCH = new Style("Birke", "birch_planks", "birch_log", "oak_planks", "oak_stairs",
            "oak_slab", "oak_planks", "glass_pane", "birch_door", "birch_fence");
    public static final Style SPRUCE = new Style("Fichte", "spruce_planks", "spruce_log", "oak_planks", "dark_oak_stairs",
            "dark_oak_slab", "dark_oak_planks", "glass_pane", "spruce_door", "spruce_fence");
    public static final Style DARK = new Style("Dunkeleiche", "dark_oak_planks", "dark_oak_log", "spruce_planks",
            "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles", "glass_pane", "dark_oak_door", "dark_oak_fence");
    public static final Style CHERRY = new Style("Kirsche", "cherry_planks", "cherry_log", "birch_planks", "cherry_stairs",
            "cherry_slab", "cherry_planks", "glass_pane", "cherry_door", "cherry_fence");
    public static final Style QUARTZ = new Style("Quarz", "quartz_block", "quartz_pillar", "birch_planks", "quartz_stairs",
            "quartz_slab", "quartz_block", "glass_pane", "birch_door", "birch_fence");
    public static final Style GLASS = new Style("Glas", "glass", "quartz_pillar", "quartz_block", "quartz_stairs",
            "quartz_slab", "quartz_block", "glass", "birch_door", "birch_fence");
    public static final Style COBBLE = new Style("Bruchstein", "cobblestone", "oak_log", "oak_planks", "oak_stairs",
            "oak_slab", "oak_planks", "glass_pane", "oak_door", "oak_fence");
}
