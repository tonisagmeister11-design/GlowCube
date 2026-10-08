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
					float lean = de.theboys.client.BodyLean.current(entity);
					float level = Mth.clamp(lean / 80f, 0f, 1f);
					float drift = Mth.sin(t * 0.12f);
					// fist forward like Superman, the other arm pressed to his side; hovering: arms loose, fists closed
					model.rightArm.xRot = Mth.lerp(level, -0.35f, -2.95f) + drift * 0.04f;
					model.rightArm.yRot = Mth.lerp(level, 0f, 0.05f);
					model.rightArm.zRot = Mth.lerp(level, 0.28f, 0.05f);
					model.leftArm.xRot = Mth.lerp(level, -0.2f, 0.12f) - drift * 0.03f;
					model.leftArm.yRot = 0f;
					model.leftArm.zRot = Mth.lerp(level, -0.28f, -0.08f);
					// legs straight together, trailing behind, feet fluttering slightly
					float flutter = Mth.sin(t * 0.25f) * 0.06f * (1 - level * 0.5f);
					model.rightLeg.xRot = 0.08f + flutter;
					model.leftLeg.xRot = 0.16f - flutter;
					model.rightLeg.yRot = 0f;
					model.leftLeg.yRot = 0f;
					model.rightLeg.zRot = 0.02f;
					model.leftLeg.zRot = -0.02f;
					// keep looking where he flies, not at the ground
					model.head.xRot -= (float) Math.toRadians(lean) * 0.85f;
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
			case MINIMAUS -> theboys$miniMaus(model, entity, active, state, t);
			default -> { }
		}

		if (power == Power.A_TRAIN) {
			model.head.xRot -= (float) Math.toRadians(de.theboys.client.BodyLean.current(entity)) * 0.8f;
		}
		boolean busy = active.has(ActiveState.HOLD) || active.has(ActiveState.RIP) || active.has(ActiveState.NUKE_CHARGE)
				|| active.has(ActiveState.SMASH) || active.has(ActiveState.MOON) && active.targetId() >= 0;
		if (!busy) {
			theboys$fight(model, state, entity, t);
		}
	}

	/** Twists the torso and moves the shoulders with it. */
	private static void theboys$twist(HumanoidModel<AvatarRenderState> model, float bodyY) {
		model.body.yRot = bodyY;
		model.rightArm.z = Mth.sin(bodyY) * 5f;
		model.rightArm.x = -Mth.cos(bodyY) * 5f;
		model.leftArm.z = -Mth.sin(bodyY) * 5f;
		model.leftArm.x = Mth.cos(bodyY) * 5f;
	}

	/** MiniMaus: swinging a victim by the feet, winding up for To the Moon, lurking for a bite. */
	private static void theboys$miniMaus(HumanoidModel<AvatarRenderState> model, Entity entity, ActiveState active, AvatarRenderState state, float t) {
		float pt = t - (float) Math.floor(t);
		if (active.has(ActiveState.SMASH)) {
			Entity victim = entity.level().getEntity(active.targetId());
			float max = victim != null ? de.theboys.power.MiniMausMath.maxAngle(entity, victim) : 100f;
			float a = (float) Math.toRadians(de.theboys.power.MiniMausMath.angle(active.charge() + pt, max));
			// both hands over the head, following the body she swings around
			model.rightArm.xRot = -3.05f;
			model.leftArm.xRot = -3.05f;
			model.rightArm.yRot = 0f;
			model.leftArm.yRot = 0f;
			model.rightArm.zRot = -a + 0.1f;
			model.leftArm.zRot = -a - 0.1f;
			theboys$twist(model, -a * 0.15f);
			model.head.xRot = -0.35f;
			model.rightLeg.xRot = 0f;
			model.leftLeg.xRot = 0f;
			model.rightLeg.zRot = 0.28f;
			model.leftLeg.zRot = -0.28f;
			return;
		}
		if (active.has(ActiveState.MOON)) {
			int c = active.charge();
			if (c >= 100) {
				// the blow: a huge uppercut, follow-through high over the head
				float k = (c - 100) / 8f;
				theboys$twist(model, -0.55f * k);
				model.rightArm.xRot = -2.95f;
				model.rightArm.yRot = -0.55f * k;
				model.rightArm.zRot = -0.15f;
				model.leftArm.xRot = 0.5f * k;
				model.leftArm.zRot = -0.3f;
				model.head.xRot -= 0.5f;
				model.rightLeg.xRot = 0.35f * k;
				model.leftLeg.xRot = -0.45f * k;
			} else if (active.targetId() >= 0) {
				// winding up: fist pulled far back and down, body coiled
				float k = Mth.clamp((c + pt) / 9f, 0f, 1f);
				float shake = Mth.sin(t * 3.1f) * 0.03f * k;
				theboys$twist(model, 0.75f * k);
				model.rightArm.xRot = 1.1f * k + shake;
				model.rightArm.yRot = 0.75f * k;
				model.rightArm.zRot = 0.45f * k;
				model.leftArm.xRot = -1.3f * k;
				model.leftArm.yRot = 0.75f * k + 0.3f;
				model.rightLeg.xRot = 0.5f * k;
				model.leftLeg.xRot = -0.45f * k;
			} else {
				// armed: fist cocked at the hip
				model.rightArm.xRot = -0.5f;
				model.rightArm.zRot = 0.25f;
				model.leftArm.xRot = -1.0f;
				model.leftArm.yRot = 0.45f;
			}
			return;
		}
		if (active.has(ActiveState.BITE)) {
			Long bit = de.theboys.client.ClientState.BITES.get(entity.getId());
			float since = bit == null ? 99f : entity.level().getGameTime() - bit + pt;
			if (since < 8f) {
				// the lunge: head snaps forward, claws grab
				float k = since < 2f ? since / 2f : Math.max(0f, 1f - (since - 2f) / 6f);
				model.head.z -= 3.0f * k;
				model.head.xRot += 0.35f * k;
				model.body.xRot = 0.35f * k;
				model.rightArm.xRot = -1.7f * k;
				model.leftArm.xRot = -1.7f * k;
				model.rightArm.zRot = -0.3f * k;
				model.leftArm.zRot = 0.3f * k;
			} else {
				// lurking: paws up, claws twitching
				float twitch = Mth.sin(t * 0.9f) * 0.08f;
				model.rightArm.xRot = -1.15f + twitch;
				model.leftArm.xRot = -1.15f - twitch;
				model.rightArm.zRot = -0.35f;
				model.leftArm.zRot = 0.35f;
				model.head.xRot += 0.15f;
			}
		}
	}

	/** Fight moves: every swing is a different move of a combo, depending on the weapon. */
	private static void theboys$fight(HumanoidModel<AvatarRenderState> model, AvatarRenderState state, Entity entity, float t) {
		de.theboys.client.CombatAnim.Swing swing = de.theboys.client.CombatAnim.get(entity.getId());
		float p = state.swingAnimation;
		if (swing == null || p <= 0f) return;
		float[][] move = theboys$move(swing.kind, swing.combo);
		float[] w = move[0], e = move[1];
		// punches alternate fists
		boolean left = swing.leftArm ^ (swing.kind == de.theboys.client.CombatAnim.FIST && swing.combo % 4 == 1);
		float m = left ? -1f : 1f;
		net.minecraft.client.model.geom.ModelPart arm = left ? model.leftArm : model.rightArm;
		net.minecraft.client.model.geom.ModelPart other = left ? model.rightArm : model.leftArm;
		net.minecraft.client.model.geom.ModelPart front = left ? model.rightLeg : model.leftLeg;
		net.minecraft.client.model.geom.ModelPart back = left ? model.leftLeg : model.rightLeg;

		// rest -> wind-up -> strike -> rest
		float[] rest = {arm.xRot, arm.yRot - model.body.yRot, arm.zRot, 0f, other.xRot, other.yRot - model.body.yRot, other.zRot, 0f};
		float[] pose = new float[8];
		if (p < 0.22f) {
			float k = theboys$smooth(p / 0.22f);
			for (int i = 0; i < 8; i++) pose[i] = Mth.lerp(k, rest[i], w[i]);
		} else if (p < 0.5f) {
			// the strike itself is fast and snappy
			float k = theboys$snap((p - 0.22f) / 0.28f);
			for (int i = 0; i < 8; i++) pose[i] = Mth.lerp(k, w[i], e[i]);
		} else {
			float k = theboys$smooth((p - 0.5f) / 0.5f);
			for (int i = 0; i < 8; i++) pose[i] = Mth.lerp(k, e[i], rest[i]);
		}
		float bodyY = pose[3] * m;
		model.body.yRot = bodyY;
		// shoulders follow the twisting torso
		model.rightArm.z = Mth.sin(bodyY) * 5f;
		model.rightArm.x = -Mth.cos(bodyY) * 5f;
		model.leftArm.z = -Mth.sin(bodyY) * 5f;
		model.leftArm.x = Mth.cos(bodyY) * 5f;
		arm.xRot = pose[0];
		arm.yRot = pose[1] * m + bodyY;
		arm.zRot = pose[2] * m;
		other.xRot = pose[4];
		other.yRot = pose[5] * m + bodyY;
		other.zRot = pose[6] * m;
		// step into the strike
		float step = pose[7];
		front.xRot = -0.55f * step;
		back.xRot = 0.45f * step;
		front.zRot = 0f;
		back.zRot = 0f;
		// the head stays on the target while the body leans
		model.head.xRot -= (float) Math.toRadians(de.theboys.client.CombatAnim.lean(entity, p)) * 0.9f;
	}

	/** {wind-up, end of strike}: arm x/y/z, body twist, other arm x/y/z, step. Right-handed; mirrored for left. */
	private static float[][] theboys$move(int kind, int combo) {
		return switch (kind) {
			case de.theboys.client.CombatAnim.AXE -> combo % 2 == 0
					// two-handed overhead chop
					? new float[][] {{-2.95f, -0.2f, 0f, 0.15f, -2.85f, 0.6f, 0f, 0.15f}, {-0.55f, -0.35f, 0f, -0.2f, -0.6f, 0.75f, 0f, 0.85f}}
					// two-handed sweep from the right to the left
					: new float[][] {{-1.5f, 1.0f, 0.3f, 0.75f, -1.4f, 1.25f, 0f, 0.3f}, {-1.3f, -1.15f, -0.1f, -0.75f, -1.2f, -0.55f, 0f, 0.55f}};
			case de.theboys.client.CombatAnim.THRUST ->
					new float[][] {{-0.7f, 0.25f, 0.1f, 0.5f, -1.0f, 0.5f, 0f, 0.2f}, {-1.62f, -0.1f, 0f, -0.45f, -1.5f, 0.6f, 0f, 0.95f}};
			case de.theboys.client.CombatAnim.SWORD -> switch (combo % 4) {
				// forehand slash: from high on the right, diagonally down across the body
				case 0 -> new float[][] {{-2.7f, 0.7f, 0.5f, 0.55f, -0.4f, 0f, -0.3f, 0.2f}, {-0.5f, -1.0f, -0.2f, -0.6f, 0.5f, 0f, -0.1f, 0.5f}};
				// backhand: back across from the left, rising
				case 1 -> new float[][] {{-1.7f, -1.2f, -0.2f, -0.6f, 0.3f, 0f, -0.2f, 0.2f}, {-1.45f, 1.0f, 0.6f, 0.55f, -0.6f, 0f, -0.4f, 0.4f}};
				// overhead cleave
				case 2 -> new float[][] {{-3.1f, 0.1f, 0.1f, 0.15f, -0.5f, 0f, -0.4f, 0.1f}, {-0.35f, -0.2f, 0f, -0.25f, 0.6f, 0f, -0.2f, 0.75f}};
				// lunging stab
				default -> new float[][] {{-0.9f, 0.4f, 0.2f, 0.6f, -0.8f, 0f, -0.3f, 0.1f}, {-1.65f, -0.15f, 0f, -0.55f, 0.5f, 0f, -0.1f, 0.85f}};
			};
			default -> switch (combo % 4) {
				// jab and cross (the cross is thrown with the other fist), the guard hand stays up
				case 0, 1 -> new float[][] {{-1.2f, 0.2f, 0.2f, 0.3f, -1.35f, 0.45f, 0f, 0.2f}, {-1.62f, -0.18f, 0f, -0.4f, -1.3f, 0.45f, 0f, 0.45f}};
				// uppercut
				case 2 -> new float[][] {{-0.3f, 0.3f, 0.2f, 0.45f, -1.3f, 0.4f, 0f, 0.3f}, {-2.5f, -0.3f, 0f, -0.45f, -1.2f, 0.4f, 0f, 0.5f}};
				// hook
				default -> new float[][] {{-1.4f, 1.1f, 0.4f, 0.65f, -1.3f, 0.4f, 0f, 0.2f}, {-1.55f, -0.9f, 0.2f, -0.7f, -1.3f, 0.4f, 0f, 0.45f}};
			};
		};
	}

	private static float theboys$smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3 - 2 * x);
	}

	/** Fast start, hard stop: the snap of a strike. */
	private static float theboys$snap(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return 1f - (1f - x) * (1f - x) * (1f - x);
	}
}
