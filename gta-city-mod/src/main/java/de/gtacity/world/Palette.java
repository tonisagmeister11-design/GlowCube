package de.gtacity.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static de.gtacity.world.B.s;

/** Material set of one building facade. */
public record Palette(BlockState frame, BlockState glass, BlockState floor, BlockState light, BlockState roof,
                      BlockState accent) {

    /** Glass skyscrapers for downtown. */
    public static final Palette[] TOWERS = {
            new Palette(s(Blocks.BLACK_CONCRETE), s(Blocks.LIGHT_BLUE_STAINED_GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.GRAY_CONCRETE), s(Blocks.WHITE_CONCRETE)),
            new Palette(s(Blocks.WHITE_CONCRETE), s(Blocks.CYAN_STAINED_GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.LIGHT_GRAY_CONCRETE)),
            new Palette(s(Blocks.GRAY_CONCRETE), s(Blocks.BLUE_STAINED_GLASS), s(Blocks.POLISHED_ANDESITE), B.SEA_LANTERN, s(Blocks.GRAY_CONCRETE), s(Blocks.LIGHT_BLUE_CONCRETE)),
            new Palette(s(Blocks.QUARTZ_BLOCK), s(Blocks.LIGHT_GRAY_STAINED_GLASS), s(Blocks.SMOOTH_QUARTZ), B.SEA_LANTERN, s(Blocks.SMOOTH_QUARTZ), s(Blocks.GOLD_BLOCK)),
            new Palette(s(Blocks.POLISHED_DEEPSLATE), s(Blocks.BLACK_STAINED_GLASS), s(Blocks.POLISHED_DEEPSLATE), B.SEA_LANTERN, s(Blocks.DEEPSLATE_TILES), s(Blocks.RED_CONCRETE)),
            new Palette(s(Blocks.SMOOTH_SANDSTONE), s(Blocks.BROWN_STAINED_GLASS), s(Blocks.SMOOTH_STONE), B.GLOWSTONE, s(Blocks.SMOOTH_SANDSTONE), s(Blocks.CUT_SANDSTONE)),
            new Palette(s(Blocks.POLISHED_ANDESITE), s(Blocks.GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.ANDESITE), s(Blocks.CYAN_CONCRETE)),
            new Palette(s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.GRAY_STAINED_GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.GRAY_CONCRETE), s(Blocks.ORANGE_CONCRETE)),
            new Palette(s(Blocks.BLUE_CONCRETE), s(Blocks.LIGHT_BLUE_STAINED_GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.WHITE_CONCRETE)),
            new Palette(s(Blocks.WAXED_OXIDIZED_CUT_COPPER), s(Blocks.GREEN_STAINED_GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.WAXED_OXIDIZED_COPPER), s(Blocks.GOLD_BLOCK)),
    };

    /** Brick and stone mid-rise office buildings. */
    public static final Palette[] OFFICES = {
            new Palette(s(Blocks.BRICKS), s(Blocks.GLASS), s(Blocks.SPRUCE_PLANKS), B.GLOWSTONE, s(Blocks.STONE_BRICKS), s(Blocks.STONE_BRICKS)),
            new Palette(s(Blocks.STONE_BRICKS), s(Blocks.LIGHT_GRAY_STAINED_GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.STONE_BRICKS), s(Blocks.CHISELED_STONE_BRICKS)),
            new Palette(s(Blocks.WHITE_TERRACOTTA), s(Blocks.GLASS), s(Blocks.BIRCH_PLANKS), B.GLOWSTONE, s(Blocks.SMOOTH_STONE), s(Blocks.ORANGE_TERRACOTTA)),
            new Palette(s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.CYAN_STAINED_GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.GRAY_CONCRETE), s(Blocks.WHITE_CONCRETE)),
            new Palette(s(Blocks.CUT_SANDSTONE), s(Blocks.GLASS), s(Blocks.SMOOTH_SANDSTONE), B.GLOWSTONE, s(Blocks.SMOOTH_SANDSTONE), s(Blocks.TERRACOTTA)),
            new Palette(s(Blocks.MUD_BRICKS), s(Blocks.BROWN_STAINED_GLASS), s(Blocks.SPRUCE_PLANKS), B.GLOWSTONE, s(Blocks.PACKED_MUD), s(Blocks.DARK_OAK_PLANKS)),
            new Palette(s(Blocks.GRANITE), s(Blocks.GLASS), s(Blocks.SMOOTH_STONE), B.GLOWSTONE, s(Blocks.POLISHED_GRANITE), s(Blocks.POLISHED_GRANITE)),
            new Palette(s(Blocks.PINK_TERRACOTTA), s(Blocks.LIGHT_BLUE_STAINED_GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.WHITE_TERRACOTTA), s(Blocks.WHITE_TERRACOTTA)),
    };

    /** Family houses. frame = wall, accent = roof stairs material handled separately. */
    public static final Palette[] HOUSES = {
            new Palette(s(Blocks.WHITE_TERRACOTTA), s(Blocks.GLASS), s(Blocks.OAK_PLANKS), B.GLOWSTONE, s(Blocks.DARK_OAK_PLANKS), s(Blocks.STRIPPED_DARK_OAK_LOG)),
            new Palette(s(Blocks.SMOOTH_SANDSTONE), s(Blocks.GLASS), s(Blocks.SPRUCE_PLANKS), B.GLOWSTONE, s(Blocks.TERRACOTTA), s(Blocks.CUT_SANDSTONE)),
            new Palette(s(Blocks.BRICKS), s(Blocks.GLASS), s(Blocks.OAK_PLANKS), B.GLOWSTONE, s(Blocks.SPRUCE_PLANKS), s(Blocks.STONE_BRICKS)),
            new Palette(s(Blocks.LIGHT_BLUE_TERRACOTTA), s(Blocks.GLASS), s(Blocks.BIRCH_PLANKS), B.GLOWSTONE, s(Blocks.WHITE_TERRACOTTA), s(Blocks.WHITE_CONCRETE)),
            new Palette(s(Blocks.YELLOW_TERRACOTTA), s(Blocks.GLASS), s(Blocks.OAK_PLANKS), B.GLOWSTONE, s(Blocks.BROWN_TERRACOTTA), s(Blocks.WHITE_CONCRETE)),
            new Palette(s(Blocks.QUARTZ_BLOCK), s(Blocks.GLASS), s(Blocks.SMOOTH_QUARTZ), B.SEA_LANTERN, s(Blocks.SMOOTH_QUARTZ), s(Blocks.GRAY_CONCRETE)),
    };
}
