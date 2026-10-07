package de.theboys.power;

import java.util.Iterator;
import java.util.Map;

import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import de.theboys.time.TimeRewind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A-Train: super speed (toggle) with auto-step and run-through kills, the Time Jump (runs so fast
 * that time flows backwards for everybody else), a blitz dash and a punch barrage.
 * Weakness: his heart. Pushing the speed fills the strain meter; when it is full he has a heart attack.
 */
public final class ATrain {
	/** Movement speed multiplier while running (on top of his base speed). */
	public static final double RUN_MULTIPLIER = 9.0;
	/** Multiplier during the Time Jump - roughly 1000 km/h when sprinting. */
	public static final double TIME_JUMP_MULTIPLIER = 45.0;
	public static final int TIME_JUMP_TICKS = 110;

	private ATrain() {
	}

	static void key(ServerPlayer player, PlayerSession s, int slot, boolean pressed) {
		if (!pressed) return;
		switch (slot) {
			case 0 -> {
				if (s.rewindTicks > 0) return;
				setSpeed(player, s, !s.speed);
			}
			case 1 -> timeJump(player, s);
			case 2 -> blitz(player, s);
			case 3 -> barrage(player, s);
			default -> { }
		}
	}

	static int tick(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		ActiveState state = PowerAttachments.active(player);

		boolean running = s.speed || s.rewindTicks > 0 || s.dashTicks > 0;
		if (s.rewindTicks > 0) {
			s.rewindTicks--;
			s.strain += 0.45f;
			if (s.rewindTicks == 0) {
				PowerManager.modifier(player, Attributes.MOVEMENT_SPEED, PowerManager.RUN, s.speed ? RUN_MULTIPLIER : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				Supe.sound(level, player.position(), SoundEvents.BEACON_DEACTIVATE, 1.5f, 1.5f);
			}
		}
		if (s.dashTicks > 0) {
			s.dashTicks--;
			Vec3 look = player.getLookAngle();
			Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
			player.setDeltaMovement(dir.scale(3.2).add(0, Math.min(0, player.getDeltaMovement().y), 0));
			player.needsSync = true;
		}
		if (s.speed) {
			s.strain += 0.035f;
			player.causeFoodExhaustion(0.02f);
		}
		if (!running) {
			s.strain = Math.max(0, s.strain - 0.12f);
		}
		if (running) {
			runThrough(player, s, level);
			if (s.horizontalSpeed() > 0.6 && player.tickCount % 2 == 0) {
				Vec3 p = player.position();
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 1, p.z, 3, 0.3, 0.5, 0.3, 0.05);
				level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 0.1, p.z, 1, 0.2, 0.0, 0.2, 0.01);
			}
		}
		player.resetFallDistance();

