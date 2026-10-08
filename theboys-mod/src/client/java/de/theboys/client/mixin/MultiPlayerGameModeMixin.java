package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.theboys.client.PixelMining;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Tiny MiniMaus chips pixels instead of mining whole blocks. */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
	private void theboys$pixelStart(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
		if (PixelMining.mine(pos, face)) cir.setReturnValue(true);
	}

	@Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
	private void theboys$pixelContinue(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
		if (PixelMining.mine(pos, face)) cir.setReturnValue(true);
	}
}
