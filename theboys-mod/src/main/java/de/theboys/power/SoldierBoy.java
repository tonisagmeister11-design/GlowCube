package de.theboys.power;

import de.theboys.entity.ThrownShield;
import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import de.theboys.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Soldier Boy: radiation beam from the chest (hold), the nuke (hold to charge, release),
 * shield throw and shield charge. Passive: super-soldier strength, toughness and regeneration.
 * The nuke burns Compound V out of every other supe it hits.
 */
public final class SoldierBoy {
	public static final int MAX_CHARGE = 60;
	private static final int MAX_BEAM = 70;

	private SoldierBoy() {
	}

	static void key(ServerPlayer player, PlayerSession s, int slot, boolean pressed) {
		switch (slot) {
			case 0 -> {
				if (pressed && !s.ready(0)) PowerManager.notReady(player, s, 0);
				if (pressed && s.ready(0)) {
					Supe.sound(player.level(), player.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.2f, 0.6f);
				}
				if (!pressed && s.beamTicks > 0) endBeam(s);
			}
			case 1 -> {
				if (pressed && !s.ready(1)) PowerManager.notReady(player, s, 1);
				if (!pressed && s.nukeCharge > 0) detonate(player, s);
			}
			case 2 -> {
				if (pressed) throwShield(player, s);
			}
			case 3 -> {
				if (pressed) charge(player, s);
			}
			default -> { }
		}
	}

