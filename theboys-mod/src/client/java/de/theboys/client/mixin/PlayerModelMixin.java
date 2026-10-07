package de.theboys.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.theboys.item.SyringeItem;
import de.theboys.power.ActiveState;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Third-person poses: injecting, A-Train's sprint, Soldier Boy charging, Homelander flying, Butcher's tendrils. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void theboys$poses(AvatarRenderState state, CallbackInfo ci) {
		if (Minecraft.getInstance().level == null) return;
		Entity entity = Minecraft.getInstance().level.getEntity(state.id);
		if (!(entity instanceof LivingEntity living)) return;
		@SuppressWarnings("unchecked")
		HumanoidModel<AvatarRenderState> model = (HumanoidModel<AvatarRenderState>) (Object) this;
		float t = state.ageInTicks;

		if (living.isUsingItem() && living.getUseItem().getItem() instanceof SyringeItem) {
			float progress = Mth.clamp(state.ticksUsingItem / SyringeItem.INJECT_TICKS, 0f, 1f);
			float reach = Math.min(1f, progress * 4f);
			float stab = progress > 0.25f && progress < 0.85f ? 0.18f : 0f;
			float tremble = progress > 0.4f && progress < 0.85f ? Mth.sin(t * 3f) * 0.03f : 0f;
			model.leftArm.xRot = -1.3f * reach;
			model.leftArm.yRot = 0.35f * reach;
			model.leftArm.zRot = 0f;
			model.rightArm.xRot = (-1.05f - stab) * reach + tremble;
			model.rightArm.yRot = -0.85f * reach;
			model.rightArm.zRot = 0.1f;
			model.head.xRot = 0.5f * reach;
			model.head.yRot = 0.25f * reach;
			return;
		}

		Power power = PowerAttachments.powerOf(entity);
		ActiveState active = PowerAttachments.active(entity);
		switch (power) {
			case A_TRAIN -> {
				double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
				if (active.has(ActiveState.SPEED) && dx * dx + dz * dz > 0.09) {
					// speedster sprint: arms swept back, head down
					float swing = Mth.sin(t * 1.6f) * 0.12f;
					model.rightArm.xRot = 1.25f + swing;
					model.leftArm.xRot = 1.25f - swing;
					model.rightArm.zRot = 0.25f;
					model.leftArm.zRot = -0.25f;
					model.head.xRot = Math.min(model.head.xRot, 0.1f) + 0.15f;
				}
			}
			case SOLDIER_BOY -> {
				if (active.has(ActiveState.NUKE_CHARGE)) {
					// arms spread wide, head thrown back, shaking with the build-up
					float c = Math.min(1f, active.charge() / 60f);
					float shake = Mth.sin(t * 2.7f) * 0.05f * c;
					model.rightArm.zRot = 1.1f * c + shake;
					model.leftArm.zRot = -1.1f * c - shake;
					model.rightArm.xRot = -0.3f * c;
					model.leftArm.xRot = -0.3f * c;
					model.head.xRot = -0.45f * c;
				} else if (active.has(ActiveState.CHEST_BEAM)) {
					model.rightArm.zRot = 0.65f;
					model.leftArm.zRot = -0.65f;
					model.rightArm.xRot = 0.35f;
					model.leftArm.xRot = 0.35f;
				}
			}
			case HOMELANDER -> {
				if (active.has(ActiveState.FLYING)) {
					// flying: one fist forward, the other arm at his side
					model.rightArm.xRot = -2.9f;
					model.rightArm.zRot = 0.05f;
					model.leftArm.xRot = 0.2f;
					model.leftArm.zRot = -0.1f;
				}
			}
			case BUTCHER -> {
				if (active.has(ActiveState.RIP)) {
					// pulling the victim apart
					float pull = 0.9f + Mth.sin(t * 2.2f) * 0.12f;
					model.rightArm.xRot = -1.3f;
					model.leftArm.xRot = -1.3f;
					model.rightArm.yRot = -0.15f;
					model.leftArm.yRot = 0.15f;
					model.rightArm.zRot = pull;
					model.leftArm.zRot = -pull;
				} else if (active.has(ActiveState.HOLD)) {
					// reaching for the victim held by the tendrils
					model.rightArm.xRot = -1.45f;
					model.leftArm.xRot = -1.2f;
					model.rightArm.zRot = 0.2f;
					model.leftArm.zRot = -0.35f;
				}
			}
			default -> { }
		}
	}
}
