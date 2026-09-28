package de.gtacity.block;

import com.mojang.serialization.MapCodec;
import de.gtacity.gameplay.Heists;
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

/** The safe in the back of every bank. Right click it with a drill to start a heist. */
public class BankVaultBlock extends Block {
    public static final MapCodec<BankVaultBlock> CODEC = simpleCodec(BankVaultBlock::new);

    public BankVaultBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            Heists.interact(serverPlayer, pos, stack);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            Heists.interact(serverPlayer, pos, ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }
}