		// punch barrage
		if (s.barrageTicks > 0) {
			s.barrageTicks--;
			Entity target = level.getEntity(s.barrageTarget);
			if (target instanceof LivingEntity living && living.isAlive() && living.distanceToSqr(player) < 36) {
				Supe.hurt(player, living, 2.5f);
				Vec3 at = living.position().add(0, living.getBbHeight() * 0.6, 0);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.3);
				Supe.sound(level, at, SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 1.4f + level.getRandom().nextFloat() * 0.4f);
				if (s.barrageTicks == 0) {
					Supe.push(living, player.getLookAngle().scale(2.5).add(0, 0.5, 0));
					if (living.isDeadOrDying()) Supe.blood(level, at, 2.5f);
				}
			} else {
				s.barrageTicks = 0;
			}
		}

		// heart attack
		if (s.strain >= 100) {
			heartAttack(player, s);
		}

		state = state.with(ActiveState.SPEED, running).with(ActiveState.REWIND, s.rewindTicks > 0);
		PowerAttachments.setActive(player, state);
		return (int) Math.min(1000, s.strain * 10);
	}

	public static void setSpeed(ServerPlayer player, PlayerSession s, boolean on) {
		s.speed = on;
		PowerManager.modifier(player, Attributes.MOVEMENT_SPEED, PowerManager.RUN, on ? RUN_MULTIPLIER : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerManager.modifier(player, Attributes.STEP_HEIGHT, PowerManager.STEP, on ? 1.1 : 0, AttributeModifier.Operation.ADD_VALUE);
		Supe.sound(player.level(), player.position(), on ? SoundEvents.TRIDENT_RIPTIDE_3 : SoundEvents.TRIDENT_RIPTIDE_1, 1.0f, on ? 1.4f : 0.8f);
		player.sendSystemMessage(Component.translatable(on ? "message.theboys.speed_on" : "message.theboys.speed_off").withStyle(ChatFormatting.AQUA), true);
	}

	/** Anyone he runs into explodes. */
	private static void runThrough(ServerPlayer player, PlayerSession s, ServerLevel level) {
		Iterator<Map.Entry<Integer, Integer>> it = s.runThroughHit.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Integer> e = it.next();
			if (e.getValue() <= player.tickCount) it.remove();
		}
		double speed = s.horizontalSpeed();
		if (speed < 0.7) return;
		Vec3 now = player.position();
		Vec3 before = now.subtract(s.motion);
		AABB sweep = player.getBoundingBox().expandTowards(s.motion.scale(-1)).inflate(0.4);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e != player && e.isAlive() && !e.isSpectator())) {
			if (s.runThroughHit.containsKey(target.getId())) continue;
			s.runThroughHit.put(target.getId(), player.tickCount + 20);
			float damage = (float) (25 + speed * 20);
			Supe.burst(player, target, damage);
			Supe.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.5f, 0.6f);
		}
		if (before.distanceToSqr(now) > 0) {
			// keep momentum, he does not stop for anyone
		}
	}

	/** Runs so fast that time flows backwards for everyone around him. */
	private static void timeJump(ServerPlayer player, PlayerSession s) {
		if (!s.ready(1)) {
			PowerManager.notReady(player, s, 1);
			return;
		}
		if (!TimeRewind.start(player, TIME_JUMP_TICKS)) {
			player.sendSystemMessage(Component.translatable("message.theboys.rewind_busy").withStyle(ChatFormatting.RED), true);
			return;
		}
		s.cool(1, 2400);
		s.rewindTicks = TIME_JUMP_TICKS;
		if (!s.speed) setSpeed(player, s, true);
		PowerManager.modifier(player, Attributes.MOVEMENT_SPEED, PowerManager.RUN, TIME_JUMP_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Vec3 p = player.position();
		ModNetworking.sendFx(player.level(), p, new FxPayload(FxPayload.REWIND_START, player.getId(), (float) p.x, (float) p.y, (float) p.z, TIME_JUMP_TICKS, 0, 0));
		Supe.sound(player.level(), p, SoundEvents.BEACON_ACTIVATE, 2.0f, 0.5f);
		Supe.sound(player.level(), p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 1.8f);
	}

	private static void blitz(ServerPlayer player, PlayerSession s) {
		if (!s.ready(2)) {
			PowerManager.notReady(player, s, 2);
			return;
		}
		s.cool(2, 140);
		s.dashTicks = 7;
		s.strain += 6;
		Supe.sound(player.level(), player.position(), SoundEvents.BREEZE_JUMP, 1.4f, 1.6f);
	}

	private static void barrage(ServerPlayer player, PlayerSession s) {
		if (!s.ready(3)) {
			PowerManager.notReady(player, s, 3);
			return;
		}
		Supe.Ray ray = Supe.ray(player, player.getEyePosition(), 5);
		if (!(ray.entity() instanceof LivingEntity target)) {
			player.sendSystemMessage(Component.translatable("message.theboys.no_target").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		s.cool(3, 200);
		s.barrageTicks = 14;
		s.barrageTarget = target.getId();
	}

	private static void heartAttack(ServerPlayer player, PlayerSession s) {
		s.strain = 55;
		if (s.speed) setSpeed(player, s, false);
		s.dashTicks = 0;
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 3));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0));
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
		player.hurtServer(player.level(), player.level().damageSources().magic(), 8);
		Supe.sound(player.level(), player.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 0.5f);
		player.sendSystemMessage(Component.translatable("message.theboys.heart_attack").withStyle(ChatFormatting.DARK_RED));
	}

	static void stop(ServerPlayer player, PlayerSession s) {
		if (s.speed || s.rewindTicks > 0) {
			s.rewindTicks = 0;
			s.speed = false;
		}
		PowerManager.modifier(player, Attributes.MOVEMENT_SPEED, PowerManager.RUN, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerManager.modifier(player, Attributes.STEP_HEIGHT, PowerManager.STEP, 0, AttributeModifier.Operation.ADD_VALUE);
		s.dashTicks = 0;
		s.barrageTicks = 0;
	}
}
