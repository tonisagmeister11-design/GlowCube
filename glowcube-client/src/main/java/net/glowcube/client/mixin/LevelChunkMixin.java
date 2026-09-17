package net.glowcube.client.mixin;

import net.glowcube.client.module.render.Search;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
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

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void glowcube$blockGeaendert(BlockPos pos, BlockState zustand, int flaggen,
                                         CallbackInfoReturnable<BlockState> info) {
        Search.blockGeaendert(pos, zustand);
    }
}
