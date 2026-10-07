package de.theboys.client.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import de.theboys.client.BodyLean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;

/** Tilts the whole player: flying, sprinting at super speed, leaning into strikes. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererLeanMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void theboys$lean(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
		if (Minecraft.getInstance().level == null || state.isFallFlying || state.isVisuallySwimming) return;
		Entity entity = Minecraft.getInstance().level.getEntity(state.id);
		if (entity == null) return;
		float lean = BodyLean.update(entity, state.swingAnimation);
		if (Math.abs(lean) < 0.01f) return;
		// tilt around the middle of the body
		float pivot = 0.9f;
		poseStack.translate(0f, pivot, 0f);
		poseStack.mulPose(new Matrix4f().rotationX((float) Math.toRadians(-lean)));
		poseStack.translate(0f, -pivot, 0f);
	}
}
