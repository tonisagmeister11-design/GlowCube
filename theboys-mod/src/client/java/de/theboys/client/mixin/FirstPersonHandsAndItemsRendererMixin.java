package de.theboys.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * First-person injection: the other arm comes up, the syringe swings over to the forearm,
 * the needle goes in, the plunger is pressed (the hand trembles), and it is pulled out again.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererMixin {
	@Shadow
	private void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector collector, int light, float equippedProgress, float swingProgress, HumanoidArm arm, PlayerRenderState player) {
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void theboys$injectStart(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partialTicks, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		poseStack.pushPose();
		if (!(stack.getItem() instanceof SyringeItem) || state.useItemRemainingTicks <= 0) {
			return;
		}
		float used = SyringeItem.INJECT_TICKS - state.useItemRemainingTicks + partialTicks;
		float p = Mth.clamp(used / SyringeItem.INJECT_TICKS, 0f, 1f);
		float side = hand == InteractionHand.MAIN_HAND ? 1f : -1f;
		// phases: 0-.25 raise the arm and bring the syringe over, .25-.4 needle in, .4-.85 press, .85-1 pull out
		float move = smooth(Math.min(1f, p / 0.25f));
		float stab = p < 0.25f ? 0f : p < 0.4f ? smooth((p - 0.25f) / 0.15f) : p < 0.85f ? 1f : 1f - smooth((p - 0.85f) / 0.15f);
		float press = p < 0.4f ? 0f : Math.min(1f, (p - 0.4f) / 0.45f);
		float tremble = press > 0 && press < 1 ? Mth.sin(used * 3.1f) * 0.006f : 0f;

		// the other arm, held out with the forearm facing up
		poseStack.pushPose();
		poseStack.translate(0.18f * move * side, -0.05f * move, -0.05f * move);
		poseStack.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(-12f * move * side)));
		renderPlayerArm(poseStack, collector, light, 1f - move, 0f, side > 0 ? HumanoidArm.LEFT : HumanoidArm.RIGHT, player);
		poseStack.popPose();

		// the syringe swings over to the forearm: turn it around the spot where it is normally held
		// (vanilla holds items at about side*0.56, -0.52, -0.72) so the needle points down at the arm
		float px = 0.56f * side, py = -0.52f, pz = -0.72f;
		poseStack.translate(-0.6f * move * side, 0.26f * move - 0.07f * stab, -0.05f * stab + 0.02f * press);
		poseStack.translate(px, py, pz);
		poseStack.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(140f * move * side)));
		poseStack.mulPose(new Matrix4f().rotation(Axis.XP.rotationDegrees(-15f * stab)));
		poseStack.translate(-px, -py, -pz);
		poseStack.translate(tremble, tremble, 0);
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void theboys$injectEnd(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partialTicks, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		poseStack.popPose();
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}
}
