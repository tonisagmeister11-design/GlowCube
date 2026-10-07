package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.theboys.item.SyringeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Third-person pose while injecting: one hand brings the needle to the other, bent arm. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void theboys$injectPose(AvatarRenderState state, CallbackInfo ci) {
		if (Minecraft.getInstance().level == null) return;
		Entity entity = Minecraft.getInstance().level.getEntity(state.id);
		if (!(entity instanceof LivingEntity living) || !living.isUsingItem() || !(living.getUseItem().getItem() instanceof SyringeItem)) {
			return;
		}
		@SuppressWarnings("unchecked")
		HumanoidModel<AvatarRenderState> model = (HumanoidModel<AvatarRenderState>) (Object) this;
		float progress = Mth.clamp(state.ticksUsingItem / SyringeItem.INJECT_TICKS, 0f, 1f);
		float reach = Math.min(1f, progress * 3f);
		float stab = progress > 0.35f && progress < 0.85f ? 0.15f : 0f;
		// left arm held out, palm up
		model.leftArm.xRot = -1.25f * reach;
		model.leftArm.yRot = 0.25f * reach;
		model.leftArm.zRot = 0f;
		// right hand brings the syringe across to the left forearm
		model.rightArm.xRot = (-1.1f - stab) * reach;
		model.rightArm.yRot = -0.75f * reach;
		model.rightArm.zRot = 0.1f;
		model.head.xRot = 0.45f * reach;
		model.head.yRot = 0.2f * reach;
	}
}
