package de.gtacity.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One piece of the big GlowCube billboard: {@link #SIZE} x {@link #SIZE} of these show the logo (tools/gen_billboard.py).
 * {@code facing} is the side with the picture, {@code tile} the piece (row * SIZE + column, from the top left as seen
 * from the front). It glows, so it can be seen at night too.
 */
public class BillboardBlock extends Block {
    public static final int SIZE = 8;
    public static final IntegerProperty TILE = IntegerProperty.create("tile", 0, SIZE * SIZE - 1);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BillboardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TILE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TILE);
    }

    public BlockState tile(Direction facing, int tile) {
        return defaultBlockState().setValue(FACING, facing).setValue(TILE, tile);
    }
}
