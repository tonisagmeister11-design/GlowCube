package de.gtacity.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Short names for the block states the city generator uses all the time. */
public final class B {
    private B() {
    }

    public static BlockState s(Block block) {
        return block.defaultBlockState();
    }

    public static final BlockState BEDROCK = s(Blocks.BEDROCK);
    public static final BlockState STONE = s(Blocks.STONE);
    public static final BlockState DEEPSLATE = s(Blocks.DEEPSLATE);
    public static final BlockState DIRT = s(Blocks.DIRT);
    public static final BlockState GRASS = s(Blocks.GRASS_BLOCK);
    public static final BlockState SAND = s(Blocks.SAND);
    public static final BlockState SANDSTONE = s(Blocks.SANDSTONE);
    public static final BlockState GRAVEL = s(Blocks.GRAVEL);
    public static final BlockState WATER = s(Blocks.WATER);
    public static final BlockState DIRT_PATH = s(Blocks.DIRT_PATH);

    public static final BlockState ASPHALT = s(Blocks.GRAY_CONCRETE);
    public static final BlockState LINE_YELLOW = s(Blocks.YELLOW_CONCRETE);
    public static final BlockState LINE_WHITE = s(Blocks.WHITE_CONCRETE);
    public static final BlockState SIDEWALK = s(Blocks.SMOOTH_STONE_SLAB);
    public static final BlockState CURB = s(Blocks.POLISHED_ANDESITE_SLAB);
    public static final BlockState PLAZA = s(Blocks.POLISHED_ANDESITE);
    public static final BlockState PLAZA_LIGHT = s(Blocks.SMOOTH_STONE);

    public static final BlockState POLE = s(Blocks.POLISHED_BLACKSTONE_WALL);
    public static final BlockState ARM = s(Blocks.POLISHED_BLACKSTONE_SLAB).setValue(SlabBlock.TYPE, SlabType.TOP);
    public static final BlockState STREET_LIGHT = s(Blocks.OCHRE_FROGLIGHT);
    public static final BlockState HYDRANT = s(Blocks.RED_NETHER_BRICK_WALL);
    public static final BlockState TRASH = s(Blocks.CAULDRON);

    public static final BlockState OAK_LOG = s(Blocks.OAK_LOG);
    public static final BlockState BIRCH_LOG = s(Blocks.BIRCH_LOG);
    public static final BlockState JUNGLE_LOG = s(Blocks.JUNGLE_LOG);
    public static final BlockState OAK_LEAVES = leaves(Blocks.OAK_LEAVES);
    public static final BlockState BIRCH_LEAVES = leaves(Blocks.BIRCH_LEAVES);
    public static final BlockState JUNGLE_LEAVES = leaves(Blocks.JUNGLE_LEAVES);
    public static final BlockState AZALEA_LEAVES = leaves(Blocks.AZALEA_LEAVES);
    public static final BlockState FLOWERING_AZALEA = leaves(Blocks.FLOWERING_AZALEA_LEAVES);

    public static final BlockState SHORT_GRASS = s(Blocks.SHORT_GRASS);
    public static final BlockState POPPY = s(Blocks.POPPY);
    public static final BlockState DANDELION = s(Blocks.DANDELION);
    public static final BlockState BLUET = s(Blocks.AZURE_BLUET);

    public static final BlockState GLASS = s(Blocks.GLASS);
    public static final BlockState SEA_LANTERN = s(Blocks.SEA_LANTERN);
    public static final BlockState GLOWSTONE = s(Blocks.GLOWSTONE);
    public static final BlockState END_ROD = s(Blocks.END_ROD);

    public static BlockState leaves(Block block) {
        return s(block).setValue(LeavesBlock.PERSISTENT, true);
    }

    public static BlockState slabTop(Block block) {
        return s(block).setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    public static BlockState stairs(Block block, Direction facing) {
        return s(block).setValue(StairBlock.FACING, facing);
    }

    public static BlockState stairsUpsideDown(Block block, Direction facing) {
        return s(block).setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.TOP);
    }

    public static BlockState door(Block block, Direction facing, boolean upper) {
        return s(block).setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }

    public static BlockState ladder(Direction facing) {
        return s(Blocks.LADDER).setValue(LadderBlock.FACING, facing);
    }

    public static BlockState trapdoorTop(Block block) {
        return s(block).setValue(TrapDoorBlock.HALF, Half.TOP);
    }
}
