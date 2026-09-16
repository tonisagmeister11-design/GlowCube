package net.glowcube.client.mixin;

import net.glowcube.client.module.render.XRay;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * X-Ray. Minecraft fragt hier, ob eine Blockseite ueberhaupt gezeichnet werden
 * muss - normalerweise nur, wenn dahinter Luft ist. Solange X-Ray laeuft,
 * entscheidet stattdessen die Liste: Bloecke darauf zeigen immer alle Seiten,
 * alle anderen keine.
 */
@Mixin(Block.class)
public final class BlockMixin {

    @Inject(method = "shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void glowcube$xray(BlockState state, BlockState neighbour, Direction face,
                                      CallbackInfoReturnable<Boolean> info) {
        if (XRay.active()) {
            info.setReturnValue(XRay.visible(state));
        }
    }
}
