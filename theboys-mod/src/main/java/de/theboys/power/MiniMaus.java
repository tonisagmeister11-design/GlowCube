package de.theboys.power;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.theboys.TheBoys;
import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * MiniMaus (Mini V): a mouse with twice the speed who can shrink to the size of a pixel and still hits
 * like a truck. Poison Bite, To the Moon, Multi Smash, and shrinking (which lets her chip single pixels
 * out of blocks).
 */
public final class MiniMaus {
	public static final Identifier SCALE = TheBoys.id("minimaus_scale");
	/** The smallest size Minecraft allows: 1/16, a player about two pixels tall. */
	public static final double SMALL_SCALE = 0.0625;
	private static final int SHRINK_TICKS = 12;
	private static final int MOON_WINDUP = 9;
	private static final double MOON_HEIGHT = 120;

	/** Entities knocked into the sky by To the Moon. */
	private record Launch(Entity entity, double targetY, int[] ticks) {
	}

	private static final List<Launch> LAUNCHES = new ArrayList<>();

	private MiniMaus() {
	}

	// ------------------------------------------------------------------ keys

	static void key(ServerPlayer player, PlayerSession s, int slot, boolean pressed) {
		if (!pressed) return;
		switch (slot) {
			case 0 -> {
				if (!s.ready(0)) {
					PowerManager.notReady(player, s, 0);
					return;
				}
				s.biteArmed = !s.biteArmed;
				player.sendSystemMessage(Component.translatable(s.biteArmed ? "message.theboys.bite_ready" : "message.theboys.bite_off")
						.withStyle(ChatFormatting.GREEN), true);
				Supe.sound(player.level(), player.position(), SoundEvents.SILVERFISH_AMBIENT, 1.0f, 1.6f);
			}
			case 1 -> {
				if (!s.ready(1)) {
					PowerManager.notReady(player, s, 1);
					return;
				}
				s.moonArmed = !s.moonArmed;
				player.sendSystemMessage(Component.translatable(s.moonArmed ? "message.theboys.moon_ready" : "message.theboys.moon_off")
						.withStyle(ChatFormatting.GOLD), true);
			}
			case 2 -> startSmash(player, s);
			case 3 -> {
				s.small = !s.small;
				s.shrinkTicks = SHRINK_TICKS;
				Supe.sound(player.level(), player.position(), s.small ? SoundEvents.ILLUSIONER_MIRROR_MOVE : SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, s.small ? 2.0f : 1.4f);
				player.level().sendParticles(new DustParticleOptions(0xFF6FA5, 0.8f), player.getX(), player.getY() + player.getBbHeight() / 2,
						player.getZ(), 30, 0.4, 0.5, 0.4, 0.05);
				player.sendSystemMessage(Component.translatable(s.small ? "message.theboys.small_on" : "message.theboys.small_off")
						.withStyle(ChatFormatting.LIGHT_PURPLE), true);
			}
			default -> { }
		}
	}

	// ------------------------------------------------------------------ attacks

	/**
	 * Melee hook. Returns true when the normal hit should be replaced (To the Moon winds up instead).
	 * Poison Bite rides on top of the normal hit.
	 */
	public static boolean onAttack(ServerPlayer player, Entity target) {
		PlayerSession s = PowerManager.session(player);
		if (s.smashTarget >= 0 || s.moonTarget >= 0) return true;
		if (s.moonArmed && target instanceof LivingEntity) {
			s.moonArmed = false;
			s.moonTarget = target.getId();
			s.moonTicks = MOON_WINDUP;
			s.moonSpot = target.position();
			Supe.sound(player.level(), player.position(), SoundEvents.TRIDENT_RIPTIDE_2, 1.0f, 0.7f);
			return true;
		}
		if (s.biteArmed && target instanceof LivingEntity living) {
			s.biteArmed = false;
			s.biteTicks = 8;
			s.cool(0, 120);
			bite(player, living);
		}
		return false;
	}

