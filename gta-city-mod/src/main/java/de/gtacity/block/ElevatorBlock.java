package de.gtacity.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Right click: ride to the roof (or back down to the lobby). */
public class ElevatorBlock extends Block {
    public ElevatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        boolean up = true;
        BlockPos target = find(level, pos, true);
        if (target == null) {
            target = find(level, pos, false);
            up = false;
        }
        if (target == null) {
            player.sendOverlayMessage(Component.literal("Dieser Aufzug fährt nirgendwohin."));
            return InteractionResult.CONSUME;
        }
        player.teleportTo(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
        level.playSound(null, target, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0F, 1.6F);
        player.sendOverlayMessage(Component.literal(up ? "Aufzug: Dach" : "Aufzug: Lobby")
                .withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }

    private BlockPos find(Level level, BlockPos from, boolean up) {
        BlockPos.MutableBlockPos p = from.mutable();
        if (up) {
            for (int y = from.getY() + 2; y < level.getMaxY(); y++) {
                p.setY(y);
                if (level.getBlockState(p).is(this)) {
                    return p.immutable();
                }
            }
        } else {
            for (int y = from.getY() - 2; y > level.getMinY(); y--) {
                p.setY(y);
                if (level.getBlockState(p).is(this)) {
                    return p.immutable();
                }
            }
        }
        return null;
    }
}
