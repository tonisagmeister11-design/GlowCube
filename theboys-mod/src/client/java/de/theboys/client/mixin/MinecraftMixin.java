package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.theboys.power.ActiveState;
import de.theboys.power.PowerAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Homelander's X-ray vision: living things glow through walls. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void theboys$xray(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		Minecraft mc = (Minecraft) (Object) this;
		if (cir.getReturnValue() || mc.player == null || entity == mc.player || !(entity instanceof LivingEntity)) {
			return;
		}
		if (PowerAttachments.active(mc.player).has(ActiveState.XRAY) && entity.distanceToSqr(mc.player) < 72 * 72) {
			cir.setReturnValue(true);
		}
	}
}
