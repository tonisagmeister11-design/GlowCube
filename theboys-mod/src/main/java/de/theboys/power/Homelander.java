package de.theboys.power;

import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Homelander: heat vision (hold), flight with sonic booms, X-ray vision and a thunder clap.
 * Passive: huge strength and toughness, no fall damage, immune to fire.
 */
public final class Homelander {
	public static final int MAX_HEAT = 140;

	private Homelander() {
	}

	static void key(ServerPlayer player, PlayerSession s, int slot, boolean pressed) {
		if (!pressed) return;
		switch (slot) {
			case 0 -> {
				if (!s.ready(0)) PowerManager.notReady(player, s, 0);
				else Supe.sound(player.level(), player.getEyePosition(), SoundEvents.GUARDIAN_ATTACK, 1.0f, 1.8f);
			}
			case 1 -> toggleFlight(player, s);
			case 2 -> {
				if (!s.ready(2)) {
					PowerManager.notReady(player, s, 2);
					return;
				}
				s.xrayTicks = 240;
				s.cool(2, 240 + 400);
				Supe.sound(player.level(), player.getEyePosition(), SoundEvents.BEACON_ACTIVATE, 0.8f, 2.0f);
			}
			case 3 -> clap(player, s);
			default -> { }
		}
	}

	static int tick(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		ActiveState state = PowerAttachments.active(player);

		// heat vision
		boolean firing = s.keyDown[0] && s.ready(0) && s.heat < MAX_HEAT;
		if (firing) {
			s.heat += 1;
			laser(player, s, level);
			if (s.heat >= MAX_HEAT) {
				s.cool(0, 80);
				Supe.sound(level, player.getEyePosition(), SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.8f);
			}
		} else {
			s.heat = Math.max(0, s.heat - 2);
			if (s.burnPos != null) {
				level.destroyBlockProgress(player.getId(), s.burnPos, -1);
				s.burnPos = null;
			}
		}
		state = state.with(ActiveState.LASER, firing);

		// x-ray
		if (s.xrayTicks > 0) s.xrayTicks--;
		state = state.with(ActiveState.XRAY, s.xrayTicks > 0);

		// flight & sonic boom
		if (s.flying) {
			if (!player.getAbilities().flying && player.onGround()) {
				// player landed and stopped flying with double-jump: keep flight available
			}
			player.resetFallDistance();
			double speed = s.motion.length();
			if (player.getAbilities().flying && speed > 1.6 && s.boomCooldown <= 0) {
				s.boomCooldown = 160;
				Vec3 p = player.position();
				ModNetworking.sendFx(level, p, new FxPayload(FxPayload.SHOCKWAVE, player.getId(), (float) p.x, (float) p.y + 1, (float) p.z, 9f, player.getYRot(), player.getXRot()));
				Supe.sound(level, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.5f, 1.7f);
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 1, p.z, 1, 0, 0, 0, 0);
				for (LivingEntity e : Supe.livingAround(level, p, 6, player)) {
					Vec3 away = e.position().subtract(p).normalize();
					Supe.push(e, away.scale(1.6).add(0, 0.5, 0));
				}
			}
		}
		if (s.flying && player.getAbilities().flying && s.motion.length() > 0.75) {
			smashThrough(player, s, level);
		}
		state = state.with(ActiveState.FLYING, s.flying && player.getAbilities().flying);