	static int tick(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		ActiveState state = PowerAttachments.active(player);

		if (player.tickCount % 80 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false));
		}

		// radiation beam
		if (s.keyDown[0] && s.ready(0)) {
			s.beamTicks++;
			beam(player, level, s);
			if (s.beamTicks >= MAX_BEAM) endBeam(s);
		}
		state = state.with(ActiveState.CHEST_BEAM, s.beamTicks > 0 && s.keyDown[0] && s.ready(0));

		// nuke charge-up
		if (s.keyDown[1] && s.ready(1)) {
			s.nukeCharge = Math.min(MAX_CHARGE + 30, s.nukeCharge + 1);
			Vec3 c = Supe.chest(player);
			level.sendParticles(new DustParticleOptions(0xFF8C1A, 1.4f), c.x, c.y, c.z, 3, 0.35, 0.35, 0.35, 0.01);
			if (s.nukeCharge % 10 == 0) {
				Supe.sound(level, c, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.5f + s.nukeCharge / (float) MAX_CHARGE);
			}
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 2, true, false));
			if (s.nukeCharge >= MAX_CHARGE + 30) {
				// he cannot hold it any longer - it goes off on its own
				detonate(player, s);
			}
		}
		state = state.with(ActiveState.NUKE_CHARGE, s.nukeCharge > 0).withCharge(s.nukeCharge);

		PowerAttachments.setActive(player, state);
		return Math.min(1000, s.nukeCharge * 1000 / MAX_CHARGE);
	}

	private static void beam(ServerPlayer player, ServerLevel level, PlayerSession s) {
		Vec3 start = Supe.chest(player);
		Supe.Ray ray = Supe.ray(player, start, 40);
		Vec3 end = ray.end();
		if (s.beamTicks % 5 == 1) {
			Supe.sound(level, start, SoundEvents.BLAZE_SHOOT, 1.0f, 0.5f);
		}
		level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0.05);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, end.x, end.y, end.z, 2, 0.3, 0.3, 0.3, 0.02);
		if (ray.entity() != null) {
			if (s.beamTicks % 3 == 0) {
				Supe.hurt(player, ray.entity(), 8.5f);
				ray.entity().igniteForSeconds(6);
				if (ray.entity() instanceof LivingEntity living && living.isDeadOrDying()) {
					Supe.blood(level, end, 2.5f);
				}
			}
		} else if (ray.block() != null && s.beamTicks % 4 == 0) {
			// the beam carves through walls
			BlockPos center = ray.block();
			for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
				if (p.distSqr(center) <= 1.5 && Supe.breakable(level, p, 6)) {
					level.destroyBlock(p, level.getRandom().nextFloat() < 0.3f, player, 512);
				}
			}
		}
	}

	private static void endBeam(PlayerSession s) {
		int used = s.beamTicks;
		s.beamTicks = 0;
		s.cool(0, Math.max(80, used * 4));
	}

	/** The nuke. Bigger the longer it was charged; strips the powers of other supes caught in it. */
	private static void detonate(ServerPlayer player, PlayerSession s) {
		int charge = s.nukeCharge;
		s.nukeCharge = 0;
		ServerLevel level = player.level();
		if (charge < 15) {
			return;
		}
		float frac = Math.min(1f, charge / (float) MAX_CHARGE);
		float radius = 6 + 12 * frac;
		Vec3 c = Supe.chest(player);
		s.cool(1, 760);

		ModNetworking.sendFx(level, c, new FxPayload(FxPayload.NUKE, player.getId(), (float) c.x, (float) c.y, (float) c.z, radius, 0, 0));
		ModNetworking.sendFx(level, c, new FxPayload(FxPayload.SHAKE, player.getId(), (float) c.x, (float) c.y, (float) c.z, 3f * frac + 1f, 0, 0));
		Supe.sound(level, c, SoundEvents.GENERIC_EXPLODE, 4.0f, 0.5f);
		Supe.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 0.6f);
		level.explode(player, c.x, c.y, c.z, 2.5f + 4.5f * frac, Level.ExplosionInteraction.MOB);

		for (LivingEntity e : Supe.livingAround(level, c, radius, player)) {
			double d = Math.sqrt(e.distanceToSqr(c));
			float power = (float) (1 - d / radius);
			Supe.hurt(player, e, 15 + 34 * power);
			e.igniteForSeconds(8);
			Supe.push(e, e.position().subtract(c).normalize().scale(1.5 + 2.5 * power).add(0, 0.6, 0));
			if (e instanceof ServerPlayer other && PowerAttachments.powerOf(other) != Power.NONE) {
				PowerManager.setPower(other, Power.NONE);
				other.sendSystemMessage(Component.translatable("message.theboys.burned_out").withStyle(ChatFormatting.GOLD));
			}
		}
		// he is spent afterwards
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 1));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
	}

	private static void throwShield(ServerPlayer player, PlayerSession s) {
		if (!s.ready(2)) {
			PowerManager.notReady(player, s, 2);
			return;
		}
		InteractionHand hand = null;
		if (player.getMainHandItem().is(ModItems.SOLDIER_BOY_SHIELD)) hand = InteractionHand.MAIN_HAND;
		else if (player.getOffhandItem().is(ModItems.SOLDIER_BOY_SHIELD)) hand = InteractionHand.OFF_HAND;
		if (hand == null) {
			player.sendSystemMessage(Component.translatable("message.theboys.need_shield").withStyle(ChatFormatting.RED), true);
			return;
		}
		ItemStack stack = player.getItemInHand(hand).copy();
		player.setItemInHand(hand, ItemStack.EMPTY);
		ThrownShield shield = new ThrownShield(player.level(), player, stack);
		shield.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 2.2f, 0);
		player.level().addFreshEntity(shield);
		Supe.sound(player.level(), player.getEyePosition(), SoundEvents.TRIDENT_THROW, 1.0f, 0.7f);
		s.cool(2, 50);
	}

	/** Shield charge: a short dash that bowls over everything in the way. */
	private static void charge(ServerPlayer player, PlayerSession s) {
		if (!s.ready(3)) {
			PowerManager.notReady(player, s, 3);
			return;
		}
		s.cool(3, 120);
		Vec3 look = player.getLookAngle();
		Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
		Supe.push(player, dir.scale(2.4).add(0, 0.25, 0));
		Supe.sound(player.level(), player.position(), SoundEvents.IRON_GOLEM_ATTACK, 1.2f, 0.8f);
		ServerLevel level = player.level();
		Vec3 from = player.position();
		for (LivingEntity e : Supe.livingAround(level, from.add(dir.scale(3)), 3.5, player)) {
			Supe.hurt(player, e, 12);
			Supe.push(e, dir.scale(2.2).add(0, 0.5, 0));
			Supe.sound(level, e.position(), SoundEvents.SHIELD_BLOCK, 1.2f, 0.7f);
		}
	}

	static void stop(ServerPlayer player, PlayerSession s) {
		s.beamTicks = 0;
		s.nukeCharge = 0;
	}
}
