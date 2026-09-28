package de.gtacity.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Gas pump that blows up when it is shot. */
public class GasPumpBlock extends Block {
    public static final MapCodec<GasPumpBlock> CODEC = simpleCodec(GasPumpBlock::new);

    public GasPumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Called by the weapon code when a bullet hits the pump. */
    public static void blowUp(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0F, true,
                Level.ExplosionInteraction.NONE);
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
            if (level.getBlockState(near).getBlock() instanceof GasPumpBlock) {
                BlockPos other = near.immutable();
                level.getServer().execute(() -> {
                    if (level.getBlockState(other).getBlock() instanceof GasPumpBlock) {
                        blowUp(level, other);
                    }
                });
            }
        }
    }
}
