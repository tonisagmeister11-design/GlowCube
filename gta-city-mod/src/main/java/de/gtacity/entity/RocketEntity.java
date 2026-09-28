package de.gtacity.entity;

import de.gtacity.registry.ModEntities;
import de.gtacity.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** RPG rocket: flies straight and explodes on impact. */
public class RocketEntity extends ThrowableItemProjectile {
    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
    }

    public RocketEntity(Level level, LivingEntity owner) {
        super(ModEntities.ROCKET, owner, level, new ItemStack(ModItems.ROCKET));
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.ROCKET;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 0, 0.02, 0);
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
        } else if (tickCount > 200) {
            discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel level) {
            level.explode(this, getX(), getY(), getZ(), 4.0F, true, Level.ExplosionInteraction.NONE);
            NpcEntity.scare(level, position(), 30.0, getOwner() instanceof LivingEntity l ? l : null);
            discard();
        }
    }
}
