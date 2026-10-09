package de.theboys.power;

import java.util.ArrayList;
import java.util.List;

import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Starlight: she soaks up light and electricity and fires it back out of her hands.
 * R (hold) light blast, G flight (needs charge), C blinding flash, X drain the lights around her,
 * Sneak+R supernova. Charges in bright light, from lamps and from lightning.
 */
public final class Starlight {
	public static final int MAX_CHARGE = 1000;
	public static final int FLY_MIN = 100;
	private static final int FLASH_COST = 220;
	private static final int NOVA_COST = 650;

	private Starlight() {
	}

	static void key(ServerPlayer player, PlayerSession s, int slot, boolean pressed) {
		if (!pressed) return;
		switch (slot) {
			case 0 -> {
				if (player.isShiftKeyDown()) supernova(player, s);
				else if (!s.ready(0)) PowerManager.notReady(player, s, 0);
				else if (s.charge < 10) empty(player);
				else Supe.sound(player.level(), player.getEyePosition(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.8f);
			}
			case 1 -> {
				if (!s.flying && s.charge < FLY_MIN) {
					empty(player);
					return;
				}
				Homelander.toggleFlight(player, s);
				if (s.flying) {
					player.getAbilities().setFlyingSpeed(0.1f);
					player.onUpdateAbilities();
				}
			}
			case 2 -> flash(player, s);
			case 3 -> absorb(player, s);
			default -> { }
		}
	}

	private static void empty(ServerPlayer player) {
		player.sendSystemMessage(Component.translatable("message.theboys.starlight_empty").withStyle(ChatFormatting.GOLD), true);
		Supe.sound(player.level(), player.position(), SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.6f);
	}

	static int tick(ServerPlayer player, PlayerSession s) {
		ServerLevel level = player.level();
		ActiveState state = PowerAttachments.active(player);
		if (s.novaCool > 0) s.novaCool--;

		// she drinks the light around her
		int light = level.getMaxLocalRawBrightness(player.blockPosition());
		int gain = light >= 13 ? 2 : light >= 9 ? 1 : 0;
		if (level.isThundering() && level.canSeeSky(player.blockPosition())) gain += 3;
		if (player.tickCount % 2 == 0) s.charge = Math.min(MAX_CHARGE, s.charge + gain);

		// light blast
		boolean firing = s.keyDown[0] && s.ready(0) && s.charge >= 4 && !player.isShiftKeyDown();
		if (firing) {
			s.charge -= 4;
			blast(player, level);
			if (s.charge < 4) s.cool(0, 40);
		}
		state = state.with(ActiveState.HAND_BEAM, firing);

		// flight costs charge; when she runs dry she floats down
		boolean flying = s.flying && player.getAbilities().flying;
		if (s.flying) {
			player.resetFallDistance();
			if (flying && player.tickCount % 2 == 0) s.charge--;
			if (s.charge <= 0) {
				s.charge = 0;
				Homelander.toggleFlight(player, s);
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
				empty(player);
			}
		}
		state = state.with(ActiveState.FLYING, flying);
		state = state.with(ActiveState.CHARGED, s.charge >= 600);
		PowerAttachments.setActive(player, state.withCharge(s.charge));
		return s.charge;
	}

	/** Golden light out of both hands: burns, blinds a little and pushes back. */
	private static void blast(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Supe.Ray ray = Supe.ray(player, eye, 40, 0.5f);
		Vec3 end = ray.end();
		if (player.tickCount % 5 == 0) Supe.sound(level, eye, SoundEvents.BEACON_AMBIENT, 0.7f, 1.9f);
		level.sendParticles(new DustParticleOptions(0xFFE9A8, 1.6f), end.x, end.y, end.z, 5, 0.15, 0.15, 0.15, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 2, 0.1, 0.1, 0.1, 0.06);
		if (ray.entity() != null && player.tickCount % 3 == 0) {
			Supe.hurt(player, ray.entity(), 5.5f);
			Supe.push(ray.entity(), player.getLookAngle().scale(0.35).add(0, 0.08, 0));
			if (ray.entity() instanceof LivingEntity living) {
				living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), player);
				if (living.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)) living.igniteForSeconds(3);
				if (living.isDeadOrDying()) Supe.blood(level, end, 1.5f);
			}
		}
	}

	/** A flash as bright as the sun: everyone who can see her is blinded and staggers. */
	private static void flash(ServerPlayer player, PlayerSession s) {
		if (!s.ready(2)) {
			PowerManager.notReady(player, s, 2);
			return;
		}
		if (s.charge < FLASH_COST) {
			empty(player);
			return;
		}
		s.charge -= FLASH_COST;
		s.cool(2, 300);
		ServerLevel level = player.level();
		Vec3 c = player.getEyePosition();
		double radius = 14;
		for (LivingEntity e : Supe.livingAround(level, c, radius, player)) {
			if (!e.hasLineOfSight(player)) continue;
			float k = (float) (1 - e.distanceTo(player) / (radius + 1));
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100 + (int) (60 * k), 0), player);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), player);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1), player);
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0), player);
			boolean undead = e.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
			Supe.hurt(player, e, (undead ? 10f : 3f) + 4f * k);
			if (undead) e.igniteForSeconds(5);
		}
		ModNetworking.sendFx(level, c, new FxPayload(FxPayload.FLASH, player.getId(), (float) c.x, (float) c.y, (float) c.z, (float) radius, 0, 0));
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 80, 1.5, 1.5, 1.5, 0.4);
		Supe.sound(level, c, SoundEvents.BEACON_ACTIVATE, 2.0f, 2.0f);
		Supe.sound(level, c, SoundEvents.FIREWORK_ROCKET_BLAST, 2.0f, 0.6f);
	}

	/**
	 * Drains every light source around her: the energy streams into her hands, lamps go dark,
	 * candles and campfires go out. Under an open daytime sky she drinks the sunlight.
	 */
	private static void absorb(ServerPlayer player, PlayerSession s) {
		if (!s.ready(3)) {
			PowerManager.notReady(player, s, 3);
			return;
		}
		ServerLevel level = player.level();
		BlockPos me = player.blockPosition();
		List<BlockPos> sources = new ArrayList<>();
		int gained = 0;
		for (BlockPos p : BlockPos.betweenClosed(me.offset(-10, -6, -10), me.offset(10, 6, 10))) {
			if (sources.size() >= 16) break;
			BlockState st = level.getBlockState(p);
			int emission = st.getLightEmission();
			if (emission < 7) continue;
			if (p.distSqr(me) > 110) continue;
			BlockPos im = p.immutable();
			sources.add(im);
			gained += 30 + emission * 5;
			if (st.hasProperty(BlockStateProperties.LIT) && (st.getBlock() instanceof AbstractCandleBlock
					|| st.getBlock() instanceof CampfireBlock || st.getBlock() instanceof RedstoneLampBlock)) {
				level.setBlock(im, st.setValue(BlockStateProperties.LIT, false), 3);
			}
		}
		if (level.isBrightOutside() && level.canSeeSky(me)) {
			gained += 220;
			Vec3 sky = player.position().add(0, 14, 0);
			ModNetworking.sendFx(level, sky, new FxPayload(FxPayload.LIGHT_STREAM, player.getId(), (float) sky.x, (float) sky.y, (float) sky.z, 0, 0, 0));
		}
		if (level.isThundering()) gained += 200;
		if (gained == 0) {
			player.sendSystemMessage(Component.translatable("message.theboys.starlight_dark").withStyle(ChatFormatting.GRAY), true);
			s.cool(3, 40);
			return;
		}
		s.cool(3, 200);
		s.charge = Math.min(MAX_CHARGE, s.charge + gained);
		for (BlockPos p : sources) {
			Vec3 c = Vec3.atCenterOf(p);
			ModNetworking.sendFx(level, c, new FxPayload(FxPayload.LIGHT_STREAM, player.getId(), (float) c.x, (float) c.y, (float) c.z, 0, 0, 0));
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.02);
		}
		Supe.sound(level, player.position(), SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.7f);
		Supe.sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 1.4f);
	}

	/** Everything she has stored in one blast: a sphere of light that burns and throws everything away. */
	private static void supernova(ServerPlayer player, PlayerSession s) {
		if (s.novaCool > 0) {
			player.sendSystemMessage(Component.translatable("message.theboys.cooldown", Component.translatable("ability.theboys.starlight.nova"),
					String.format("%.1f", s.novaCool / 20f)).withStyle(ChatFormatting.RED), true);
			return;
		}
		if (s.charge < NOVA_COST) {
			empty(player);
			return;
		}
		float power = s.charge / (float) MAX_CHARGE;
		s.charge = 0;
		s.novaCool = 900;
		ServerLevel level = player.level();
		Vec3 c = player.position().add(0, 1, 0);
		double radius = 7 + 4 * power;
		for (LivingEntity e : Supe.livingAround(level, c, radius, player)) {
			float k = (float) (1 - e.distanceTo(player) / (radius + 1));
			Supe.hurt(player, e, (8f + 18f * k) * (0.7f + 0.3f * power));
			e.igniteForSeconds(4);
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0), player);
			Supe.push(e, e.position().subtract(player.position()).normalize().scale(1.0 + 1.8 * k).add(0, 0.5 + 0.4 * k, 0));
			if (e.isDeadOrDying()) Supe.blood(level, e.position().add(0, e.getBbHeight() / 2, 0), 2.5f);
		}
		ModNetworking.sendFx(level, c, new FxPayload(FxPayload.NOVA, player.getId(), (float) c.x, (float) c.y, (float) c.z, (float) radius, 0xFFE9A8, 0));
		ModNetworking.sendFx(level, c, new FxPayload(FxPayload.FLASH, player.getId(), (float) c.x, (float) c.y, (float) c.z, (float) radius * 1.5f, 0, 0));
		ModNetworking.sendFx(level, c, new FxPayload(FxPayload.SHAKE, player.getId(), (float) c.x, (float) c.y, (float) c.z, 2.0f, 0, 0));
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 160, radius * 0.4, radius * 0.3, radius * 0.4, 0.6);
		Supe.sound(level, c, SoundEvents.GENERIC_EXPLODE, 2.0f, 1.5f);
		Supe.sound(level, c, SoundEvents.BEACON_ACTIVATE, 2.0f, 0.5f);
		Supe.sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0f, 1.8f);
	}

	/** Lightning does not hurt her: she drinks it (true = the hit is absorbed). */
	public static boolean absorbLightning(ServerPlayer player) {
		PlayerSession s = PowerManager.session(player);
		s.charge = MAX_CHARGE;
		player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 1, 0.5, 0.2);
		return true;
	}

	static void stop(ServerPlayer player, PlayerSession s) {
		if (s.flying) {
			s.flying = false;
			Homelander.endFlight(player);
			player.onUpdateAbilities();
		}
	}
}