		PowerAttachments.setActive(player, state);
		return s.heat * 1000 / MAX_HEAT;
	}

	private static void laser(ServerPlayer player, PlayerSession s, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Supe.Ray ray = Supe.ray(player, eye, 64);
		if (player.tickCount % 6 == 0) {
			Supe.sound(level, eye, SoundEvents.BEACON_AMBIENT, 0.6f, 2.0f);
		}
		Vec3 end = ray.end();
		level.sendParticles(new DustParticleOptions(0xFF2A1A, 1.2f), end.x, end.y, end.z, 4, 0.1, 0.1, 0.1, 0.02);
		level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 1, 0.05, 0.05, 0.05, 0.01);
		if (ray.entity() != null) {
			if (player.tickCount % 3 == 0) {
				Supe.hurt(player, ray.entity(), 6.0f);
				ray.entity().igniteForSeconds(4);
				if (ray.entity() instanceof LivingEntity living && living.isDeadOrDying()) {
					Supe.blood(level, end, 2.5f);
				}
			}
		} else if (ray.block() != null) {
			burn(player, s, level, ray.block());
		}
	}

	/**
	 * Flying at full speed he does not stop for walls: everything in his flight path is smashed
	 * (cleared a few blocks ahead, so the client never bumps into it) and whoever is in the way gets hit.
	 */
	private static void smashThrough(ServerPlayer player, PlayerSession s, ServerLevel level) {
		Vec3 dir = s.motion.normalize();
		Vec3 from = player.position().add(0, 0.9, 0);
		java.util.Set<BlockPos> done = new java.util.HashSet<>();
		int broken = 0;
		for (double d = 0; d <= 6 && broken < 48; d += 0.5) {
			Vec3 c = from.add(dir.scale(d));
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(c.x - 0.9, c.y - 0.85, c.z - 0.9), BlockPos.containing(c.x + 0.9, c.y + 1.2, c.z + 0.9))) {
				if (broken >= 48) break;
				BlockPos im = p.immutable();
				if (!done.add(im) || !Supe.breakable(level, im, 60)) continue;
				level.destroyBlock(im, false, player, 512);
				broken++;
			}
		}
		if (broken > 0 && player.tickCount % 2 == 0) {
			Supe.sound(level, from, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.4f);
			level.sendParticles(ParticleTypes.EXPLOSION, from.x + dir.x * 2, from.y, from.z + dir.z * 2, 1, 0.2, 0.2, 0.2, 0);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().expandTowards(s.motion.scale(2)).inflate(0.6),
				e -> e != player && e.isAlive() && !e.isSpectator())) {
			Supe.hurt(player, e, (float) (22 + s.motion.length() * 12));
			Supe.push(e, dir.scale(2.5).add(0, 0.4, 0));
			Supe.blood(level, e.position().add(0, e.getBbHeight() / 2, 0), e.isDeadOrDying() ? 3f : 1f);
		}
	}

	/** Heat vision slowly melts through the block it is aimed at and sets things on fire. */
	private static void burn(ServerPlayer player, PlayerSession s, ServerLevel level, BlockPos pos) {
		if (!pos.equals(s.burnPos)) {
			if (s.burnPos != null) level.destroyBlockProgress(player.getId(), s.burnPos, -1);
			s.burnPos = pos;
			s.burnProgress = 0;
		}
		if (!Supe.breakable(level, pos, 30)) return;
		float hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
		int needed = Math.max(3, (int) (hardness * 5));
		s.burnProgress++;
		level.destroyBlockProgress(player.getId(), pos, Math.min(9, s.burnProgress * 10 / needed));
		if (s.burnProgress >= needed) {
			level.destroyBlockProgress(player.getId(), pos, -1);
			level.destroyBlock(pos, true, player, 512);
			s.burnPos = null;
			BlockPos below = pos.below();
			if (level.getRandom().nextFloat() < 0.35f && level.getBlockState(pos).isAir() && !level.getBlockState(below).isAir()) {
				level.setBlock(pos, BaseFireBlock.getState(level, pos), 11, 512);
			}
		}
	}

	private static void toggleFlight(ServerPlayer player, PlayerSession s) {
		s.flying = !s.flying;
		var abilities = player.getAbilities();
		if (s.flying) {
			abilities.mayfly = true;
			abilities.flying = true;
			abilities.setFlyingSpeed(0.14f);
			Vec3 p = player.position();
			ModNetworking.sendFx(player.level(), p, new FxPayload(FxPayload.SHOCKWAVE, player.getId(), (float) p.x, (float) p.y + 0.1f, (float) p.z, 4f, Float.NaN, Float.NaN));
			Supe.sound(player.level(), p, SoundEvents.ENDER_DRAGON_FLAP, 1.0f, 1.3f);
			Supe.push(player, new Vec3(0, 0.9, 0));
		} else {
			endFlight(player);
		}
		player.onUpdateAbilities();
	}

	private static void endFlight(ServerPlayer player) {
		var abilities = player.getAbilities();
		abilities.setFlyingSpeed(0.05f);
		if (!player.isCreative() && !player.isSpectator()) {
			abilities.mayfly = false;
			abilities.flying = false;
		}
	}

	/** Thunder clap: a cone shockwave that throws everything in front of him away. */
	private static void clap(ServerPlayer player, PlayerSession s) {
		if (!s.ready(3)) {
			PowerManager.notReady(player, s, 3);
			return;
		}
		s.cool(3, 180);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		ModNetworking.sendFx(level, eye, new FxPayload(FxPayload.SHOCKWAVE, player.getId(), (float) eye.x, (float) eye.y - 0.3f, (float) eye.z, 14f, player.getYRot(), player.getXRot()));
		ModNetworking.sendFx(level, eye, new FxPayload(FxPayload.SHAKE, player.getId(), (float) eye.x, (float) eye.y, (float) eye.z, 1.5f, 0, 0));
		Supe.sound(level, eye, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0f, 0.7f);
		Supe.sound(level, eye, SoundEvents.PLAYER_ATTACK_STRONG, 2.0f, 0.5f);
		for (LivingEntity e : Supe.livingAround(level, eye, 14, player)) {
			Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
			double dist = to.length();
			if (dist < 0.01 || to.normalize().dot(look) < 0.55) continue;
			double power = 1.0 - dist / 14.0;
			Supe.hurt(player, e, (float) (8 + 12 * power));
			Supe.push(e, look.scale(2.5 * power + 0.8).add(0, 0.6, 0));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
			e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
		}
	}

	static void stop(ServerPlayer player, PlayerSession s) {
		if (s.flying) {
			s.flying = false;
			endFlight(player);
			player.onUpdateAbilities();
		}
		if (s.burnPos != null) {
			player.level().destroyBlockProgress(player.getId(), s.burnPos, -1);
			s.burnPos = null;
		}
		s.xrayTicks = 0;
	}
}
