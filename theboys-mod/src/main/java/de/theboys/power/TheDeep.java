package de.theboys.power;

import java.util.Iterator;
import java.util.Map;

import de.theboys.TheBoys;
import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.Vec3;

/**
 * The Deep: king of the sea. Breathes and sees under water, swims like a torpedo and is much stronger
 * there. R sends dolphins at his target, G sonar shows every living thing around, C a tidal wave,
 * X a torpedo dash. Out of the water he slowly dries out and gets weak.
 */
public final class TheDeep {
	public static final int MAX_MOISTURE = 1000;
	public static final Identifier WATER_DAMAGE = TheBoys.id("the_deep_water_damage");
	private static final int WAVE_TICKS = 16;

	private TheDeep() {
	}

	static void key(ServerPlayer player, PlayerSession s, int slot, boolean pressed) {
		if (!pressed) return;
		if (!s.ready(slot)) {
			PowerManager.notReady(player, s, slot);
			return;
		}
		switch (slot) {
			case 0 -> dolphins(player, s);
			case 1 -> sonar(player, s);
			case 2 -> wave(player, s);
			case 3 -> dash(player, s);
			default -> { }
		}
	}

	static int tick(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		ActiveState state = PowerAttachments.active(player);
		boolean water = player.isInWater();
		boolean wet = water || level.isRainingAt(player.blockPosition()) || level.isRainingAt(player.blockPosition().above());

		// gills: breathes, sees and swims under water like a fish
		if (water) {
			effect(player, MobEffects.CONDUIT_POWER, 260);
			effect(player, MobEffects.DOLPHINS_GRACE, 60);
			effect(player, MobEffects.NIGHT_VISION, 260);
		}
		PowerManager.modifier(player, Attributes.ATTACK_DAMAGE, WATER_DAMAGE, water ? 7 : 0, AttributeModifier.Operation.ADD_VALUE);
		// deep sea cold does nothing to him
		player.setTicksFrozen(0);

		// drying out on land
		if (wet) {
			if (s.moisture <= 0) player.sendSystemMessage(Component.translatable("message.theboys.deep_wet").withStyle(ChatFormatting.AQUA), true);
			s.moisture = Math.min(MAX_MOISTURE, s.moisture + 25);
		} else if (!player.isCreative()) {
			int before = s.moisture;
			s.moisture = Math.max(0, s.moisture - 1);
			if (before > 0 && s.moisture == 0) {
				player.sendSystemMessage(Component.translatable("message.theboys.deep_dry").withStyle(ChatFormatting.GOLD), true);
			}
		}
		if (s.moisture == 0) {
			effect(player, MobEffects.WEAKNESS, 40);
			effect(player, MobEffects.SLOWNESS, 40);
		}

		tickDolphins(player, s, level);
		if (s.waveTicks > 0) tickWave(player, s, level);
		if (s.deepDashTicks > 0) tickDash(player, s, level);
		if (s.sonarTicks > 0) s.sonarTicks--;

		state = state.with(ActiveState.SONAR, s.sonarTicks > 0).with(ActiveState.DASH, s.deepDashTicks > 0);
		PowerAttachments.setActive(player, state);
		return s.moisture;
	}

