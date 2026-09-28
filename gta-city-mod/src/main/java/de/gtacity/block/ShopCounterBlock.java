package de.gtacity.block;

import de.gtacity.network.ModNetworking;
import de.gtacity.shop.ShopType;
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

/** Shop counter. Right click opens the shop, sneak + right click with a gun robs the till. */
public class ShopCounterBlock extends Block {
    private final ShopType type;

    public ShopCounterBlock(ShopType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public ShopType type() {
        return type;
    }

    /** Robbing (sneak + right click with a gun) is handled in {@link de.gtacity.gameplay.CityEvents}. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetworking.openShop(serverPlayer, type, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
