package de.theboys.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.theboys.client.render.EffectRenderer;
import de.theboys.client.render.Geo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;

/** Hook into entity rendering (same place vanilla draws guardian beams) for player-attached effects. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
	private void theboys$effects(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState)) return;
		if (EffectRenderer.debugBoxes) {
			AABB box = new AABB(-0.5, 2.5, -0.5, 0.5, 3.5, 0.5);
			collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> Geo.box(buffer, pose, box, 0xFFFF0000));
			AABB box2 = new AABB(1.0, 2.5, -0.5, 2.0, 3.5, 0.5);
			collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> Geo.box(buffer, pose, box2, 0xFF2040FF));
		}
	}
}
