package de.gtacity.block;

import de.gtacity.gameplay.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The desk of a job station. Right click opens the job board of that station. */
public class JobDeskBlock extends Block {
    private final boolean shady;

    public JobDeskBlock(boolean shady, Properties properties) {
        super(properties);
        this.shady = shady;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            Jobs.openBoard(serverPlayer, shady ? Jobs.Station.SHADY : Jobs.Station.JOBCENTER);
        }
        return InteractionResult.SUCCESS;
    }
}
