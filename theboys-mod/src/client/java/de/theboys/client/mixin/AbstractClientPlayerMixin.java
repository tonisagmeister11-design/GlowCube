package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.minecraft.client.player.AbstractClientPlayer;

/** At A-Train speeds the vanilla speed FOV would turn the screen into a tunnel; keep it sane. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void theboys$clampFov(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (PowerAttachments.powerOf(self) == Power.A_TRAIN && PowerAttachments.active(self).has(ActiveState.SPEED)) {
			boolean timeJump = PowerAttachments.active(self).has(ActiveState.REWIND);
			cir.setReturnValue(Math.min(cir.getReturnValue(), timeJump ? 1.3f : 1.15f));
		}
	}
}
