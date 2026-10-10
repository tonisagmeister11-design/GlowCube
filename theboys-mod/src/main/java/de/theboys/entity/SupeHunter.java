package de.theboys.entity;

import java.util.EnumSet;

import de.theboys.net.FxPayload;
import de.theboys.net.ModNetworking;
import de.theboys.power.Power;
import de.theboys.power.PowerAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A supe hunter: an ordinary-looking person from a hunter camp who fights supes with a rifle loaded with
 * V-laced bullets. The bullets ignore armour (so even Homelander feels them) and fly as far as the
 * hunters can see. They go for every player with a power within sight - also when he only flies past.
 */
public class SupeHunter extends Monster {
	public static final double SIGHT = 56;
	private static final float BULLET_DAMAGE = 6f;

	public int aimTicks;
	private int shotCooldown = 30 + (int) (Math.random() * 30);

	public SupeHunter(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 44)
				.add(Attributes.ARMOR, 8)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, SIGHT + 8)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 4);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new HuntGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		goalSelector.addGoal(6, new RandomLookAroundGoal(this));
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	/** A player is hunted when he has a power (or has hurt a hunter). */
	public static boolean isPrey(LivingEntity e) {
		return e instanceof Player p && !p.isCreative() && !p.isSpectator() && PowerAttachments.powerOf(p) != Power.NONE;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (aimTicks > 0) aimTicks--;
		LivingEntity target = getTarget();
		if (target != null && (!target.isAlive() || distanceTo(target) > SIGHT + 16 || target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			setTarget(null);
			target = null;
		}
		if (tickCount % 10 == 0 && target == null) {
			// looks for a supe nearby - flying past a camp is enough
			Player best = null;
			double bestD = SIGHT * SIGHT;
			for (Player p : level.players()) {
				if (!isPrey(p)) continue;
				double d = p.distanceToSqr(this);
				if (d < bestD && hasLineOfSight(p)) {
					bestD = d;
					best = p;
				}
			}
			if (best != null) alert(level, best);
		}
	}

	/** The whole camp turns on the one one of them has seen. */
	private void alert(ServerLevel level, LivingEntity prey) {
		setTarget(prey);
		for (SupeHunter h : level.getEntitiesOfClass(SupeHunter.class, getBoundingBox().inflate(40), h -> h != this && h.getTarget() == null)) {
			h.setTarget(prey);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof SupeHunter) && attacker.isAlive()
				&& !(attacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			alert(level, attacker);
		}
		return hurt;
	}

	public boolean isAiming() {
		return aimTicks > 0 || getTarget() != null;
	}

	/** Fires the rifle at the target: a hitscan bullet that ignores armour when it hits a supe. */
	void shoot(ServerLevel level, LivingEntity target) {
		Vec3 from = getEyePosition().add(getLookAngle().scale(0.6)).add(0, -0.15, 0);
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.62, 0);
		double dist = from.distanceTo(aim);
		// not a perfect shot: the farther away, the more they miss; supes that are moving fast are hard to hit
		double spread = 0.025 * dist + 0.12;
		Vec3 dir = aim.subtract(from).normalize().add(random.nextGaussian() * spread / Math.max(8, dist), random.nextGaussian() * spread / Math.max(8, dist),
				random.nextGaussian() * spread / Math.max(8, dist)).normalize();
		Vec3 end = from.add(dir.scale(SIGHT + 10));
		BlockHitResult block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, this, from, stop, new AABB(from, stop).inflate(1.0),
				e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof SupeHunter) && !e.isSpectator() && e.isPickable(), 0.35f);
		if (hit != null) {
			stop = hit.getLocation();
			Entity victim = hit.getEntity();
			boolean supe = victim instanceof Player p && PowerAttachments.powerOf(p) != Power.NONE;
			// V-laced rounds: against supes they cut through any armour
			DamageSource src = supe ? level.damageSources().indirectMagic(this, this) : level.damageSources().mobProjectile(this, this);
			if (victim.hurtServer(level, src, BULLET_DAMAGE)) {
				level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 8, 0.1, 0.1, 0.1, 0.2);
				if (victim instanceof ServerPlayer sp) {
					sp.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.HOSTILE, 0.8f, 1.2f);
				}
			}
		} else {
			level.sendParticles(ParticleTypes.SMOKE, stop.x, stop.y, stop.z, 3, 0.05, 0.05, 0.05, 0.01);
		}
		aimTicks = 14;
		ModNetworking.sendFx(level, from, new FxPayload(FxPayload.TRACER, getId(), (float) from.x, (float) from.y, (float) from.z,
				(float) stop.x, (float) stop.y, (float) stop.z));
		level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.4f, 0.7f + random.nextFloat() * 0.2f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.0f, 1.4f);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	/** Chases the target to a good shooting distance, keeps moving a little and fires on a short rhythm. */
	private static final class HuntGoal extends Goal {
		private final SupeHunter hunter;
		private int strafe;
		private int dir = 1;

		HuntGoal(SupeHunter hunter) {
			this.hunter = hunter;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = hunter.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void tick() {
			LivingEntity t = hunter.getTarget();
			if (t == null || !(hunter.level() instanceof ServerLevel level)) return;
			hunter.getLookControl().setLookAt(t, 40f, 40f);
			double d = hunter.distanceTo(t);
			boolean sees = hunter.hasLineOfSight(t);
			if (!sees || d > 26) {
				// closes in until he has a shot (flying supes: as close as the ground allows)
				hunter.getNavigation().moveTo(t.getX(), t.getY(), t.getZ(), 1.15);
			} else if (d < 9) {
				// too close: steps back and shoots
				Vec3 away = hunter.position().subtract(t.position()).normalize().scale(6);
				hunter.getNavigation().moveTo(hunter.getX() + away.x, hunter.getY(), hunter.getZ() + away.z, 1.1);
			} else {
				// moves sideways so he is not an easy target
				if (--strafe <= 0) {
					strafe = 20 + hunter.random.nextInt(30);
					dir = hunter.random.nextBoolean() ? 1 : -1;
				}
				Vec3 side = t.position().subtract(hunter.position()).cross(new Vec3(0, 1, 0)).normalize().scale(3 * dir);
				hunter.getNavigation().moveTo(hunter.getX() + side.x, hunter.getY(), hunter.getZ() + side.z, 0.9);
			}
			if (hunter.shotCooldown > 0) hunter.shotCooldown--;
			if (sees && d < SIGHT && hunter.shotCooldown <= 0) {
				hunter.shotCooldown = 26 + hunter.random.nextInt(24);
				hunter.shoot(level, t);
			}
		}

		@Override
		public void stop() {
			hunter.getNavigation().stop();
		}
	}
}
