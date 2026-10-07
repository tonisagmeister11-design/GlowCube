package de.theboys.power;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.theboys.TheBoys;
import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Billy Butcher (season 5) and his "Super Cancer": the parasitic tendrils growing out of his chest.
 * Tendril lash (smashes blocks and fells whole trees), grab and throw, rip apart in mid-air,
 * Cancer Walk (the tendrils carry him like legs; sneak + key = tendril grapple), and when he is
 * close to death the tendrils burst out on their own and tear every threat around him to pieces.
 */
public final class Butcher {
	private static final int RIP_TICKS = 40;
	private static final double HOLD_DISTANCE = 3.4;
	/** Health (half hearts) at which the Super Cancer takes over. */
	private static final float FRENZY_HEALTH = 7.0f;
	private static final int FRENZY_TICKS = 34;
	private static final Identifier WALK_SPEED = TheBoys.id("cancer_walk_speed");
	private static final Identifier WALK_JUMP = TheBoys.id("cancer_walk_jump");

	private Butcher() {
	}

	static void key(ServerPlayer player, PlayerSession s, int slot, boolean pressed) {
		if (!pressed) return;
		switch (slot) {
			case 0 -> lash(player, s);
			case 1 -> grabOrThrow(player, s);
			case 2 -> rip(player, s);
			case 3 -> {
				if (player.isShiftKeyDown()) grapple(player, s);
				else setCancerWalk(player, s, !s.cancerWalk);
			}
			default -> { }
		}
	}

	static int tick(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		ActiveState state = PowerAttachments.active(player);
		if (s.frenzyCooldown > 0) s.frenzyCooldown--;

		// lash impact arrives a few ticks after the tendril is launched
		if (s.strikeDelay > 0 && --s.strikeDelay == 0) {
			impact(player, level, s.strikePoint);
		}

		// holding someone in the air
		Entity held = s.heldId >= 0 ? level.getEntity(s.heldId) : null;
		if (held != null && (!held.isAlive() || held.distanceToSqr(player) > 400)) {
			held = null;
			release(s);
		}
		if (held != null) {
			s.holdTicks++;
			Vec3 eye = player.getEyePosition();
			Vec3 spot = eye.add(player.getLookAngle().scale(HOLD_DISTANCE)).subtract(0, held.getBbHeight() / 2, 0);
			if (s.ripTicks > 0) {
				s.ripTicks--;
				// lift higher and shake while the tendrils pull
				double lift = 1.4 * (1 - s.ripTicks / (double) RIP_TICKS);
				double shake = 0.08 + 0.18 * (1 - s.ripTicks / (double) RIP_TICKS);
				spot = spot.add((level.getRandom().nextDouble() - 0.5) * shake, lift, (level.getRandom().nextDouble() - 0.5) * shake);
				if (s.ripTicks % 8 == 0) {
					Supe.sound(level, spot, SoundEvents.SLIME_SQUISH, 1.2f, 0.5f);
					if (held instanceof LivingEntity living) Supe.hurt(player, living, 1.0f);
					Supe.blood(level, spot.add(0, held.getBbHeight() / 2, 0), 0.6f);
				}
				if (s.ripTicks == 0) {
					tearApart(player, held, player.getYRot() + 90f);
					release(s);
					held = null;
				}
			}
			if (held != null) {
				hold(held, spot);
				if (s.holdTicks > 400 && s.ripTicks == 0) release(s);
			}
		}

		// thrown entities take damage when they slam into something
		if (s.thrownId >= 0) {
			Entity thrown = level.getEntity(s.thrownId);
			if (thrown == null || --s.thrownTicks <= 0) {
				s.thrownId = -1;
			} else if (s.thrownTicks < 36 && (thrown.horizontalCollision || thrown.onGround())) {
				Supe.hurt(player, thrown, 14);
				Supe.sound(level, thrown.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.5f, 0.5f);
				Supe.blood(level, thrown.position().add(0, 1, 0), 1f);
				s.thrownId = -1;
			}
		}

		// tendril grapple pulls him to the anchor point
		if (s.grappleTicks > 0) {
			s.grappleTicks--;
			Vec3 to = s.grappleTarget.subtract(player.position());
			if (to.lengthSqr() < 4) {
				s.grappleTicks = 0;
			} else {
				player.setDeltaMovement(to.normalize().scale(Math.min(2.2, to.length() * 0.5)));
				player.needsSync = true;
			}
			player.resetFallDistance();
		}

		// Cancer Walk: the tendrils carry him and crush whatever is in the way
		if (s.cancerWalk) {
			player.resetFallDistance();
			if (s.horizontalSpeed() > 0.05) {
				crushAhead(player, level, s);
			}
		}

		// Super Cancer: close to death, the tendrils burst out and kill every threat
		if (!player.isCreative() && player.getHealth() <= FRENZY_HEALTH && s.frenzyCooldown == 0 && s.frenzyTicks == 0) {
			startFrenzy(player, level, s);
		}
		if (s.frenzyTicks > 0) {
			tickFrenzy(player, level, s);
		}

		state = state.with(ActiveState.HOLD, s.heldId >= 0)
				.with(ActiveState.RIP, s.ripTicks > 0)
				.with(ActiveState.GRAPPLE, s.grappleTicks > 0)
				.with(ActiveState.FRENZY, s.frenzyTicks > 0)
				.with(ActiveState.CANCER_WALK, s.cancerWalk)
				.withTarget(s.heldId)
				.withCharge(s.ripTicks);
		PowerAttachments.setActive(player, state);
		return s.ripTicks > 0 ? 1000 - s.ripTicks * 1000 / RIP_TICKS : 0;
	}

