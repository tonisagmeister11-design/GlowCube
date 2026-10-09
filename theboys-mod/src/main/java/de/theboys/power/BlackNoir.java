package de.theboys.power;

import de.theboys.TheBoys;
import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Black Noir: the silent ninja. R katana combo (dashes in, three cuts), G vanishes into the shadows,
 * C shadow step behind the target, X throwing knives. Heals fast, never speaks - and nuts nearly kill him.
 */
public final class BlackNoir {
	public static final Identifier AMBUSH = TheBoys.id("black_noir_ambush");
	private static final int COMBO_TICKS = 14;

	private BlackNoir() {
	}

	static void key(ServerPlayer player, PlayerSession s, int slot, boolean pressed) {
		if (!pressed) return;
		if (!s.ready(slot)) {
			PowerManager.notReady(player, s, slot);
			return;
		}
		switch (slot) {
			case 0 -> combo(player, s);
			case 1 -> shadow(player, s);
			case 2 -> shadowStep(player, s);
			case 3 -> knives(player, s);
			default -> { }
		}
	}

	static int tick(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		ActiveState state = PowerAttachments.active(player);

		// heals fast
		if (player.tickCount % 30 == 0 && player.getHealth() < player.getMaxHealth() && player.getHealth() > 0) {
			player.heal(1.5f);
		}
		// nut allergy: cookies (and anything with cocoa) nearly kill him
		if (player.isUsingItem() && player.getUseItem().is(Items.COOKIE) && player.getUseItemRemainingTicks() <= 1) {
			allergy(player);
		}

		if (s.comboTicks > 0) tickCombo(player, s, level);
		if (s.shadowTicks > 0) {
			s.shadowTicks--;
			if (player.tickCount % 3 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.3, player.getZ(), 2, 0.25, 0.2, 0.25, 0.005);
			}
			// whoever was after him loses him
			if (player.tickCount % 10 == 0) {
				for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), m -> m.getTarget() == player)) {
					m.setTarget(null);
				}
			}
			if (s.shadowTicks == 0) leaveShadow(player, s);
		}
		if (s.ambushTicks > 0) s.ambushTicks--;
		boolean ambush = s.shadowTicks > 0 || s.ambushTicks > 0;
		PowerManager.modifier(player, Attributes.ATTACK_DAMAGE, AMBUSH, ambush ? 10 : 0, AttributeModifier.Operation.ADD_VALUE);

		state = state.with(ActiveState.KATANA, s.comboTicks > 0).with(ActiveState.SHADOW, s.shadowTicks > 0);
		PowerAttachments.setActive(player, state.withTarget(s.comboTicks > 0 ? s.comboTarget : -1));
		return 0;
	}

	/** Called when he lands a normal hit: an ambush out of the shadows ends it. */
	public static void onAttack(ServerPlayer player, Entity target) {
		PlayerSession s = PowerManager.session(player);
		if (s.shadowTicks > 0 || s.ambushTicks > 0) {
			// the +10 damage modifier is still on for this hit; it comes off next tick
			s.ambushTicks = 0;
			if (s.shadowTicks > 0) {
				s.shadowTicks = 0;
				leaveShadow(player, s);
			}
			ServerLevel level = player.level();
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 25, 0.3, 0.4, 0.3, 0.3);
			Supe.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.5f, 0.6f);
			Supe.blood(level, Supe.chest(target), 1f);
		}
	}

	private static void allergy(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 2));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 300, 0));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 2));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 1));
		player.sendSystemMessage(Component.translatable("message.theboys.noir_allergy").withStyle(ChatFormatting.DARK_RED), true);
		Supe.sound(player.level(), player.position(), SoundEvents.PLAYER_HURT_DROWN, 1.2f, 0.7f);
	}

	// ------------------------------------------------------------------ katana

	private static Entity target(ServerPlayer player, double range, float margin) {
		Entity e = Supe.ray(player, player.getEyePosition(), range, margin).entity();
		if (e != null) return e;
		Vec3 look = player.getLookAngle();
		double best = Double.MAX_VALUE;
		for (LivingEntity l : Supe.livingAround(player.level(), player.position(), Math.min(range, 7), player)) {
			Vec3 to = l.position().subtract(player.position());
			if (to.normalize().dot(look) < 0.5) continue;
			if (to.lengthSqr() < best) {
				best = to.lengthSqr();
				e = l;
			}
		}
		return e;
	}

	/** Katana combo: dashes at the target and cuts it three times. */
	private static void combo(ServerPlayer player, PlayerSession s) {
		Entity t = target(player, 10, 1.0f);
		s.cool(0, 140);
		s.comboTicks = COMBO_TICKS;
		s.comboTarget = t != null ? t.getId() : -1;
		Supe.sound(player.level(), player.position(), SoundEvents.TRIDENT_RETURN, 1.2f, 1.6f);
		if (t != null) {
			Vec3 to = t.position().subtract(player.position());
			double dist = to.horizontalDistance();
			if (dist > 1.8) {
				Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
				player.setDeltaMovement(dir.scale(Math.min(2.4, (dist - 1.4) * 0.55)).add(0, 0.12, 0));
				player.needsSync = true;
			}
		}
	}

	private static void tickCombo(ServerPlayer player, PlayerSession s, ServerLevel level) {
		int t = COMBO_TICKS - s.comboTicks--;
		Entity target = s.comboTarget >= 0 ? level.getEntity(s.comboTarget) : null;
		if (t != 4 && t != 8 && t != 12) return;
		swing(player, InteractionHand.MAIN_HAND);
		Supe.sound(level, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.3f + t * 0.03f);
		if (target == null || !target.isAlive() || target.distanceTo(player) > 4.5) return;
		boolean last = t == 12;
		Supe.hurt(player, target, last ? 10f : 7f);
		Vec3 at = Supe.chest(target);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		if (target instanceof LivingEntity l) {
			if (last) {
				l.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1), player);
				Supe.push(l, player.getLookAngle().scale(0.9).add(0, 0.3, 0));
			}
			Supe.blood(level, at, l.isDeadOrDying() ? 3f : 1f);
		}
	}

	// ------------------------------------------------------------------ shadows

	private static void shadow(ServerPlayer player, PlayerSession s) {
		s.cool(1, 500);
		s.shadowTicks = 200;
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1, false, false));
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.02);
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1, player.getZ(), 25, 0.4, 0.8, 0.4, 0.05);
		Supe.sound(level, player.position(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 0.6f);
	}

	private static void leaveShadow(ServerPlayer player, PlayerSession s) {
		player.removeEffect(MobEffects.INVISIBILITY);
		player.removeEffect(MobEffects.SPEED);
		player.level().sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
	}

	/** Shadow step: gone in a puff of smoke, standing right behind the target. The next hit is an ambush. */
	private static void shadowStep(ServerPlayer player, PlayerSession s) {
		Entity t = target(player, 28, 1.5f);
		ServerLevel level = player.level();
		Vec3 from = player.position();
		Vec3 to;
		float yaw;
		if (t != null) {
			Vec3 back = Vec3.directionFromRotation(0, t.getYRot()).scale(-1.4);
			to = t.position().add(back);
			if (!free(player, to)) to = t.position().add(new Vec3(back.z, 0, -back.x));
			if (!free(player, to)) to = t.position().subtract(back);
			Vec3 look = t.position().subtract(to);
			yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
		} else {
			// nobody there: a blink of up to 10 blocks forward
			Supe.Ray ray = Supe.ray(player, player.getEyePosition(), 10);
			Vec3 dir = player.getLookAngle();
			to = ray.end().subtract(dir.scale(0.8)).subtract(0, player.getEyeHeight(), 0);
			if (!free(player, to)) {
				s.cool(2, 20);
				return;
			}
			yaw = player.getYRot();
		}
		s.cool(2, 160);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y + 1, from.z, 30, 0.3, 0.7, 0.3, 0.02);
		level.sendParticles(ParticleTypes.SQUID_INK, from.x, from.y + 1, from.z, 20, 0.3, 0.7, 0.3, 0.05);
		player.connection.teleport(to.x, to.y, to.z, yaw, player.getXRot());
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, to.x, to.y + 1, to.z, 30, 0.3, 0.7, 0.3, 0.02);
		Supe.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.5f);
		Supe.sound(level, to, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.5f);
		if (t != null) s.ambushTicks = 80;
	}

	private static boolean free(ServerPlayer player, Vec3 at) {
		AABB box = player.getBoundingBox().move(at.subtract(player.position()));
		return player.level().noCollision(player, box);
	}

	// ------------------------------------------------------------------ knives

	/** Three throwing knives in a fan: hit instantly, cut deep. */
	private static void knives(ServerPlayer player, PlayerSession s) {
		s.cool(3, 80);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		swing(player, InteractionHand.OFF_HAND);
		for (int i = -1; i <= 1; i++) {
			Vec3 dir = Vec3.directionFromRotation(player.getXRot(), player.getYRot() + i * 6f);
			Vec3 end = eye.add(dir.scale(32));
			var blockHit = level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, player));
			Vec3 stop = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : blockHit.getLocation();
			var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level, player, eye, stop, new AABB(eye, stop).inflate(1),
					e -> e != player && e.isAlive() && e.isPickable() && !e.isSpectator(), 0.4f);
			if (hit != null) {
				stop = hit.getLocation();
				Entity e = hit.getEntity();
				Supe.hurt(player, e, 6.5f);
				if (e instanceof LivingEntity l) {
					l.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), player);
					Supe.blood(level, stop, l.isDeadOrDying() ? 2.5f : 0.6f);
				}
				Supe.sound(level, stop, SoundEvents.TRIDENT_HIT, 1.0f, 1.4f);
			} else {
				level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 6, 0.05, 0.05, 0.05, 0.1);
				Supe.sound(level, stop, SoundEvents.TRIDENT_HIT_GROUND, 0.8f, 1.6f);
			}
			ModNetworking.sendFx(level, eye, new FxPayload(FxPayload.KNIFE, player.getId(), (float) eye.x, (float) eye.y - 0.2f, (float) eye.z,
					(float) stop.x, (float) stop.y, (float) stop.z));
		}
		Supe.sound(level, eye, SoundEvents.TRIDENT_THROW, 1.0f, 1.8f);
	}

	/** Plays the arm swing for everyone, him included (so the fight animation and slash trail show up). */
	private static void swing(ServerPlayer player, InteractionHand hand) {
		player.level().getChunkSource().broadcastAndSend(player, new net.minecraft.network.protocol.game.ClientboundAnimatePacket(player,
				hand == InteractionHand.MAIN_HAND ? net.minecraft.network.protocol.game.ClientboundAnimatePacket.SWING_MAIN_HAND
						: net.minecraft.network.protocol.game.ClientboundAnimatePacket.SWING_OFF_HAND));
	}

	static void stop(ServerPlayer player, PlayerSession s) {
		if (s.shadowTicks > 0) leaveShadow(player, s);
		s.shadowTicks = 0;
		s.comboTicks = 0;
		s.ambushTicks = 0;
		PowerManager.modifier(player, Attributes.ATTACK_DAMAGE, AMBUSH, 0, AttributeModifier.Operation.ADD_VALUE);
	}
}
