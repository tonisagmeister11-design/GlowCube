package de.theboys.power;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Stormfront (V-One only): lightning out of her hands that jumps from victim to victim, fast flight,
 * real lightning called down from the sky and an electric nova that stuns everyone around her.
 * Lightning cannot hurt her; fire is her weakness.
 */
public final class Stormfront {
	public static final int MAX_HEAT = 160;
	private static boolean reentrant;

	private Stormfront() {
	}

	static void key(ServerPlayer player, PlayerSession s, int slot, boolean pressed) {
		if (!pressed) return;
		switch (slot) {
			case 0 -> {
				if (!s.ready(0)) PowerManager.notReady(player, s, 0);
				else Supe.sound(player.level(), player.getEyePosition(), SoundEvents.TRIDENT_THUNDER, 0.6f, 1.8f);
			}
			case 1 -> {
				Homelander.toggleFlight(player, s);
				if (s.flying) {
					// she is faster in the air than anyone
					player.getAbilities().setFlyingSpeed(0.17f);
					player.onUpdateAbilities();
					Supe.sound(player.level(), player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.9f);
				}
			}
			case 2 -> callStorm(player, s);
			case 3 -> nova(player, s);
			default -> { }
		}
	}

	static int tick(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		ActiveState state = PowerAttachments.active(player);

		boolean firing = s.keyDown[0] && s.ready(0) && s.heat < MAX_HEAT;
		int target = -1;
		if (firing) {
			s.heat++;
			target = stream(player, level);
			if (s.heat >= MAX_HEAT) {
				s.cool(0, 70);
				Supe.sound(level, player.getEyePosition(), SoundEvents.BEACON_DEACTIVATE, 1.0f, 1.4f);
			}
		} else {
			s.heat = Math.max(0, s.heat - 2);
		}
		state = state.with(ActiveState.HAND_BEAM, firing).withTarget(target);

		// lightning strikes called down from the sky, one after the other
		if (s.strikeBolts > 0 && s.strikeAt != null && --s.strikeDelay <= 0) {
			s.strikeBolts--;
			s.strikeDelay = 5;
			strike(player, level, s.strikeAt, s.strikeBolts);
		}

		if (s.flying) {
			player.resetFallDistance();
			if (player.getAbilities().flying && player.tickCount % 7 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 6, 0.4, 0.6, 0.4, 0.15);
			}
		}
		state = state.with(ActiveState.FLYING, s.flying && player.getAbilities().flying);
		PowerAttachments.setActive(player, state);
		return s.heat * 1000 / MAX_HEAT;
	}

	/** The lightning stream: hits what she aims at and jumps on to up to three more. Returns the main target id. */
	private static int stream(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Supe.Ray ray = Supe.ray(player, eye, 32, 0.6f);
		if (player.tickCount % 4 == 0) Supe.sound(level, eye, SoundEvents.BEE_LOOP_AGGRESSIVE, 1.0f, 1.9f);
		Vec3 end = ray.end();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 6, 0.2, 0.2, 0.2, 0.3);
		if (ray.entity() == null) {
			BlockPos b = ray.block();
			if (b != null && player.tickCount % 10 == 0 && level.getRandom().nextFloat() < 0.25f && level.getBlockState(b.above()).isAir()) {
				level.setBlock(b.above(), BaseFireBlock.getState(level, b.above()), 11);
			}
			return -1;
		}
		if (player.tickCount % 2 != 0) return ray.entity().getId();
		Supe.hurt(player, ray.entity(), 4.5f);
		if (ray.entity() instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, 3), player);
			if (living.isDeadOrDying()) Supe.blood(level, end, 1.5f);
		}
		// chain lightning
		List<LivingEntity> chain = new ArrayList<>();
		Vec3 from = Supe.chest(ray.entity());
		List<LivingEntity> near = Supe.livingAround(level, from, 6, player);
		near.removeIf(e -> e == ray.entity());
		near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(ray.entity())));
		for (LivingEntity e : near) {
			if (chain.size() >= 3) break;
			chain.add(e);
		}
		for (LivingEntity e : chain) {
			Vec3 to = Supe.chest(e);
			Supe.hurt(player, e, 3f);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, 2), player);
			ModNetworking.sendFx(level, from, new FxPayload(FxPayload.BOLT, player.getId(), (float) from.x, (float) from.y, (float) from.z,
					(float) to.x, (float) to.y, (float) to.z));
			from = to;
		}
		return ray.entity().getId();
	}

	/** Calls five bolts down onto the spot she points at (it follows the target if she aimed at someone). */
	private static void callStorm(ServerPlayer player, PlayerSession s) {
		if (!s.ready(2)) {
			PowerManager.notReady(player, s, 2);
			return;
		}
		s.cool(2, 260);
		Supe.Ray ray = Supe.ray(player, player.getEyePosition(), 80, 1.0f);
		s.strikeAt = ray.end();
		s.strikeTarget = ray.entity() != null ? ray.entity().getId() : -1;
		s.strikeBolts = 5;
		s.strikeDelay = 4;
		ServerLevel level = player.level();
		Supe.sound(level, player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, 0.6f);
		// her arm goes up, the sky answers
		Vec3 hand = player.getEyePosition().add(0, 0.6, 0);
		ModNetworking.sendFx(level, hand, new FxPayload(FxPayload.BOLT, player.getId(), (float) hand.x, (float) hand.y, (float) hand.z,
				(float) hand.x, (float) hand.y + 30, (float) hand.z));
	}

	private static void strike(ServerPlayer player, ServerLevel level, Vec3 at, int left) {
		PlayerSession s = PowerManager.session(player);
		if (s.strikeTarget >= 0) {
			var e = level.getEntity(s.strikeTarget);
			if (e != null && e.isAlive()) at = e.position();
		}
		var rnd = level.getRandom();
		Vec3 spot = left == 4 ? at : at.add((rnd.nextDouble() - 0.5) * 5, 0, (rnd.nextDouble() - 0.5) * 5);
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setPos(spot);
			bolt.setCause(player);
			level.addFreshEntity(bolt);
		}
		for (LivingEntity e : Supe.livingAround(level, spot, 3.2, player)) {
			Supe.hurt(player, e, 9f);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3), player);
			Supe.push(e, e.position().subtract(spot).normalize().scale(0.6).add(0, 0.4, 0));
		}
		ModNetworking.sendFx(level, spot, new FxPayload(FxPayload.SHOCKWAVE, player.getId(), (float) spot.x, (float) spot.y + 0.2f, (float) spot.z, 4f, Float.NaN, 0));
	}

	/** Electric nova: arcs to everyone around her, stunned and thrown back. */
	private static void nova(ServerPlayer player, PlayerSession s) {
		if (!s.ready(3)) {
			PowerManager.notReady(player, s, 3);
			return;
		}
		s.cool(3, 400);
		ServerLevel level = player.level();
		Vec3 c = Supe.chest(player);
		double radius = 10;
		for (LivingEntity e : Supe.livingAround(level, c, radius, player)) {
			float k = (float) (1 - e.distanceTo(player) / (radius + 1));
			Supe.hurt(player, e, 6f + 10f * k);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 70, 5), player);
			e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 2), player);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1), player);
			Supe.push(e, e.position().subtract(player.position()).normalize().scale(0.8 + 1.2 * k).add(0, 0.45, 0));
			Vec3 to = Supe.chest(e);
			ModNetworking.sendFx(level, c, new FxPayload(FxPayload.BOLT, player.getId(), (float) c.x, (float) c.y, (float) c.z,
					(float) to.x, (float) to.y, (float) to.z));
		}
		ModNetworking.sendFx(level, c, new FxPayload(FxPayload.NOVA, player.getId(), (float) c.x, (float) c.y, (float) c.z, (float) radius, 0x8FC8FF, 1));
		ModNetworking.sendFx(level, c, new FxPayload(FxPayload.SHAKE, player.getId(), (float) c.x, (float) c.y, (float) c.z, 1.5f, 0, 0));
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 120, radius * 0.35, 1.2, radius * 0.35, 0.8);
		Supe.sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0f, 1.2f);
		Supe.sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 1.6f);
	}

	/**
	 * Fire is her weakness (Ryan's heat vision): fire hurts her twice as much. Returns false when the
	 * original hit was replaced by the stronger one.
	 */
	public static boolean fireHit(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (reentrant) return true;
		reentrant = true;
		try {
			player.hurtServer(player.level(), source, amount * 2f);
		} finally {
			reentrant = false;
		}
		return false;
	}

	static void stop(ServerPlayer player, PlayerSession s) {
		if (s.flying) {
			s.flying = false;
			Homelander.endFlight(player);
			player.onUpdateAbilities();
		}
		s.strikeBolts = 0;
		s.heat = 0;
	}
}
