package de.theboys.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Holder block for a block MiniMaus chipped pixels out of (see {@link CarvedBlockEntity}). */
public class CarvedBlock extends BaseEntityBlock {
	public static final MapCodec<CarvedBlock> CODEC = simpleCodec(CarvedBlock::new);

	public CarvedBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CarvedBlockEntity(pos, state);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		// drawn pixel by pixel by the block entity renderer
		return RenderShape.INVISIBLE;
	}

	private static VoxelShape carved(BlockGetter level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof CarvedBlockEntity be ? be.shape() : Shapes.block();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return carved(level, pos);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return carved(level, pos);
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return Shapes.empty();
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// mined normally: you get the original block back if most of it is still there
		if (!level.isClientSide() && !player.isCreative() && level.getBlockEntity(pos) instanceof CarvedBlockEntity be
				&& be.remaining() >= CarvedBlockEntity.COUNT / 2) {
			popResource(level, pos, new ItemStack(be.original().getBlock()));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
