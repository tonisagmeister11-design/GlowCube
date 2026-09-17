package net.glowcube.client.mixin;

import net.glowcube.client.module.render.Search;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Meldet Search jede Blockaenderung.
 *
 * <p>Ohne das bliebe ein einmal gefundener Block fuer immer markiert, auch
 * nachdem ihn jemand abgebaut hat - und ein neu gesetzter tauchte erst beim
 * naechsten Nachladen des Chunks auf. Dieselbe Stelle benutzt BleachHack
 * fuer sein Search.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Shadow
    @Final
    Level level;

    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
    private void glowcube$blockGeaendert(BlockPos pos, BlockState zustand, int flaggen,
                                         CallbackInfoReturnable<BlockState> info) {
        // Im Einzelspieler laeuft derselbe Code auch im eingebauten Server.
        // Dessen Chunks gehen Search nichts an - sonst landen Fundstellen
        // aus einer anderen Dimension in derselben Liste.
        if (level != null && level.isClientSide) {
            Search.blockGeaendert(pos, zustand);
        }
    }
}
