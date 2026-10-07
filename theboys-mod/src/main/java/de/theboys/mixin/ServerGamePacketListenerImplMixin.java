package de.theboys.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** A running A-Train legitimately moves faster than vanilla's "moved too quickly" check allows. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "shouldCheckPlayerMovement", at = @At("HEAD"), cancellable = true)
	private void theboys$allowSuperSpeed(boolean fallFlying, CallbackInfoReturnable<Boolean> cir) {
		if (PowerAttachments.powerOf(player) == Power.A_TRAIN && PowerAttachments.active(player).has(ActiveState.SPEED)) {
			cir.setReturnValue(false);
		}
	}
}