	private static void hold(Entity held, Vec3 spot) {
		held.setDeltaMovement(Vec3.ZERO);
		held.resetFallDistance();
		if (held instanceof ServerPlayer p) {
			p.teleportTo(p.level(), spot.x, spot.y, spot.z, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
		} else {
			held.snapTo(spot.x, spot.y, spot.z, held.getYRot(), held.getXRot());
		}
	}

	private static void release(PlayerSession s) {
		s.heldId = -1;
		s.holdTicks = 0;
		s.ripTicks = 0;
	}

	/** Tendril lash: a tendril shoots out and smashes whatever it hits. */
	private static void lash(ServerPlayer player, PlayerSession s) {
		if (!s.ready(0)) {
			PowerManager.notReady(player, s, 0);
			return;
		}
		s.cool(0, 22);
		Supe.Ray ray = Supe.ray(player, player.getEyePosition(), 20);
		Vec3 point = ray.end();
		Vec3 from = Supe.chest(player);
		ModNetworking.sendFx(player.level(), from, new FxPayload(FxPayload.TENDRIL_STRIKE, player.getId(), (float) point.x, (float) point.y, (float) point.z, 0, 0, 0));
		Supe.sound(player.level(), from, SoundEvents.SLIME_ATTACK, 1.5f, 0.5f);
		s.strikeDelay = 3;
		s.strikePoint = point;
	}

	private static void impact(ServerPlayer player, ServerLevel level, Vec3 point) {
		Supe.sound(level, point, SoundEvents.WITHER_BREAK_BLOCK, 0.8f, 1.2f);
		ModNetworking.sendFx(level, point, new FxPayload(FxPayload.SHAKE, player.getId(), (float) point.x, (float) point.y, (float) point.z, 0.6f, 0, 0));
		for (LivingEntity e : Supe.livingAround(level, point, 2.0, player)) {
			Supe.hurt(player, e, 16);
			Vec3 away = e.position().subtract(player.position()).normalize();
			Supe.push(e, away.scale(1.6).add(0, 0.5, 0));
			if (e.isDeadOrDying()) {
				tearApart(player, e, player.getYRot() + 90f);
			} else {
				Supe.blood(level, e.position().add(0, e.getBbHeight() / 2, 0), 1f);
			}
		}
		BlockPos center = BlockPos.containing(point);
		Set<BlockPos> logs = new HashSet<>();
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
			if (p.distToCenterSqr(point) > 2.6 || !Supe.breakable(level, p, 8)) continue;
			BlockState state = level.getBlockState(p);
			if (state.is(BlockTags.LOGS)) logs.add(p.immutable());
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.2);
			level.destroyBlock(p, true, player, 512);
		}
		// hitting a trunk fells the whole tree
		for (BlockPos log : logs) {
			fellTree(player, level, log);
		}
	}

	private static void fellTree(ServerPlayer player, ServerLevel level, BlockPos start) {
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		todo.add(start);
		int broken = 0;
		while (!todo.isEmpty() && broken < 160) {
			BlockPos pos = todo.poll();
			for (BlockPos n : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))) {
				BlockPos im = n.immutable();
				if (!seen.add(im)) continue;
				if (level.getBlockState(im).is(BlockTags.LOGS)) {
					level.destroyBlock(im, true, player, 512);
					broken++;
					todo.add(im);
				}
			}
		}
	}

	private static void grabOrThrow(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		if (s.heldId >= 0) {
			if (s.ripTicks > 0) return;
			Entity held = level.getEntity(s.heldId);
			release(s);
			if (held != null) {
				Vec3 v = player.getLookAngle().scale(3.0).add(0, 0.35, 0);
				held.setDeltaMovement(v);
				held.needsSync = true;
				s.thrownId = held.getId();
				s.thrownTicks = 40;
				Supe.sound(level, held.position(), SoundEvents.TRIDENT_THROW, 1.2f, 0.5f);
				s.cool(1, 30);
			}
			return;
		}
		if (!s.ready(1)) {
			PowerManager.notReady(player, s, 1);
			return;
		}
		Entity target = grabTarget(player);
		if (target == null) return;
		s.heldId = target.getId();
		s.holdTicks = 0;
		Supe.sound(level, target.position(), SoundEvents.SLIME_SQUISH, 1.5f, 0.6f);
	}

	private static Entity grabTarget(ServerPlayer player) {
		Supe.Ray ray = Supe.ray(player, player.getEyePosition(), 22);
		Entity target = ray.entity();
		if (target == null || target.isPassenger() && target.getVehicle() == player) {
			player.sendSystemMessage(Component.translatable("message.theboys.no_target").withStyle(ChatFormatting.GRAY), true);
			return null;
		}
		target.stopRiding();
		target.ejectPassengers();
		return target;
	}

	/** Rip apart: lift the victim, the tendrils pull, and it tears in two. */
	private static void rip(ServerPlayer player, PlayerSession s) {
		if (s.ripTicks > 0) return;
		if (!s.ready(2)) {
			PowerManager.notReady(player, s, 2);
			return;
		}
		Entity held = s.heldId >= 0 ? player.level().getEntity(s.heldId) : null;
		if (held == null) {
			held = grabTarget(player);
			if (held == null) return;
			s.heldId = held.getId();
			s.holdTicks = 0;
		}
		if (!(held instanceof LivingEntity)) {
			player.sendSystemMessage(Component.translatable("message.theboys.no_target").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		s.ripTicks = RIP_TICKS;
		s.cool(2, 300);
		Supe.sound(player.level(), held.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 1.5f);
	}

	/**
	 * Kills the victim and replaces the body with two torn halves (drawn client side) that fly apart
	 * while blood sprays out of them.
	 */
	public static void tearApart(ServerPlayer player, Entity victim, float axisYaw) {
		ServerLevel level = player.level();
		Vec3 at = victim.position();
		float w = victim.getBbWidth();
		float h = victim.getBbHeight();
		if (victim instanceof LivingEntity living) {
			Supe.burst(player, living, 10000);
			// the halves replace the body; mobs skip the usual death animation (their drops are already out)
			if (!(victim instanceof ServerPlayer) && living.isDeadOrDying()) {
				victim.discard();
			}
		} else {
			victim.discard();
		}
		Supe.blood(level, at.add(0, h / 2, 0), 4.5f);
		ModNetworking.sendFx(level, at, new FxPayload(FxPayload.TORN, player.getId(), (float) at.x, (float) at.y, (float) at.z, w, h, axisYaw));
		ModNetworking.sendFx(level, at, new FxPayload(FxPayload.SHAKE, player.getId(), (float) at.x, (float) at.y, (float) at.z, 1.2f, 0, 0));
		Supe.sound(level, at, SoundEvents.PLAYER_HURT, 1.5f, 0.5f);
		Supe.sound(level, at, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.8f, 1.6f);
	}

	// ------------------------------------------------------------------ Super Cancer (self defence)

	private static void startFrenzy(ServerPlayer player, ServerLevel level, PlayerSession s) {
		List<LivingEntity> threats = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(20),
				e -> e != player && e.isAlive() && !e.isSpectator())) {
			boolean hostile = e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == player
					|| e == player.getLastHurtByMob();
			if (hostile) threats.add(e);
		}
		threats.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
		if (threats.size() > 12) threats = threats.subList(0, 12);

		s.frenzyTicks = FRENZY_TICKS;
		s.frenzyCooldown = 900;
		s.frenzyVictims.clear();
		s.frenzySpots.clear();
		Vec3 from = Supe.chest(player);
		for (LivingEntity e : threats) {
			Vec3 spot = e.position().add(0, 1.2, 0);
			s.frenzyVictims.put(e.getId(), FRENZY_TICKS);
			s.frenzySpots.put(e.getId(), spot);
			Vec3 c = spot.add(0, e.getBbHeight() / 2, 0);
			ModNetworking.sendFx(level, from, new FxPayload(FxPayload.TENDRIL_STRIKE, player.getId(), (float) c.x, (float) c.y, (float) c.z, FRENZY_TICKS, 0, 0));
		}
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 3));
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
		Supe.sound(level, from, SoundEvents.WARDEN_ROAR, 1.5f, 1.3f);
		Supe.sound(level, from, SoundEvents.SLIME_ATTACK, 2.0f, 0.4f);
		Supe.blood(level, from, 1.5f);
		ModNetworking.sendFx(level, from, new FxPayload(FxPayload.SHAKE, player.getId(), (float) from.x, (float) from.y, (float) from.z, 2f, 0, 0));
		player.sendSystemMessage(Component.translatable("message.theboys.super_cancer").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
	}

	private static void tickFrenzy(ServerPlayer player, ServerLevel level, PlayerSession s) {
		s.frenzyTicks--;
		Iterator<Map.Entry<Integer, Integer>> it = s.frenzyVictims.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Integer> en = it.next();
			Entity e = level.getEntity(en.getKey());
			if (e == null || !e.isAlive()) {
				it.remove();
				continue;
			}
			// pinned in the air by the tendrils, shaken, then torn apart
			Vec3 spot = s.frenzySpots.get(en.getKey());
			double shake = 0.12;
			hold(e, spot.add((level.getRandom().nextDouble() - 0.5) * shake, 0, (level.getRandom().nextDouble() - 0.5) * shake));
			if (s.frenzyTicks % 10 == 0) {
				Supe.blood(level, spot.add(0, e.getBbHeight() / 2, 0), 0.5f);
			}
			if (s.frenzyTicks == 0 || s.frenzyTicks == 6 + (en.getKey() % 5) * 3) {
				Vec3 toVictim = e.position().subtract(player.position());
				float yaw = (float) Math.toDegrees(Math.atan2(-toVictim.x, toVictim.z)) + 90f;
				tearApart(player, e, yaw);
				it.remove();
			}
		}
		if (s.frenzyTicks == 0) {
			s.frenzyVictims.clear();
			s.frenzySpots.clear();
		}
	}

	// ------------------------------------------------------------------ Cancer Walk

	private static void setCancerWalk(ServerPlayer player, PlayerSession s, boolean on) {
		s.cancerWalk = on;
		var abilities = player.getAbilities();
		// server-side only: lets him hover on his tendrils without the "flying is not enabled" kick
		abilities.mayfly = on || player.isCreative() || player.isSpectator();
		PowerManager.modifier(player, Attributes.MOVEMENT_SPEED, WALK_SPEED, on ? 0.4 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerManager.modifier(player, Attributes.JUMP_STRENGTH, WALK_JUMP, on ? 0.5 : 0, AttributeModifier.Operation.ADD_VALUE);
		Supe.sound(player.level(), player.position(), on ? SoundEvents.SLIME_ATTACK : SoundEvents.SLIME_SQUISH, 1.4f, on ? 0.5f : 0.8f);
		if (on) Supe.blood(player.level(), Supe.chest(player), 0.8f);
		player.sendSystemMessage(Component.translatable(on ? "message.theboys.cancer_walk_on" : "message.theboys.cancer_walk_off").withStyle(ChatFormatting.DARK_RED), true);
	}

	/** Blocks in front of him (from the ground up to his head) are smashed by the walking tendrils. */
	private static void crushAhead(ServerPlayer player, ServerLevel level, PlayerSession s) {
		Vec3 dir = new Vec3(s.motion.x, 0, s.motion.z).normalize();
		Vec3 base = player.position().add(dir.scale(1.2));
		AABB box = new AABB(base.x - 1.7, base.y - 5.5, base.z - 1.7, base.x + 1.7, base.y + 2.2, base.z + 1.7);
		int broken = 0;
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (broken >= 24) break;
			// keep the ground he walks on: only what sticks out above the tendril feet
			if (p.getY() < groundBelow(level, player) + 1) continue;
			if (!Supe.breakable(level, p, 6)) continue;
			level.destroyBlock(p, level.getRandom().nextFloat() < 0.5f, player, 512);
			broken++;
		}
		if (broken > 0 && player.tickCount % 3 == 0) {
			Supe.sound(level, base, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.6f, 0.8f);
		}
	}

	/** Y of the first solid block surface below the player (the tendrils stand on it). */
	private static int groundBelow(ServerLevel level, ServerPlayer player) {
		BlockPos.MutableBlockPos p = player.blockPosition().mutable();
		for (int i = 0; i < 12; i++) {
			p.move(0, -1, 0);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
				return p.getY() + 1;
			}
		}
		return p.getY();
	}

	private static void grapple(ServerPlayer player, PlayerSession s) {
		if (!s.ready(3)) {
			PowerManager.notReady(player, s, 3);
			return;
		}
		Supe.Ray ray = Supe.ray(player, player.getEyePosition(), 32);
		if (ray.block() == null && ray.entity() == null) {
			player.sendSystemMessage(Component.translatable("message.theboys.no_target").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		s.cool(3, 50);
		s.grappleTarget = ray.end();
		s.grappleTicks = 18;
		Vec3 from = Supe.chest(player);
		Vec3 p = ray.end();
		ModNetworking.sendFx(player.level(), from, new FxPayload(FxPayload.TENDRIL_STRIKE, player.getId(), (float) p.x, (float) p.y, (float) p.z, 16, 0, 0));
		Supe.sound(player.level(), from, SoundEvents.SLIME_ATTACK, 1.5f, 0.7f);
	}

	static void stop(ServerPlayer player, PlayerSession s) {
		release(s);
		s.thrownId = -1;
		s.grappleTicks = 0;
		s.strikeDelay = -1;
		if (s.cancerWalk) setCancerWalk(player, s, false);
		s.frenzyTicks = 0;
		s.frenzyVictims.clear();
	}
}
