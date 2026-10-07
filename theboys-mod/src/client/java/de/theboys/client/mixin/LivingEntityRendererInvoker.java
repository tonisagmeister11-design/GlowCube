package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The body transform of a mob, needed to cut its model exactly where it is drawn. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {
	@Invoker("setupRotations")
	void theboys$setupRotations(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale);

	@Invoker("scale")
	void theboys$scale(LivingEntityRenderState state, PoseStack poseStack);
}