	private static void bite(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		Supe.hurt(player, target, 6f);
		target.addEffect(new MobEffectInstance(MobEffects.POISON, 260, 3), player);
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 260, 1), player);
		if (target instanceof Player) {
			target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0), player);
		}
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
		level.sendParticles(new DustParticleOptions(0x5BD12E, 1.4f), at.x, at.y, at.z, 30, 0.3, 0.35, 0.3, 0.08);
		level.sendParticles(new DustParticleOptions(0x2E7A12, 1.0f), at.x, at.y, at.z, 15, 0.25, 0.3, 0.25, 0.04);
		level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.05);
		ModNetworking.sendFx(level, at, new FxPayload(FxPayload.BITE, player.getId(), (float) at.x, (float) at.y, (float) at.z, target.getId(), 0, 0));
		Supe.sound(level, at, SoundEvents.FOX_BITE, 1.4f, 0.8f);
		Supe.sound(level, at, SoundEvents.SPIDER_HURT, 0.6f, 1.6f);
	}

	private static void moonStrike(ServerPlayer player, PlayerSession s, LivingEntity target) {
		ServerLevel level = player.level();
		Vec3 at = target.position();
		Supe.hurt(player, target, 12f);
		LAUNCHES.removeIf(l -> l.entity() == target);
		LAUNCHES.add(new Launch(target, at.y + MOON_HEIGHT, new int[] {0}));
		Supe.push(target, new Vec3(0, 3.5, 0).subtract(target.getDeltaMovement()));
		ModNetworking.sendFx(level, at, new FxPayload(FxPayload.SHOCKWAVE, player.getId(), (float) at.x, (float) at.y + 0.2f, (float) at.z, 5f, Float.NaN, 0));
		ModNetworking.sendFx(level, at, new FxPayload(FxPayload.SHOCKWAVE, player.getId(), (float) at.x, (float) at.y + 0.5f, (float) at.z, 14f, 0f, -90f));
		ModNetworking.sendFx(level, at, new FxPayload(FxPayload.SHAKE, player.getId(), (float) at.x, (float) at.y, (float) at.z, 2.0f, 0, 0));
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 3, 0.4, 0.2, 0.4, 0);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 40, 1.2, 0.1, 1.2, 0.15);
		Supe.sound(level, at, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0f, 0.7f);
		Supe.sound(level, at, SoundEvents.GENERIC_EXPLODE, 1.2f, 1.4f);
		s.cool(1, 240);
	}

	// ------------------------------------------------------------------ Multi Smash

	private static void startSmash(ServerPlayer player, PlayerSession s) {
		if (s.smashTarget >= 0) return;
		if (!s.ready(2)) {
			PowerManager.notReady(player, s, 2);
			return;
		}
		// tiny or not, she grabs whatever is in front of her
		double reach = s.small ? 3.0 : 4.5;
		Supe.Ray ray = Supe.ray(player, player.getEyePosition(), reach, 0.6f);
		Entity target = ray.entity();
		if (target == null) {
			for (LivingEntity e : Supe.livingAround(player.level(), player.position().add(MiniMausMath.forward(player.getYRot()).scale(1.2)), 2.0, player)) {
				target = e;
				break;
			}
		}
		if (!(target instanceof LivingEntity)) {
			player.sendSystemMessage(Component.translatable("message.theboys.no_target").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		s.smashTarget = target.getId();
		s.smashTicks = 0;
		s.cool(2, 260);
		Supe.sound(player.level(), target.position(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.2f, 0.6f);
	}

	private static void tickSmash(ServerPlayer player, PlayerSession s, ServerLevel level) {
		Entity e = level.getEntity(s.smashTarget);
		if (!(e instanceof LivingEntity victim) || !victim.isAlive() || victim.distanceToSqr(player) > 100) {
			s.smashTarget = -1;
			return;
		}
		s.smashTicks++;
		float yaw = player.getYRot();
		Vec3 pivot = MiniMausMath.pivot(player, 1f);
		float max = MiniMausMath.maxAngle(player, victim);
		// lifted off the ground during the first ticks
		Vec3 feet = s.smashTicks < MiniMausMath.LIFT
				? victim.position().lerp(pivot, Math.min(1.0, s.smashTicks / (double) MiniMausMath.LIFT * 2))
				: pivot;
		hold(victim, feet, yaw);
		int slam = MiniMausMath.slamAt(s.smashTicks);
		if (slam > 0) {
			float a = MiniMausMath.side(slam - 1) * max;
			Vec3 head = pivot.add(MiniMausMath.body(yaw, a).scale(victim.getBbHeight()));
			slamImpact(player, victim, head, slam);
		}
		if (s.smashTicks >= MiniMausMath.THROW) {
			Vec3 fwd = MiniMausMath.forward(yaw);
			victim.setDeltaMovement(Vec3.ZERO);
			Supe.push(victim, fwd.scale(1.1).add(0, 0.55, 0));
			Supe.hurt(player, victim, 4f);
			Supe.sound(level, victim.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.6f);
			s.smashTarget = -1;
		}
	}

	private static void slamImpact(ServerPlayer player, LivingEntity victim, Vec3 head, int slam) {
		ServerLevel level = player.level();
		Supe.hurt(player, victim, 5f);
		BlockPos ground = BlockPos.containing(head.x, head.y - 0.2, head.z);
		BlockState state = level.getBlockState(ground);
		if (state.isAir()) state = level.getBlockState(ground.below());
		if (!state.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), head.x, head.y + 0.1, head.z, 30, 0.5, 0.1, 0.5, 0.2);
		}
		level.sendParticles(ParticleTypes.CLOUD, head.x, head.y + 0.1, head.z, 8, 0.4, 0.05, 0.4, 0.05);
		ModNetworking.sendFx(level, head, new FxPayload(FxPayload.SHOCKWAVE, player.getId(), (float) head.x, (float) head.y + 0.05f, (float) head.z, 2.5f, Float.NaN, 0));
		ModNetworking.sendFx(level, head, new FxPayload(FxPayload.SHAKE, player.getId(), (float) head.x, (float) head.y, (float) head.z, 0.8f, 0, 0));
		Supe.sound(level, head, SoundEvents.MACE_SMASH_GROUND, 1.4f, 0.8f + slam * 0.05f);
		Supe.sound(level, head, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
		// whoever stands where the body comes down gets hit too
		for (LivingEntity other : Supe.livingAround(level, head, 1.6, player)) {
			if (other != victim) {
				Supe.hurt(player, other, 4f);
				Supe.push(other, other.position().subtract(head).normalize().scale(0.6).add(0, 0.3, 0));
			}
		}
	}

	private static void hold(Entity held, Vec3 feet, float yaw) {
		held.setDeltaMovement(Vec3.ZERO);
		held.resetFallDistance();
		held.setYRot(yaw);
		if (held instanceof LivingEntity l) {
			l.setYBodyRot(yaw);
			l.setYHeadRot(yaw);
		}
		if (held instanceof ServerPlayer p) {
			p.teleportTo(p.level(), feet.x, feet.y, feet.z, java.util.Set.of(), yaw, p.getXRot(), false);
		} else {
			held.snapTo(feet.x, feet.y, feet.z, yaw, held.getXRot());
		}
	}

	// ------------------------------------------------------------------ tick

	static int tick(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		ActiveState state = PowerAttachments.active(player);

		// shrinking / growing, smoothly
		double target = s.small ? SMALL_SCALE : 1.0;
		if (s.shrinkTicks > 0) s.shrinkTicks--;
		double k = s.shrinkTicks / (double) SHRINK_TICKS;
		double from = s.small ? 1.0 : SMALL_SCALE;
		double scale = target + (from - target) * k * k;
		PowerManager.modifier(player, Attributes.SCALE, SCALE, scale >= 0.999 ? 0 : scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

		if (s.biteTicks > 0) s.biteTicks--;

		// To the Moon: wind up while the victim is frozen, then the blow
		if (s.moonTarget >= 0) {
			Entity e = level.getEntity(s.moonTarget);
			if (!(e instanceof LivingEntity victim) || !victim.isAlive()) {
				s.moonTarget = -1;
			} else {
				hold(victim, s.moonSpot, victim.getYRot());
				if (--s.moonTicks <= 0) {
					moonStrike(player, s, victim);
					s.moonTarget = -1;
					s.moonStrikeTicks = 8;
				}
			}
		}
		if (s.moonStrikeTicks > 0) s.moonStrikeTicks--;

		if (s.smashTarget >= 0) {
			tickSmash(player, s, level);
		}

		state = state.with(ActiveState.SMALL, s.small)
				.with(ActiveState.BITE, s.biteTicks > 0 || s.biteArmed)
				.with(ActiveState.MOON, s.moonTarget >= 0 || s.moonStrikeTicks > 0 || s.moonArmed)
				.with(ActiveState.SMASH, s.smashTarget >= 0)
				.withTarget(s.smashTarget >= 0 ? s.smashTarget : s.moonTarget)
				.withCharge(s.smashTarget >= 0 ? s.smashTicks : s.moonTarget >= 0 ? MOON_WINDUP - s.moonTicks : s.moonStrikeTicks > 0 ? 100 + s.moonStrikeTicks : 0);
		PowerAttachments.setActive(player, state);
		return 0;
	}

	/** Moves everything that was sent To the Moon (called every server tick). */
	public static void tickLaunches() {
		Iterator<Launch> it = LAUNCHES.iterator();
		while (it.hasNext()) {
			Launch l = it.next();
			Entity e = l.entity();
			l.ticks()[0]++;
			if (e.isRemoved() || !e.isAlive() || l.ticks()[0] > 90) {
				it.remove();
				continue;
			}
			double left = l.targetY() - e.getY();
			if (left <= 1) {
				it.remove();
				continue;
			}
			// keeps rocketing upwards until it is ~120 blocks above where it was hit
			double vy = Math.min(3.6, Math.max(0.6, left * 0.5));
			e.setDeltaMovement(e.getDeltaMovement().x * 0.5, vy, e.getDeltaMovement().z * 0.5);
			e.needsSync = true;
			e.resetFallDistance();
			if (e.level() instanceof ServerLevel level && l.ticks()[0] % 2 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY(), e.getZ(), 3, 0.2, 0.1, 0.2, 0.02);
			}
		}
	}

	public static void stop(ServerPlayer player, PlayerSession s) {
		s.small = false;
		s.biteArmed = false;
		s.moonArmed = false;
		s.moonTarget = -1;
		s.smashTarget = -1;
		PowerManager.modifier(player, Attributes.SCALE, SCALE, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}
}
