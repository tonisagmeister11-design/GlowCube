package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import de.theboys.client.render.SmashPose;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Multi Smash: the victim hangs by its feet from MiniMaus' hands and is swung over her head. */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererSmashMixin {
	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void theboys$swung(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
		SmashPose.apply(state, poseStack);
	}
}
