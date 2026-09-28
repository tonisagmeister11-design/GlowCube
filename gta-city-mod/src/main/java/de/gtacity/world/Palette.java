package de.gtacity.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static de.gtacity.world.B.s;

/** Material set of one building facade. */
public record Palette(BlockState frame, BlockState glass, BlockState floor, BlockState light, BlockState roof,
                      BlockState accent) {

    /** Glass skyscrapers for downtown. */
    public static final Palette[] TOWERS = {
            new Palette(s(Blocks.CONCRETE.black()), s(Blocks.STAINED_GLASS.lightBlue()), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.CONCRETE.gray()), s(Blocks.CONCRETE.white())),
            new Palette(s(Blocks.CONCRETE.white()), s(Blocks.STAINED_GLASS.cyan()), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.CONCRETE.lightGray()), s(Blocks.CONCRETE.lightGray())),
            new Palette(s(Blocks.CONCRETE.gray()), s(Blocks.STAINED_GLASS.blue()), s(Blocks.POLISHED_ANDESITE), B.SEA_LANTERN, s(Blocks.CONCRETE.gray()), s(Blocks.CONCRETE.lightBlue())),
            new Palette(s(Blocks.QUARTZ_BLOCK), s(Blocks.STAINED_GLASS.lightGray()), s(Blocks.SMOOTH_QUARTZ), B.SEA_LANTERN, s(Blocks.SMOOTH_QUARTZ), s(Blocks.GOLD_BLOCK)),
            new Palette(s(Blocks.POLISHED_DEEPSLATE), s(Blocks.STAINED_GLASS.black()), s(Blocks.POLISHED_DEEPSLATE), B.SEA_LANTERN, s(Blocks.DEEPSLATE_TILES), s(Blocks.CONCRETE.red())),
            new Palette(s(Blocks.SMOOTH_SANDSTONE), s(Blocks.STAINED_GLASS.brown()), s(Blocks.SMOOTH_STONE), B.GLOWSTONE, s(Blocks.SMOOTH_SANDSTONE), s(Blocks.CUT_SANDSTONE)),
            new Palette(s(Blocks.POLISHED_ANDESITE), s(Blocks.GLASS), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.ANDESITE), s(Blocks.CONCRETE.cyan())),
            new Palette(s(Blocks.CONCRETE.lightGray()), s(Blocks.STAINED_GLASS.gray()), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.CONCRETE.gray()), s(Blocks.CONCRETE.orange())),
            new Palette(s(Blocks.CONCRETE.blue()), s(Blocks.STAINED_GLASS.lightBlue()), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.CONCRETE.lightGray()), s(Blocks.CONCRETE.white())),
            new Palette(s(Blocks.CUT_COPPER.waxed().oxidized()), s(Blocks.STAINED_GLASS.green()), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.COPPER_BLOCK.waxed().oxidized()), s(Blocks.GOLD_BLOCK)),
    };

    /** Brick and stone mid-rise office buildings. */
    public static final Palette[] OFFICES = {
            new Palette(s(Blocks.BRICKS), s(Blocks.GLASS), s(Blocks.SPRUCE_PLANKS), B.GLOWSTONE, s(Blocks.STONE_BRICKS), s(Blocks.STONE_BRICKS)),
            new Palette(s(Blocks.STONE_BRICKS), s(Blocks.STAINED_GLASS.lightGray()), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.STONE_BRICKS), s(Blocks.CHISELED_STONE_BRICKS)),
            new Palette(s(Blocks.DYED_TERRACOTTA.white()), s(Blocks.GLASS), s(Blocks.BIRCH_PLANKS), B.GLOWSTONE, s(Blocks.SMOOTH_STONE), s(Blocks.DYED_TERRACOTTA.orange())),
            new Palette(s(Blocks.CONCRETE.lightGray()), s(Blocks.STAINED_GLASS.cyan()), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.CONCRETE.gray()), s(Blocks.CONCRETE.white())),
            new Palette(s(Blocks.CUT_SANDSTONE), s(Blocks.GLASS), s(Blocks.SMOOTH_SANDSTONE), B.GLOWSTONE, s(Blocks.SMOOTH_SANDSTONE), s(Blocks.TERRACOTTA)),
            new Palette(s(Blocks.MUD_BRICKS), s(Blocks.STAINED_GLASS.brown()), s(Blocks.SPRUCE_PLANKS), B.GLOWSTONE, s(Blocks.PACKED_MUD), s(Blocks.DARK_OAK_PLANKS)),
            new Palette(s(Blocks.GRANITE), s(Blocks.GLASS), s(Blocks.SMOOTH_STONE), B.GLOWSTONE, s(Blocks.POLISHED_GRANITE), s(Blocks.POLISHED_GRANITE)),
            new Palette(s(Blocks.DYED_TERRACOTTA.pink()), s(Blocks.STAINED_GLASS.lightBlue()), s(Blocks.SMOOTH_STONE), B.SEA_LANTERN, s(Blocks.DYED_TERRACOTTA.white()), s(Blocks.DYED_TERRACOTTA.white())),
    };

    /** Family houses. frame = wall, accent = roof stairs material handled separately. */
    public static final Palette[] HOUSES = {
            new Palette(s(Blocks.DYED_TERRACOTTA.white()), s(Blocks.GLASS), s(Blocks.OAK_PLANKS), B.GLOWSTONE, s(Blocks.DARK_OAK_PLANKS), s(Blocks.STRIPPED_DARK_OAK_LOG)),
            new Palette(s(Blocks.SMOOTH_SANDSTONE), s(Blocks.GLASS), s(Blocks.SPRUCE_PLANKS), B.GLOWSTONE, s(Blocks.TERRACOTTA), s(Blocks.CUT_SANDSTONE)),
            new Palette(s(Blocks.BRICKS), s(Blocks.GLASS), s(Blocks.OAK_PLANKS), B.GLOWSTONE, s(Blocks.SPRUCE_PLANKS), s(Blocks.STONE_BRICKS)),
            new Palette(s(Blocks.DYED_TERRACOTTA.lightBlue()), s(Blocks.GLASS), s(Blocks.BIRCH_PLANKS), B.GLOWSTONE, s(Blocks.DYED_TERRACOTTA.white()), s(Blocks.CONCRETE.white())),
            new Palette(s(Blocks.DYED_TERRACOTTA.yellow()), s(Blocks.GLASS), s(Blocks.OAK_PLANKS), B.GLOWSTONE, s(Blocks.DYED_TERRACOTTA.brown()), s(Blocks.CONCRETE.white())),
            new Palette(s(Blocks.QUARTZ_BLOCK), s(Blocks.GLASS), s(Blocks.SMOOTH_QUARTZ), B.SEA_LANTERN, s(Blocks.SMOOTH_QUARTZ), s(Blocks.CONCRETE.gray())),
    };
}
