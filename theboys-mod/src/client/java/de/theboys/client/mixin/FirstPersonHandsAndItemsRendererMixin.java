package de.theboys.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.theboys.item.SyringeItem;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * First-person injection animation: the syringe swings over to the other arm, the needle goes in,
 * the plunger is pushed, and it comes back.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void theboys$injectStart(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float a, float b, InteractionHand hand,
			float c, ItemStack stack, float d, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		poseStack.pushPose();
		if (!(stack.getItem() instanceof SyringeItem) || state.useItemRemainingTicks <= 0) {
			return;
		}
		float used = SyringeItem.INJECT_TICKS - state.useItemRemainingTicks;
		float p = Mth.clamp(used / SyringeItem.INJECT_TICKS, 0f, 1f);
		float side = hand == InteractionHand.MAIN_HAND ? 1f : -1f;
		// 0-0.3: move across, 0.3-0.45: needle in, 0.45-0.85: push the plunger, 0.85-1: pull out
		float move = smooth(Math.min(1f, p / 0.3f));
		float stab = p < 0.3f ? 0f : p < 0.45f ? smooth((p - 0.3f) / 0.15f) : p < 0.85f ? 1f : 1f - smooth((p - 0.85f) / 0.15f);
		float shake = p > 0.45f && p < 0.85f ? Mth.sin(used * 2.4f) * 0.008f : 0f;
		poseStack.translate(-0.55f * move * side, -0.18f * move + 0.06f * stab, 0.1f * move - 0.12f * stab);
		poseStack.mulPose(Axis.YP.rotationDegrees(-35f * move * side));
		poseStack.mulPose(Axis.ZP.rotationDegrees(60f * move * side));
		poseStack.mulPose(Axis.XP.rotationDegrees(-20f * stab));
		poseStack.translate(shake, shake, 0);
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void theboys$injectEnd(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float a, float b, InteractionHand hand,
			float c, ItemStack stack, float d, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		poseStack.popPose();
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}
}
