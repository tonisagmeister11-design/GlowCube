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
	public static final double GIANT_SCALE = 2.5;
	public static final Identifier GIANT_DAMAGE = TheBoys.id("minimaus_giant_damage");
	public static final Identifier GIANT_REACH = TheBoys.id("minimaus_giant_reach");
	private static final int RAT_TICKS = 22;
	private static final int MOON_WINDUP = 9;
	private static final double MOON_HEIGHT = 120;

	/** Entities knocked into the sky by To the Moon. */
	private record Launch(Entity entity, double targetY, int[] ticks, Vec3[] velocity) {
	}

	private static final List<Launch> LAUNCHES = new ArrayList<>();

	private MiniMaus() {
	}

	// ------------------------------------------------------------------ keys

	static void key(ServerPlayer player, PlayerSession s, int slot, boolean pressed) {
		if (!pressed) return;
		// sneaking: the second set of moves
		if (player.isShiftKeyDown() && slot < 3) {
			switch (slot) {
				case 0 -> ratFlood(player, s);
				case 1 -> squeak(player, s);
				default -> giant(player, s);
			}
			return;
		}
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
				s.giantTicks = 0;
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
		if (!target.isAlive()) return false;
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
		// the blow itself only hurts a bit; the fall from 120 blocks does the rest
		Supe.hurt(player, target, 4f);
		TheBoys.LOGGER.debug("To the Moon: {} hit at y={}, health={}", target.getType().toShortString(), at.y, target.getHealth());
		LAUNCHES.removeIf(l -> l.entity() == target);
		LAUNCHES.add(new Launch(target, at.y + MOON_HEIGHT, new int[] {0}, new Vec3[] {Vec3.ZERO}));
		ModNetworking.sendFx(level, at, new FxPayload(FxPayload.SHOCKWAVE, player.getId(), (float) at.x, (float) at.y + 0.2f, (float) at.z, 5f, Float.NaN, 0));
		ModNetworking.sendFx(level, at, new FxPayload(FxPayload.SHOCKWAVE, player.getId(), (float) at.x, (float) at.y + 0.5f, (float) at.z, 14f, 0f, -90f));
		ModNetworking.sendFx(level, at, new FxPayload(FxPayload.SHAKE, player.getId(), (float) at.x, (float) at.y, (float) at.z, 2.0f, 0, 0));
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 3, 0.4, 0.2, 0.4, 0);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 40, 1.2, 0.1, 1.2, 0.15);
		Supe.sound(level, at, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0f, 0.7f);
		Supe.sound(level, at, SoundEvents.GENERIC_EXPLODE, 1.2f, 1.4f);
		s.cool(1, 240);
	}

	/**
	 * The punch that starts To the Moon must not land as a normal hit (it would kill small mobs before
	 * they fly), and nobody hurts the victim she is winding up on.
	 */
	public static boolean blocksDamage(Entity victim) {
		for (ServerPlayer p : victim.level().getServer().getPlayerList().getPlayers()) {
			if (PowerAttachments.powerOf(p) == Power.MINIMAUS && PowerManager.session(p).moonTarget == victim.getId()) return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ sneak moves

	private static boolean cooling(ServerPlayer player, int ticks, String key) {
		if (ticks <= 0) return false;
		player.sendSystemMessage(Component.translatable("message.theboys.cooldown", Component.translatable(key),
				String.format("%.1f", ticks / 20f)).withStyle(ChatFormatting.RED), true);
		return true;
	}

	/** Rat Flood: a wave of rats pours out in front of her and runs everything over. */
	private static void ratFlood(ServerPlayer player, PlayerSession s) {
		if (cooling(player, s.ratCool, "ability.theboys.minimaus.rats")) return;
		s.ratCool = 400;
		s.ratTicks = RAT_TICKS;
		s.ratYaw = player.getYRot();
		s.ratOrigin = player.position();
		s.ratHit.clear();
		ServerLevel level = player.level();
		ModNetworking.sendFx(level, s.ratOrigin, new FxPayload(FxPayload.RATS, player.getId(), (float) s.ratOrigin.x, (float) s.ratOrigin.y,
				(float) s.ratOrigin.z, s.ratYaw, RAT_TICKS, 0));
		for (int i = 0; i < 3; i++) {
			Supe.sound(level, s.ratOrigin, SoundEvents.SILVERFISH_AMBIENT, 1.5f, 0.8f + i * 0.3f);
		}
		Supe.sound(level, s.ratOrigin, SoundEvents.FOX_SCREECH, 1.0f, 1.8f);
	}

	private static void tickRats(ServerPlayer player, PlayerSession s, ServerLevel level) {
		int t = RAT_TICKS - s.ratTicks--;
		double front = t * 0.7;
		Vec3 fwd = MiniMausMath.forward(s.ratYaw);
		for (LivingEntity e : Supe.livingAround(level, s.ratOrigin, front + 1, player)) {
			if (s.ratHit.contains(e.getId())) continue;
			Vec3 to = e.position().subtract(s.ratOrigin);
			double along = to.x * fwd.x + to.z * fwd.z;
			double side = Math.abs(to.x * fwd.z - to.z * fwd.x);
			// a cone that widens as the rats spread out
			if (along < 0 || along > front || side > 1.2 + along * 0.45 || Math.abs(to.y) > 3) continue;
			s.ratHit.add(e.getId());
			Supe.hurt(player, e, 7f);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), player);
			e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1), player);
			Supe.push(e, fwd.scale(0.7).add(0, 0.35, 0));
			Supe.sound(level, e.position(), SoundEvents.SILVERFISH_HURT, 1.0f, 1.3f);
		}
		if (t % 4 == 0) {
			Supe.sound(level, s.ratOrigin.add(fwd.scale(front)), SoundEvents.SILVERFISH_STEP, 1.2f, 1.4f);
		}
	}

	/** Squeak: an ear-splitting mouse scream that throws everything around her back and makes it reel. */
	private static void squeak(ServerPlayer player, PlayerSession s) {
		if (cooling(player, s.squeakCool, "ability.theboys.minimaus.squeak")) return;
		s.squeakCool = 300;
		ServerLevel level = player.level();
		Vec3 c = player.position().add(0, player.getBbHeight() * 0.6, 0);
		double radius = 9;
		for (LivingEntity e : Supe.livingAround(level, c, radius, player)) {
			double d = Math.max(1, e.distanceTo(player));
			float k = (float) (1 - d / (radius + 1));
			Supe.hurt(player, e, 4f + 6f * k);
			e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0), player);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1), player);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 0), player);
			Supe.push(e, e.position().subtract(player.position()).normalize().scale(0.6 + 1.4 * k).add(0, 0.4 + 0.3 * k, 0));
		}
		for (int i = 0; i < 3; i++) {
			ModNetworking.sendFx(level, c, new FxPayload(FxPayload.SHOCKWAVE, player.getId(), (float) c.x, (float) c.y - 0.3f * i, (float) c.z,
					(float) radius - i * 2, Float.NaN, 0));
		}
		ModNetworking.sendFx(level, c, new FxPayload(FxPayload.SHAKE, player.getId(), (float) c.x, (float) c.y, (float) c.z, 1.5f, 0, 0));
		Supe.sound(level, c, SoundEvents.FOX_SCREECH, 2.5f, 2.0f);
		Supe.sound(level, c, SoundEvents.BAT_TAKEOFF, 2.0f, 1.6f);
		Supe.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 2.0f);
	}

	/** Giant Mouse: for 15 seconds she is two and a half times as big - and hits accordingly. */
	private static void giant(ServerPlayer player, PlayerSession s) {
		if (s.giantTicks > 0) return;
		if (cooling(player, s.giantCool, "ability.theboys.minimaus.giant")) return;
		s.small = false;
		s.giantTicks = 300;
		s.giantCool = 300 + 600;
		ServerLevel level = player.level();
		Supe.sound(level, player.position(), SoundEvents.RAVAGER_ROAR, 1.2f, 1.6f);
		Supe.sound(level, player.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 0.6f);
		level.sendParticles(new DustParticleOptions(0xFF6FA5, 2.0f), player.getX(), player.getY() + 1, player.getZ(), 60, 1.0, 1.4, 1.0, 0.1);
		player.sendSystemMessage(Component.translatable("message.theboys.giant_on").withStyle(ChatFormatting.LIGHT_PURPLE), true);
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
			fling(victim, fwd.scale(1.1).add(0, 0.55, 0));
			Supe.hurt(player, victim, 4f);
			Supe.sound(level, victim.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.6f);
			s.smashTarget = -1;
		}
	}

	private static void slamImpact(ServerPlayer player, LivingEntity victim, Vec3 head, int slam) {
		ServerLevel level = player.level();
		Supe.hurt(player, victim, 7f);
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

		// shrinking / growing (and the giant mouse), smoothly
		if (s.giantTicks > 0 && --s.giantTicks == 0) {
			Supe.sound(level, player.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 1.2f);
		}
		double target = s.small ? SMALL_SCALE : s.giantTicks > 0 ? GIANT_SCALE : 1.0;
		double scale = Math.abs(target - s.scale) < 0.002 ? target : s.scale + (target - s.scale) * 0.22;
		if (scale != s.scale) {
			s.scale = scale;
			PowerManager.modifier(player, Attributes.SCALE, SCALE, Math.abs(scale - 1.0) < 0.001 ? 0 : scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerManager.modifier(player, Attributes.ATTACK_DAMAGE, GIANT_DAMAGE, scale > 1.01 ? 8.0 * (scale - 1) / (GIANT_SCALE - 1) : 0, AttributeModifier.Operation.ADD_VALUE);
			PowerManager.modifier(player, Attributes.ENTITY_INTERACTION_RANGE, GIANT_REACH, scale > 1.01 ? 2.0 * (scale - 1) / (GIANT_SCALE - 1) : 0, AttributeModifier.Operation.ADD_VALUE);
		}
		// twice a normal player's speed - relative to her size: tiny, she walks pixel by pixel instead of racing
		double speedFactor = 2.0 * Math.min(1.0, scale);
		if (Math.abs(speedFactor - s.speedFactor) > 0.001) {
			s.speedFactor = speedFactor;
			PowerManager.modifier(player, Attributes.MOVEMENT_SPEED, PowerManager.DOUBLE_SPEED, speedFactor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}

		if (s.biteTicks > 0) s.biteTicks--;
		if (s.ratTicks > 0) tickRats(player, s, level);
		if (s.ratCool > 0) s.ratCool--;
		if (s.squeakCool > 0) s.squeakCool--;
		if (s.giantCool > 0) s.giantCool--;

		// To the Moon: wind up while the victim is frozen, then the blow
		if (s.moonTarget >= 0) {
			Entity e = level.getEntity(s.moonTarget);
			if (!(e instanceof LivingEntity victim) || !victim.isAlive()) {
				s.moonTarget = -1;
			} else {
				hold(victim, s.moonSpot, victim.getYRot());
				if (--s.moonTicks <= 0) {
					s.moonTarget = -1;
					moonStrike(player, s, victim);
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

	/** Moves everything that was sent To the Moon or thrown away (called every server tick). */
	public static void tickLaunches() {
		Iterator<Launch> it = LAUNCHES.iterator();
		while (it.hasNext()) {
			Launch l = it.next();
			Entity e = l.entity();
			l.ticks()[0]++;
			if (e.isRemoved() || !e.isAlive() || l.ticks()[0] > 90) {
				if (!Double.isNaN(l.targetY())) {
					TheBoys.LOGGER.debug("To the Moon ended after {} ticks at y={} (removed={}, alive={})", l.ticks()[0], e.getY(), e.isRemoved(), e.isAlive());
				}
				it.remove();
				continue;
			}
			Vec3 v;
			if (Double.isNaN(l.targetY())) {
				// thrown: flies off in an arc
				if (l.ticks()[0] > 25 || l.ticks()[0] > 3 && e.onGround()) {
					it.remove();
					continue;
				}
				v = l.velocity()[0];
				l.velocity()[0] = new Vec3(v.x * 0.9, v.y - 0.08, v.z * 0.9);
			} else {
				double left = l.targetY() - e.getY();
				if (left <= 1) {
					TheBoys.LOGGER.debug("To the Moon reached y={} after {} ticks", e.getY(), l.ticks()[0]);
					it.remove();
					continue;
				}
				// keeps rocketing upwards until it is ~120 blocks above where it was hit
				v = new Vec3(0, Math.min(3.6, Math.max(0.6, left * 0.5)), 0);
				if (e.level() instanceof ServerLevel level && l.ticks()[0] % 2 == 0) {
					level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY(), e.getZ(), 3, 0.2, 0.1, 0.2, 0.02);
				}
			}
			e.resetFallDistance();
			if (e instanceof ServerPlayer) {
				e.setDeltaMovement(v);
				e.needsSync = true;
			} else {
				// moved directly, so it works for every mob (also ones without AI)
				e.setDeltaMovement(v);
				e.move(net.minecraft.world.entity.MoverType.SELF, v);
			}
		}
	}

	private static void fling(Entity e, Vec3 velocity) {
		LAUNCHES.removeIf(l -> l.entity() == e);
		LAUNCHES.add(new Launch(e, Double.NaN, new int[] {0}, new Vec3[] {velocity}));
	}

	public static void stop(ServerPlayer player, PlayerSession s) {
		s.small = false;
		s.biteArmed = false;
		s.moonArmed = false;
		s.moonTarget = -1;
		s.smashTarget = -1;
		PowerManager.modifier(player, Attributes.SCALE, SCALE, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerManager.modifier(player, Attributes.ATTACK_DAMAGE, GIANT_DAMAGE, 0, AttributeModifier.Operation.ADD_VALUE);
		PowerManager.modifier(player, Attributes.ENTITY_INTERACTION_RANGE, GIANT_REACH, 0, AttributeModifier.Operation.ADD_VALUE);
		s.scale = 1.0;
		s.giantTicks = 0;
		s.ratTicks = 0;
	}
}
