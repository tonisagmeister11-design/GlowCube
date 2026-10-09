package de.theboys.power;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.theboys.TheBoys;
import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Black Adam: the god of the game. Shazam serum only.
 * R (hold) golden lightning that jumps from enemy to enemy and blasts the ground, Sneak+R blue eye beams;
 * G flight (the fastest of all, straight through blocks), Sneak+G calls the Shazam lightning onto himself;
 * C telekinesis (lift a pile of blocks, press again to hurl them); X grab the one in front of him,
 * press again to pour so much lightning into him that only bones are left in his hand, Sneak+X lightning storm.
 * Nearly no weaknesses: only magic gets through.
 */
public final class BlackAdam {
	public static final int ZAP_TICKS = 40;
	private static final int BONES_TICKS = 60;
	private static final int MAX_LIFT = 18;
	public static final Identifier BONES_SCALE = TheBoys.id("black_adam_bones");

	private BlackAdam() {
	}

	static void key(ServerPlayer player, PlayerSession s, int slot, boolean pressed) {
		if (!pressed) return;
		boolean sneak = player.isShiftKeyDown();
		switch (slot) {
			case 0 -> {
				if (sneak) Supe.sound(player.level(), player.getEyePosition(), SoundEvents.BEACON_ACTIVATE, 0.8f, 1.6f);
				else Supe.sound(player.level(), player.getEyePosition(), SoundEvents.TRIDENT_THUNDER, 0.8f, 1.6f);
			}
			case 1 -> {
				if (sneak) shazam(player, s);
				else {
					Homelander.toggleFlight(player, s);
					if (s.flying) {
						player.getAbilities().setFlyingSpeed(0.3f);
						player.onUpdateAbilities();
						Supe.sound(player.level(), player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.5f);
					}
				}
			}
			case 2 -> telekinesis(player, s);
			case 3 -> {
				if (sneak) storm(player, s);
				else grab(player, s);
			}
			default -> { }
		}
	}

