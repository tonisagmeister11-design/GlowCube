package de.theboys.power;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Billy Butcher (season 5): the parasitic tendrils growing out of his chest.
 * Tendril lash (smashes blocks and fells whole trees), grab and throw, rip apart in mid-air, tendril grapple.
 */
public final class Butcher {
	private static final int RIP_TICKS = 40;
	private static final double HOLD_DISTANCE = 3.4;

	private Butcher() {
	}

	static void key(ServerPlayer player, PlayerSession s, int slot, boolean pressed) {
		if (!pressed) return;
		switch (slot) {
			case 0 -> lash(player, s);
			case 1 -> grabOrThrow(player, s);
			case 2 -> rip(player, s);
			case 3 -> grapple(player, s);
			default -> { }
		}
	}

	static int tick(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		ActiveState state = PowerAttachments.active(player);

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
					tearApart(player, s, held);
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

		state = state.with(ActiveState.HOLD, s.heldId >= 0)
				.with(ActiveState.RIP, s.ripTicks > 0)
				.with(ActiveState.GRAPPLE, s.grappleTicks > 0)
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
			Supe.blood(level, e.position().add(0, e.getBbHeight() / 2, 0), e.isDeadOrDying() ? 3f : 1f);
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

	/** Rip apart: lift the victim, the tendrils pull, and they burst. */
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

	private static void tearApart(ServerPlayer player, PlayerSession s, Entity held) {
		ServerLevel level = player.level();
		Vec3 at = held.position().add(0, held.getBbHeight() / 2, 0);
		release(s);
		if (held instanceof LivingEntity living) {
			Supe.burst(player, living, 10000);
		}
		Supe.blood(level, at, 4.5f);
		ModNetworking.sendFx(level, at, new FxPayload(FxPayload.SHAKE, player.getId(), (float) at.x, (float) at.y, (float) at.z, 1.2f, 0, 0));
		Supe.sound(level, at, SoundEvents.PLAYER_HURT, 1.5f, 0.5f);
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
	}
}