	private static void effect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks) {
		MobEffectInstance cur = player.getEffect(effect);
		if (cur == null || cur.getDuration() < ticks - 40) {
			player.addEffect(new MobEffectInstance(effect, ticks, 0, true, false, true));
		}
	}

	// ------------------------------------------------------------------ dolphins

	/** Calls three dolphins that shoot at his target one after the other. */
	private static void dolphins(ServerPlayer player, PlayerSession s) {
		Entity target = Supe.ray(player, player.getEyePosition(), 48, 1.2f).entity();
		if (target == null) {
			// nobody aimed at: the closest monster in front of him
			Vec3 look = player.getLookAngle();
			double best = Double.MAX_VALUE;
			for (LivingEntity e : Supe.livingAround(player.level(), player.position(), 24, player)) {
				if (e.getType().getCategory() != net.minecraft.world.entity.MobCategory.MONSTER) continue;
				Vec3 to = e.position().subtract(player.position());
				if (to.normalize().dot(look) < 0.3) continue;
				if (to.lengthSqr() < best) {
					best = to.lengthSqr();
					target = e;
				}
			}
		}
		if (target == null) {
			player.sendSystemMessage(Component.translatable("message.theboys.no_target").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		s.cool(0, 300);
		s.deepTarget = target.getId();
		s.dolphinsToSpawn = 3;
		s.dolphinDelay = 0;
		Supe.sound(player.level(), player.position(), SoundEvents.DOLPHIN_AMBIENT_WATER, 2.0f, 0.8f);
		Supe.sound(player.level(), player.position(), SoundEvents.DOLPHIN_PLAY, 2.0f, 1.2f);
	}

	private static void tickDolphins(ServerPlayer player, PlayerSession s, ServerLevel level) {
		Entity target = s.deepTarget >= 0 ? level.getEntity(s.deepTarget) : null;
		if (s.dolphinsToSpawn > 0 && --s.dolphinDelay <= 0) {
			s.dolphinsToSpawn--;
			s.dolphinDelay = 6;
			Mob d = EntityTypes.DOLPHIN.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (d != null) {
				int side = s.dolphinsToSpawn - 1;
				Vec3 right = player.getLookAngle().cross(new Vec3(0, 1, 0));
				right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
				Vec3 at = player.position().add(right.scale(side * 1.4)).add(0, 1.2, 0).add(player.getLookAngle().scale(0.8));
				d.setPos(at);
				d.setYRot(player.getYRot());
				d.setNoAi(true);
				level.addFreshEntity(d);
				s.dolphins.put(d.getId(), 0);
				level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.2);
				level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 20, 0.4, 0.4, 0.4, 0.1);
				Supe.sound(level, at, SoundEvents.DOLPHIN_JUMP, 1.5f, 1.0f);
			}
		}
		Iterator<Map.Entry<Integer, Integer>> it = s.dolphins.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Integer> en = it.next();
			Entity d = level.getEntity(en.getKey());
			int age = en.getValue() + 1;
			en.setValue(age);
			if (d == null) {
				it.remove();
				continue;
			}
			if (age >= 1000) {
				// it hit: swims (or flops) around for a bit, then dives back to wherever it came from
				if (age > 1100) {
					level.sendParticles(ParticleTypes.SPLASH, d.getX(), d.getY() + 0.4, d.getZ(), 20, 0.4, 0.3, 0.4, 0.1);
					d.discard();
					it.remove();
				}
				continue;
			}
			if (target == null || !target.isAlive() || age > 80) {
				release(d, en);
				continue;
			}
			Vec3 to = Supe.chest(target).subtract(d.position().add(0, 0.3, 0));
			double dist = to.length();
			Vec3 v = d.getDeltaMovement().scale(0.6).add(to.normalize().scale(d.isInWater() ? 0.75 : 0.55));
			if (v.length() > 1.3) v = v.normalize().scale(1.3);
			d.setDeltaMovement(v);
			d.move(MoverType.SELF, v);
			float yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
			d.setYRot(yaw);
			d.setYHeadRot(yaw);
			d.setXRot((float) -Math.toDegrees(Math.atan2(v.y, v.horizontalDistance())));
			if (age % 2 == 0) level.sendParticles(d.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.SPLASH, d.getX(), d.getY() + 0.3, d.getZ(), 4, 0.15, 0.15, 0.15, 0.02);
			if (dist < 1.4 + target.getBbWidth() * 0.5) {
				Supe.hurt(player, target, player.isInWater() || target.isInWater() ? 12f : 8f);
				Supe.push(target, v.normalize().scale(1.1).add(0, 0.45, 0));
				Vec3 at = Supe.chest(target);
				level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 40, 0.5, 0.5, 0.5, 0.3);
				Supe.sound(level, at, SoundEvents.DOLPHIN_ATTACK, 1.8f, 1.0f);
				Supe.sound(level, at, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.8f);
				if (target instanceof LivingEntity l && l.isDeadOrDying()) Supe.blood(level, at, 2f);
				release(d, en);
			}
		}
		if (s.dolphinsToSpawn == 0 && s.dolphins.isEmpty()) s.deepTarget = -1;
	}

	private static void release(Entity d, Map.Entry<Integer, Integer> en) {
		en.setValue(1000);
		if (d instanceof Mob m) m.setNoAi(false);
	}

	// ------------------------------------------------------------------ sonar

	/** Echolocation: a ping that makes every living thing within 48 blocks glow through walls. */
	private static void sonar(ServerPlayer player, PlayerSession s) {
		s.cool(1, 360);
		s.sonarTicks = 24;
		ServerLevel level = player.level();
		double radius = 48;
		int found = 0;
		for (LivingEntity e : Supe.livingAround(level, player.position(), radius, player)) {
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240, 0, false, false), player);
			found++;
		}
		Vec3 c = player.getEyePosition();
		ModNetworking.sendFx(level, c, new FxPayload(FxPayload.SONAR, player.getId(), (float) c.x, (float) c.y, (float) c.z, (float) radius, 0, 0));
		Supe.sound(level, c, SoundEvents.DOLPHIN_AMBIENT_WATER, 2.0f, 0.5f);
		Supe.sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.6f);
		Supe.sound(level, c, SoundEvents.WARDEN_SONIC_CHARGE, 0.5f, 2.0f);
		player.sendSystemMessage(Component.translatable("message.theboys.deep_sonar", found).withStyle(ChatFormatting.AQUA), true);
	}

	// ------------------------------------------------------------------ tidal wave

	private static void wave(ServerPlayer player, PlayerSession s) {
		s.cool(2, 280);
		s.waveTicks = WAVE_TICKS;
		s.waveYaw = player.getYRot();
		s.waveOrigin = player.position();
		s.waveStrong = player.isInWater();
		s.waveHit.clear();
		ServerLevel level = player.level();
		ModNetworking.sendFx(level, s.waveOrigin, new FxPayload(FxPayload.WAVE, player.getId(), (float) s.waveOrigin.x, (float) s.waveOrigin.y,
				(float) s.waveOrigin.z, s.waveYaw, WAVE_TICKS, 0));
		Supe.sound(level, s.waveOrigin, SoundEvents.GENERIC_SPLASH, 2.0f, 0.5f);
		Supe.sound(level, s.waveOrigin, SoundEvents.ELDER_GUARDIAN_CURSE, 0.5f, 1.6f);
	}

	private static void tickWave(ServerPlayer player, PlayerSession s, ServerLevel level) {
		int t = WAVE_TICKS - s.waveTicks--;
		double front = 1 + t * 1.0;
		Vec3 fwd = MiniMausMath.forward(s.waveYaw);
		Vec3 right = MiniMausMath.right(s.waveYaw);
		double half = 2.5 + t * 0.15;
		Vec3 crest = s.waveOrigin.add(fwd.scale(front));
		for (int i = -3; i <= 3; i++) {
			Vec3 p = crest.add(right.scale(i * half / 3));
			level.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 1.2, p.z, 6, 0.3, 0.8, 0.3, 0.2);
			level.sendParticles(ParticleTypes.FALLING_WATER, p.x, p.y + 2.2, p.z, 3, 0.4, 0.3, 0.4, 0);
			// puts out fires on the way
			BlockPos b = BlockPos.containing(p.x, p.y + 0.3, p.z);
			for (BlockPos q : new BlockPos[] {b, b.above()}) {
				if (level.getBlockState(q).getBlock() instanceof BaseFireBlock) level.removeBlock(q, false);
			}
		}
		for (LivingEntity e : Supe.livingAround(level, s.waveOrigin, front + 1, player)) {
			if (s.waveHit.contains(e.getId())) continue;
			Vec3 to = e.position().subtract(s.waveOrigin);
			double along = to.x * fwd.x + to.z * fwd.z;
			double side = Math.abs(to.x * right.x + to.z * right.z);
			if (along < 0 || along > front || side > half || Math.abs(to.y) > 4) continue;
			s.waveHit.add(e.getId());
			Supe.hurt(player, e, s.waveStrong ? 11f : 7f);
			e.clearFire();
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), player);
			Supe.push(e, fwd.scale(s.waveStrong ? 2.2 : 1.6).add(0, 0.55, 0));
			Supe.sound(level, e.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.2f, 1.0f);
		}
		if (t % 4 == 0) Supe.sound(level, crest, SoundEvents.GENERIC_SPLASH, 1.5f, 0.6f + t * 0.03f);
	}

	// ------------------------------------------------------------------ torpedo dash

	private static void dash(ServerPlayer player, PlayerSession s) {
		s.cool(3, 120);
		s.deepDashTicks = 12;
		s.deepDash = player.getLookAngle();
		s.dashHit.clear();
		Supe.sound(player.level(), player.position(), SoundEvents.TRIDENT_RIPTIDE_3, 1.5f, 1.0f);
	}

	private static void tickDash(ServerPlayer player, PlayerSession s, ServerLevel level) {
		s.deepDashTicks--;
		boolean water = player.isInWater();
		Vec3 v = s.deepDash.scale(water ? 1.9 : 1.1);
		if (!water && v.y < 0.1) v = new Vec3(v.x, Math.max(v.y, 0.05), v.z);
		Supe.setVelocity(player, v);
		player.resetFallDistance();
		level.sendParticles(water ? ParticleTypes.BUBBLE : ParticleTypes.SPLASH, player.getX(), player.getY() + 0.6, player.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().expandTowards(v).inflate(0.8),
				e -> e != player && e.isAlive() && !e.isSpectator())) {
			if (!s.dashHit.add(e.getId())) continue;
			Supe.hurt(player, e, water ? 14f : 9f);
			Supe.push(e, s.deepDash.scale(1.6).add(0, 0.4, 0));
			Supe.sound(level, e.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.8f);
			if (e.isDeadOrDying()) Supe.blood(level, Supe.chest(e), 2f);
		}
	}

	static void stop(ServerPlayer player, PlayerSession s) {
		PowerManager.modifier(player, Attributes.ATTACK_DAMAGE, WATER_DAMAGE, 0, AttributeModifier.Operation.ADD_VALUE);
		ServerLevel level = player.level();
		for (Integer id : s.dolphins.keySet()) {
			Entity d = level.getEntity(id);
			if (d != null) d.discard();
		}
		s.dolphins.clear();
		s.dolphinsToSpawn = 0;
		s.waveTicks = 0;
		s.deepDashTicks = 0;
	}
}