	static int tick(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		ActiveState state = PowerAttachments.active(player);
		if (s.shazamCool > 0) s.shazamCool--;
		if (s.stormCool > 0) s.stormCool--;

		// a god heals and shakes off everything
		if (player.tickCount % 20 == 0 && player.getHealth() < player.getMaxHealth()) player.heal(3f);
		if (player.tickCount % 10 == 0) cleanse(player);

		boolean sneak = player.isShiftKeyDown();
		boolean stream = s.keyDown[0] && !sneak && s.heldId < 0;
		boolean eyes = s.keyDown[0] && sneak;
		int target = -1;
		if (stream) target = stream(player, level);
		if (eyes) Homelander.laser(player, s, level, 14f, 60);
		else if (s.burnPos != null) {
			level.destroyBlockProgress(player.getId(), s.burnPos, -1);
			s.burnPos = null;
		}

		tickHeld(player, s, level);
		tickBones(player, s, level);
		tickLifted(player, s, level);
		tickThrown(player, s, level);

		boolean flying = s.flying && player.getAbilities().flying;
		if (s.flying) {
			player.resetFallDistance();
			if (flying && player.tickCount % 5 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 5, 0.4, 0.7, 0.4, 0.2);
			}
		}
		state = state.with(ActiveState.HAND_BEAM, stream).with(ActiveState.LASER, eyes)
				.with(ActiveState.FLYING, flying).with(ActiveState.PHASE, flying)
				.with(ActiveState.HOLD, s.heldId >= 0).with(ActiveState.ZAP, s.zapTicks > 0);
		int shown = s.heldId >= 0 ? s.heldId : s.bonesId >= 0 ? s.bonesId : target;
		PowerAttachments.setActive(player, state.withTarget(shown).withCharge(s.zapTicks));
		return s.zapTicks > 0 ? (ZAP_TICKS - s.zapTicks) * 1000 / ZAP_TICKS : 0;
	}

	private static final List<Holder<MobEffect>> BAD = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.WEAKNESS, MobEffects.SLOWNESS,
			MobEffects.MINING_FATIGUE, MobEffects.BLINDNESS, MobEffects.DARKNESS, MobEffects.NAUSEA, MobEffects.HUNGER, MobEffects.LEVITATION);

	private static void cleanse(ServerPlayer player) {
		for (Holder<MobEffect> e : BAD) {
			if (player.hasEffect(e)) player.removeEffect(e);
		}
		player.clearFire();
		player.setAirSupply(player.getMaxAirSupply());
	}

	/** Damage he simply ignores: only magic (and the void) gets through to him. */
	public static boolean immuneTo(net.minecraft.world.damagesource.DamageSource source) {
		return !source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
				&& (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)
				|| source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
				|| source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING) || source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.SWEET_BERRY_BUSH) || source.is(net.minecraft.world.damagesource.DamageTypes.STALAGMITE)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_STALACTITE) || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_BLOCK)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_ANVIL));
	}

	// ------------------------------------------------------------------ lightning

	/** Golden god-lightning: tears through whatever it hits, jumps on to five more and blasts the ground. */
	private static int stream(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Supe.Ray ray = Supe.ray(player, eye, 64, 0.8f);
		Vec3 end = ray.end();
		if (player.tickCount % 3 == 0) Supe.sound(level, eye, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.6f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 10, 0.3, 0.3, 0.3, 0.5);
		if (player.tickCount % 2 != 0) return ray.entity() != null ? ray.entity().getId() : -1;
		if (ray.entity() == null) {
			// the ground explodes where it hits
			if (ray.block() != null && player.tickCount % 8 == 0) {
				level.explode(player, end.x, end.y, end.z, 2.2f, Level.ExplosionInteraction.MOB);
			}
			return -1;
		}
		Entity hit = ray.entity();
		Supe.hurtNow(player, hit, 7f);
		if (hit instanceof LivingEntity l) {
			l.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 5), player);
			Supe.push(l, player.getLookAngle().scale(0.25));
			if (l.isDeadOrDying()) Supe.blood(level, end, 2f);
		}
		List<LivingEntity> near = Supe.livingAround(level, Supe.chest(hit), 9, player);
		near.removeIf(e -> e == hit);
		near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(hit)));
		for (int i = 0; i < Math.min(5, near.size()); i++) {
			LivingEntity e = near.get(i);
			Vec3 to = Supe.chest(e);
			Supe.hurtNow(player, e, 5f);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 4), player);
			// every enemy near the target is struck by a pillar from the sky
			if (player.tickCount % 6 == 0) {
				ModNetworking.sendFx(level, to, new FxPayload(FxPayload.GOD_BOLT, player.getId(), (float) to.x, (float) to.y + 30, (float) to.z,
						(float) to.x, (float) to.y, (float) to.z));
			}
		}
		return hit.getId();
	}

	/** "SHAZAM!" - the magic lightning strikes him: full health, and a blast that flattens everything around. */
	private static void shazam(ServerPlayer player, PlayerSession s) {
		if (s.shazamCool > 0) {
			cooling(player, s.shazamCool, "ability.theboys.black_adam.shazam");
			return;
		}
		s.shazamCool = 400;
		ServerLevel level = player.level();
		Vec3 c = player.position();
		bolt(player, level, c);
		player.setHealth(player.getMaxHealth());
		double radius = 11;
		for (LivingEntity e : Supe.livingAround(level, c.add(0, 1, 0), radius, player)) {
			float k = (float) (1 - e.distanceTo(player) / (radius + 1));
			Supe.hurtNow(player, e, 10f + 26f * k);
			Supe.push(e, e.position().subtract(c).normalize().scale(1.2 + 2.2 * k).add(0, 0.6 + 0.5 * k, 0));
			if (e.isDeadOrDying()) Supe.blood(level, Supe.chest(e), 2.5f);
		}
		Vec3 m = c.add(0, 1, 0);
		ModNetworking.sendFx(level, m, new FxPayload(FxPayload.NOVA, player.getId(), (float) m.x, (float) m.y, (float) m.z, (float) radius, 0xF2C230, 1));
		ModNetworking.sendFx(level, m, new FxPayload(FxPayload.SHOCKWAVE, player.getId(), (float) m.x, (float) c.y + 0.2f, (float) m.z, (float) radius * 1.3f, Float.NaN, 0));
		ModNetworking.sendFx(level, m, new FxPayload(FxPayload.SHAKE, player.getId(), (float) m.x, (float) m.y, (float) m.z, 3f, 0, 0));
		Vec3 sky = c.add(0, 40, 0);
		ModNetworking.sendFx(level, sky, new FxPayload(FxPayload.GOD_BOLT, player.getId(), (float) sky.x, (float) sky.y, (float) sky.z, (float) m.x, (float) m.y, (float) m.z));
		Supe.sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 3f, 0.6f);
		Supe.sound(level, c, SoundEvents.GENERIC_EXPLODE, 2f, 0.7f);
		player.sendSystemMessage(Component.literal("SHAZAM!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
	}

	/** Lightning storm: a real bolt onto every enemy far and wide. */
	private static void storm(ServerPlayer player, PlayerSession s) {
		if (s.stormCool > 0) {
			cooling(player, s.stormCool, "ability.theboys.black_adam.storm");
			return;
		}
		ServerLevel level = player.level();
		List<LivingEntity> targets = Supe.livingAround(level, player.position(), 30, player);
		targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		if (targets.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.theboys.no_target").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		s.stormCool = 500;
		Vec3 hand = player.getEyePosition().add(0, 0.6, 0);
		ModNetworking.sendFx(level, hand, new FxPayload(FxPayload.GOD_BOLT, player.getId(), (float) hand.x, (float) hand.y, (float) hand.z,
				(float) hand.x, (float) hand.y + 40, (float) hand.z));
		for (int i = 0; i < Math.min(16, targets.size()); i++) {
			LivingEntity e = targets.get(i);
			bolt(player, level, e.position());
			Vec3 c = Supe.chest(e);
			ModNetworking.sendFx(level, c, new FxPayload(FxPayload.GOD_BOLT, player.getId(), (float) c.x, (float) c.y + 40, (float) c.z, (float) c.x, (float) c.y, (float) c.z));
			Supe.hurtNow(player, e, 20f);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 5), player);
		}
		Supe.sound(level, player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 3f, 0.8f);
	}

	private static void bolt(ServerPlayer player, ServerLevel level, Vec3 at) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) return;
		bolt.setPos(at);
		bolt.setCause(player);
		level.addFreshEntity(bolt);
	}

	private static void cooling(ServerPlayer player, int ticks, String key) {
		player.sendSystemMessage(Component.translatable("message.theboys.cooldown", Component.translatable(key),
				String.format("%.1f", ticks / 20f)).withStyle(ChatFormatting.RED), true);
	}

	// ------------------------------------------------------------------ grab and burn to the bones

	private static void grab(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		if (s.heldId >= 0) {
			if (s.zapTicks > 0) return;
			// so much lightning that only the bones are left
			s.zapTicks = ZAP_TICKS;
			Supe.sound(level, player.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 2f, 0.6f);
			Supe.sound(level, player.position(), SoundEvents.BEACON_ACTIVATE, 2f, 0.5f);
			return;
		}
		if (s.bonesId >= 0) return;
		Entity target = Supe.ray(player, player.getEyePosition(), 5, 1.2f).entity();
		if (!(target instanceof LivingEntity) || !target.isAlive()) {
			player.sendSystemMessage(Component.translatable("message.theboys.no_target").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		s.heldId = target.getId();
		s.holdTicks = 0;
		Supe.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.5f, 0.6f);
		if (target instanceof ServerPlayer p) {
			p.sendSystemMessage(Component.translatable("message.theboys.adam_grabbed").withStyle(ChatFormatting.GOLD), true);
		}
	}

	/** Where he holds someone: up in his raised fist, by the throat, feet dangling. */
	private static Vec3 handSpot(ServerPlayer player, Entity held) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 hand = player.position().add(0, player.getBbHeight() * 1.05, 0).add(flat.scale(0.85 + held.getBbWidth() * 0.5));
		return hand.subtract(0, held.getBbHeight() * 0.85, 0);
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

	private static void tickHeld(ServerPlayer player, PlayerSession s, ServerLevel level) {
		if (s.heldId < 0) return;
		Entity held = level.getEntity(s.heldId);
		if (held == null || !held.isAlive() || held.distanceTo(player) > 8) {
			s.heldId = -1;
			s.zapTicks = 0;
			return;
		}
		s.holdTicks++;
		hold(held, handSpot(player, held));
		if (held instanceof LivingEntity l && s.zapTicks == 0) {
			l.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 10, false, false));
		}
		if (s.zapTicks <= 0) return;
		s.zapTicks--;
		Vec3 c = Supe.chest(held);
		// the body shakes and smokes, sparks everywhere
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 14, 0.3, 0.5, 0.3, 0.6);
		if (s.zapTicks % 2 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 3, 0.2, 0.4, 0.2, 0.02);
		if (s.zapTicks % 4 == 0) Supe.sound(level, c, SoundEvents.BEE_LOOP_AGGRESSIVE, 1.5f, 2f);
		if (s.zapTicks % 10 == 0) Supe.sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2f, 1.4f);
		if (s.zapTicks == 0) burnToBones(player, s, level, held);
	}

	/** Nothing is left of the victim but a skeleton in his fist. */
	private static void burnToBones(ServerPlayer player, PlayerSession s, ServerLevel level, Entity victim) {
		s.heldId = -1;
		float height = victim.getBbHeight();
		Vec3 at = victim.position();
		Vec3 c = Supe.chest(victim);
		Mob bones = EntityTypes.SKELETON.create(level, EntitySpawnReason.TRIGGERED);
		Supe.hurtNow(player, victim, 100000f);
		if (!(victim instanceof Player)) {
			// the body is gone at once: no death animation, only ash
			victim.discard();
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 40, 0.4, 0.6, 0.4, 0.05);
		level.sendParticles(ParticleTypes.WHITE_ASH, c.x, c.y, c.z, 60, 0.4, 0.6, 0.4, 0.05);
		ModNetworking.sendFx(level, c, new FxPayload(FxPayload.NOVA, player.getId(), (float) c.x, (float) c.y, (float) c.z, 3.5f, 0xF2C230, 1));
		Supe.sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 2f, 1.2f);
		Supe.sound(level, c, SoundEvents.SKELETON_HURT, 1.5f, 0.6f);
		if (bones == null) return;
		bones.setPos(at);
		bones.setYRot(player.getYRot() + 180);
		bones.setNoAi(true);
		bones.setSilent(true);
		bones.setPersistenceRequired();
		BONES.add(bones.getId());
		var scale = bones.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.addOrUpdateTransientModifier(new AttributeModifier(BONES_SCALE, Math.max(0.35, height / 1.99) - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		level.addFreshEntity(bones);
		s.bonesId = bones.getId();
		s.bonesTicks = BONES_TICKS;
	}

	private static void tickBones(ServerPlayer player, PlayerSession s, ServerLevel level) {
		if (s.bonesId < 0) return;
		Entity bones = level.getEntity(s.bonesId);
		if (bones == null) {
			s.bonesId = -1;
			return;
		}
		hold(bones, handSpot(player, bones));
		if (--s.bonesTicks > 0) return;
		// he lets go: the skeleton crumbles
		Vec3 c = Supe.chest(bones);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), c.x, c.y, c.z, 60, 0.3, 0.6, 0.3, 0.2);
		Supe.sound(level, c, SoundEvents.SKELETON_DEATH, 1.2f, 0.7f);
		Supe.sound(level, c, SoundEvents.BONE_BLOCK_BREAK, 1.5f, 0.8f);
		for (ItemStack drop : new ItemStack[] {new ItemStack(Items.BONE, 4), new ItemStack(Items.SKELETON_SKULL)}) {
			ItemEntity item = new ItemEntity(level, c.x, c.y, c.z, drop);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		BONES.remove(bones.getId());
		bones.discard();
		s.bonesId = -1;
	}

	/** Bones held right now (so nothing else hurts them before they crumble). */
	public static boolean isBones(Entity e) {
		return BONES.contains(e.getId());
	}

	private static final java.util.Set<Integer> BONES = new java.util.HashSet<>();

	// ------------------------------------------------------------------ telekinesis

	private static void telekinesis(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		if (!s.lifted.isEmpty()) {
			hurl(player, s, level);
			return;
		}
		Supe.Ray ray = Supe.ray(player, player.getEyePosition(), 40);
		BlockPos center = ray.block() != null ? ray.block() : BlockPos.containing(ray.end()).below();
		List<BlockPos> picks = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 1, 2))) {
			BlockState st = level.getBlockState(p);
			if (st.hasBlockEntity() || !Supe.breakable(level, p, 50) || !st.getFluidState().isEmpty()) continue;
			picks.add(p.immutable());
		}
		picks.sort(Comparator.comparingDouble(p -> -p.getY() * 100 + p.distSqr(center)));
		if (picks.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.theboys.no_target").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		for (int i = 0; i < Math.min(MAX_LIFT, picks.size()); i++) {
			BlockPos p = picks.get(i);
			FallingBlockEntity fb = FallingBlockEntity.fall(level, p, level.getBlockState(p));
			fb.setNoGravity(true);
			fb.setDeltaMovement(0, 0.3, 0);
			s.lifted.add(fb.getId());
		}
		Supe.sound(level, Vec3.atCenterOf(center), SoundEvents.ILLUSIONER_CAST_SPELL, 2f, 0.6f);
		Supe.sound(level, Vec3.atCenterOf(center), SoundEvents.RESPAWN_ANCHOR_CHARGE, 2f, 0.6f);
	}

	/** The lifted blocks circle above him like a crown, waiting to be thrown. */
	private static void tickLifted(ServerPlayer player, PlayerSession s, ServerLevel level) {
		if (s.lifted.isEmpty()) return;
		int n = s.lifted.size();
		Iterator<Integer> it = s.lifted.iterator();
		int i = 0;
		while (it.hasNext()) {
			Entity e = level.getEntity(it.next());
			if (!(e instanceof FallingBlockEntity fb) || !fb.isAlive()) {
				it.remove();
				continue;
			}
			double a = player.tickCount * 0.08 + i * Math.PI * 2 / n;
			double r = 2.4 + (i % 3) * 0.6;
			Vec3 spot = player.position().add(Math.cos(a) * r, 3.2 + (i % 3) * 0.8 + Math.sin(player.tickCount * 0.1 + i) * 0.2, Math.sin(a) * r);
			Vec3 to = spot.subtract(fb.position());
			fb.setDeltaMovement(to.scale(0.35));
			fb.time = 1;
			fb.needsSync = true;
			i++;
			if (player.tickCount % 4 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, fb.getX(), fb.getY() + 0.5, fb.getZ(), 1, 0.3, 0.3, 0.3, 0.05);
		}
	}

	private static void hurl(ServerPlayer player, PlayerSession s, ServerLevel level) {
		Supe.Ray ray = Supe.ray(player, player.getEyePosition(), 64, 1.0f);
		Vec3 aim = ray.end();
		var rnd = level.getRandom();
		for (int id : s.lifted) {
			Entity e = level.getEntity(id);
			if (!(e instanceof FallingBlockEntity fb)) continue;
			Vec3 dir = aim.add((rnd.nextDouble() - 0.5) * 3, (rnd.nextDouble() - 0.5) * 2, (rnd.nextDouble() - 0.5) * 3).subtract(fb.position()).normalize();
			fb.setNoGravity(false);
			fb.setDeltaMovement(dir.scale(2.4));
			fb.needsSync = true;
			s.thrown.put(id, 0);
		}
		s.lifted.clear();
		Supe.sound(level, player.position(), SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 1.2f);
	}

	private static void tickThrown(ServerPlayer player, PlayerSession s, ServerLevel level) {
		Iterator<Map.Entry<Integer, Integer>> it = s.thrown.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Integer> en = it.next();
			Entity e = level.getEntity(en.getKey());
			en.setValue(en.getValue() + 1);
			if (e == null || !e.isAlive() || en.getValue() > 80) {
				it.remove();
				continue;
			}
			for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(0.4), v -> v != player && v.isAlive())) {
				Supe.hurtNow(player, v, 16f);
				Supe.push(v, e.getDeltaMovement().normalize().scale(1.4).add(0, 0.4, 0));
				Supe.sound(level, v.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.6f);
				if (v.isDeadOrDying()) Supe.blood(level, Supe.chest(v), 2.5f);
			}
		}
	}

	static void stop(ServerPlayer player, PlayerSession s) {
		if (s.flying) {
			s.flying = false;
			Homelander.endFlight(player);
			player.onUpdateAbilities();
		}
		ServerLevel level = player.level();
		for (int id : s.lifted) {
			Entity e = level.getEntity(id);
			if (e instanceof FallingBlockEntity fb) fb.setNoGravity(false);
		}
		s.lifted.clear();
		if (s.bonesId >= 0) {
			Entity b = level.getEntity(s.bonesId);
			if (b != null) b.discard();
			BONES.remove(s.bonesId);
		}
		s.bonesId = -1;
		s.heldId = -1;
		s.zapTicks = 0;
		if (s.burnPos != null) {
			level.destroyBlockProgress(player.getId(), s.burnPos, -1);
			s.burnPos = null;
		}
	}
}
