package de.theboys.power;

import java.util.List;
import java.util.function.Predicate;

import de.theboys.mixin.EntityAccessor;
import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for all powers: ray casts, damage, gore, sounds. */
public final class Supe {
	private Supe() {
	}

	/** Result of a ray cast: the point, and the entity if one was hit before any block. */
	public record Ray(Vec3 start, Vec3 end, Entity entity, BlockPos block) {
	}

	public static Ray ray(ServerPlayer player, Vec3 start, double range) {
		return ray(player, start, range, 0.3f);
	}

	/** Ray cast with a custom entity margin (bigger = more forgiving aim). */
	public static Ray ray(ServerPlayer player, Vec3 start, double range, float margin) {
		Vec3 dir = player.getLookAngle();
		Vec3 end = start.add(dir.scale(range));
		ServerLevel level = player.level();
		BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 blockEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
		AABB box = new AABB(start, blockEnd).inflate(1.0);
		Predicate<Entity> pred = e -> e != player && e.isAlive() && e.isPickable() && !e.isSpectator();
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, start, blockEnd, box, pred, margin);
		if (entityHit != null) {
			return new Ray(start, entityHit.getLocation(), entityHit.getEntity(), null);
		}
		return new Ray(start, blockEnd, null, blockHit.getType() == HitResult.Type.MISS ? null : blockHit.getBlockPos());
	}

	public static Vec3 chest(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.72, 0);
	}

	/** Damages ignoring the usual half-second invulnerability, so beams can hit every few ticks. */
	public static boolean hurt(ServerPlayer attacker, Entity target, float amount) {
		((EntityAccessor) target).theboys$setInvulnerableTime(0);
		ServerLevel level = attacker.level();
		return target.hurtServer(level, level.damageSources().playerAttack(attacker), amount);
	}

	/** A blow of a quick combo: also skips the living target's hurt cooldown, so every cut counts in full. */
	public static boolean hurtNow(ServerPlayer attacker, Entity target, float amount) {
		if (target instanceof LivingEntity living) living.damageCooldownTime = 0;
		return hurt(attacker, target, amount);
	}

	public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	public static void sound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** A spray of blood. Strength 1 = a hit, 3+ = a body bursting. */
	public static void blood(ServerLevel level, Vec3 pos, float strength) {
		int n = (int) (25 * strength);
		level.sendParticles(new DustParticleOptions(0x8A0303, 2.2f), pos.x, pos.y, pos.z, n, 0.35 * strength, 0.45 * strength, 0.35 * strength, 0.25);
		level.sendParticles(new DustParticleOptions(0x5A0000, 1.6f), pos.x, pos.y, pos.z, n / 2, 0.5 * strength, 0.5 * strength, 0.5 * strength, 0.4);
		if (strength >= 2) {
			BlockState gore = Blocks.REDSTONE_BLOCK.defaultBlockState();
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, gore), pos.x, pos.y, pos.z, (int) (20 * strength), 0.4, 0.5, 0.4, 0.6);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState()), pos.x, pos.y, pos.z, (int) (12 * strength), 0.4, 0.5, 0.4, 0.5);
			ModNetworking.sendFx(level, pos, new FxPayload(FxPayload.GORE, -1, (float) pos.x, (float) pos.y, (float) pos.z, strength, 0, 0));
			sound(level, pos, SoundEvents.SLIME_DEATH, 1.2f, 0.6f);
			sound(level, pos, SoundEvents.HONEY_BLOCK_BREAK, 1.5f, 0.5f);
		}
	}

	/** Kills a living target in a burst of blood (A-Train running through someone, Butcher ripping someone apart). */
	public static void burst(ServerPlayer attacker, LivingEntity target, float damage) {
		Vec3 at = target.position().add(0, target.getBbHeight() / 2, 0);
		((EntityAccessor) target).theboys$setInvulnerableTime(0);
		target.hurtServer(attacker.level(), attacker.level().damageSources().playerAttack(attacker), damage);
		blood(attacker.level(), at, target.isDeadOrDying() ? 3.5f : 1.5f);
	}

	public static List<LivingEntity> livingAround(ServerLevel level, Vec3 center, double radius, Entity except) {
		AABB box = new AABB(center, center).inflate(radius);
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != except && e.isAlive() && !e.isSpectator()
				&& e.distanceToSqr(center) <= radius * radius);
	}

	/** Pushes an entity and makes sure the client of a pushed player hears about it. */
	public static void push(Entity e, Vec3 velocity) {
		setVelocity(e, e.getDeltaMovement().add(velocity));
	}

	/** Sets the velocity; a player moves himself on his client, so he is sent the new motion directly. */
	public static void setVelocity(Entity e, Vec3 velocity) {
		e.setDeltaMovement(velocity);
		e.needsSync = true;
		if (e instanceof ServerPlayer p) {
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
		}
	}

	/** Blocks a beam/tendril may break: not air, not unbreakable, not harder than obsidian. */
	public static boolean breakable(ServerLevel level, BlockPos pos, float maxHardness) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
		float h = state.getDestroySpeed(level, pos);
		return h >= 0 && h <= maxHardness;
	}
}
