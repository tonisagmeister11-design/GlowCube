package de.theboys.entity;

import java.util.HashSet;
import java.util.Set;

import de.theboys.power.Supe;
import de.theboys.registry.ModEntities;
import de.theboys.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Soldier Boy's thrown shield. Flies out, ricochets between up to three targets and comes back
 * to his hand like a boomerang.
 */
public class ThrownShield extends ThrowableItemProjectile {
	private static final int MAX_OUT = 22;
	private final Set<Integer> hit = new HashSet<>();
	private boolean returning;
	private int bounces;

	public ThrownShield(EntityType<? extends ThrownShield> type, Level level) {
		super(type, level);
	}

	public ThrownShield(Level level, LivingEntity owner, ItemStack stack) {
		super(ModEntities.THROWN_SHIELD, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.SOLDIER_BOY_SHIELD;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		Entity owner = getOwner();
		if (owner == null || !owner.isAlive()) {
			dropAsItem(level);
			return;
		}
		if (!returning && tickCount > MAX_OUT) {
			returning = true;
		}
		if (returning) {
			Vec3 to = owner.getEyePosition().subtract(position());
			if (to.length() < 1.6) {
				giveBack(level, owner);
				return;
			}
			setDeltaMovement(to.normalize().scale(Math.min(2.4, 0.6 + tickCount * 0.04)));
			// hit things on the way back too
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.4), e -> e != owner && e.isAlive() && !hit.contains(e.getId()))) {
				strike(level, e);
			}
		}
		if (tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0);
		}
		if (tickCount > 200) {
			giveBack(level, owner);
		}
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && !hit.contains(entity.getId()) && !returning;
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (level() instanceof ServerLevel level && result.getEntity() instanceof LivingEntity target) {
			strike(level, target);
			// ricochet to the next target
			Entity owner = getOwner();
			LivingEntity next = null;
			double best = Double.MAX_VALUE;
			if (bounces < 3) {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(9),
						e -> e != owner && e.isAlive() && !hit.contains(e.getId()))) {
					double d = e.distanceToSqr(this);
					if (d < best) {
						best = d;
						next = e;
					}
				}
			}
			if (next != null) {
				bounces++;
				Vec3 to = next.position().add(0, next.getBbHeight() / 2, 0).subtract(position());
				setDeltaMovement(to.normalize().scale(2.0));
			} else {
				returning = true;
			}
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		returning = true;
		playSound(SoundEvents.SHIELD_BLOCK.value(), 1.0f, 1.2f);
	}

	private void strike(ServerLevel level, LivingEntity target) {
		hit.add(target.getId());
		Entity owner = getOwner();
		if (owner instanceof ServerPlayer player) {
			Supe.hurt(player, target, 12);
		} else {
			target.hurtServer(level, level.damageSources().generic(), 12);
		}
		Supe.push(target, getDeltaMovement().normalize().scale(0.9).add(0, 0.3, 0));
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.8f);
	}

	private void giveBack(ServerLevel level, Entity owner) {
		ItemStack stack = getItem().copy();
		if (owner instanceof ServerPlayer player) {
			if (player.getMainHandItem().isEmpty()) {
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
				stack = ItemStack.EMPTY;
			} else if (player.getOffhandItem().isEmpty()) {
				player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, stack);
				stack = ItemStack.EMPTY;
			} else if (player.getInventory().add(stack) && stack.isEmpty()) {
				stack = ItemStack.EMPTY;
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.7f);
		}
		if (!stack.isEmpty()) {
			level.addFreshEntity(new ItemEntity(level, owner.getX(), owner.getY(), owner.getZ(), stack));
		}
		discard();
	}

	private void dropAsItem(ServerLevel level) {
		level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), getItem().copy()));
		discard();
	}
}
