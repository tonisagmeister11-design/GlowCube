package de.theboys.block;

import de.theboys.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Compound V fridge found in Vought labs. A stocked fridge hands out one dose when opened:
 * usually Compound V, very rarely the far more valuable V-One.
 */
public class VFridgeBlock extends HorizontalDirectionalBlock {
	public static final BooleanProperty STOCKED = BooleanProperty.create("stocked");
	/** Chance that a stocked fridge holds V-One instead of Compound V. */
	public static final float V_ONE_CHANCE = 0.02f;
	public static final float MINI_V_CHANCE = 0.08f;

	public VFridgeBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STOCKED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, STOCKED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		level.playSound(player, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8f, 1.4f);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!state.getValue(STOCKED)) {
			if (player instanceof ServerPlayer sp) {
				sp.sendSystemMessage(Component.translatable("message.theboys.fridge_empty").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		float roll = level.getRandom().nextFloat();
		ItemStack dose = new ItemStack(roll < V_ONE_CHANCE ? ModItems.COMPOUND_V1 : roll < V_ONE_CHANCE + MINI_V_CHANCE ? ModItems.MINI_V : ModItems.COMPOUND_V);
		Direction front = state.getValue(FACING);
		popResourceFromFace(level, pos, front, dose);
		level.setBlock(pos, state.setValue(STOCKED, false), 3);
		level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.2f);
		return InteractionResult.SUCCESS_SERVER;
	}
}
