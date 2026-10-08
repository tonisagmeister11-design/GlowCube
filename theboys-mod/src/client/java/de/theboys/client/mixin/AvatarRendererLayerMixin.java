package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.theboys.client.render.MouseLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

/** Every player renderer gets MiniMaus' ears and tail (only drawn for MiniMaus). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererLayerMixin {
	@SuppressWarnings({"unchecked", "rawtypes"})
	@Inject(method = "<init>", at = @At("RETURN"))
	private void theboys$mouseParts(CallbackInfo ci) {
		((LivingEntityRendererInvoker) (Object) this).theboys$addLayer(new MouseLayer((AvatarRenderer) (Object) this));
	}
}
