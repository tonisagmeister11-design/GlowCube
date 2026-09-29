package de.gtacity.entity;

import de.gtacity.registry.ModSounds;
import de.gtacity.registry.ModEntities;
import de.gtacity.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Hand grenade: bounces around and explodes after three seconds. */
public class GrenadeEntity extends ThrowableItemProjectile {
    private static final int FUSE = 60;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
        super(type, level);
    }

    public GrenadeEntity(Level level, LivingEntity owner) {
        super(ModEntities.GRENADE, owner, level, new ItemStack(ModItems.GRENADE));
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.GRENADE;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 0, 0.01, 0);
        } else if (tickCount >= FUSE && level() instanceof ServerLevel level) {
            level.explode(this, getX(), getY(), getZ(), 3.5F, false, Level.ExplosionInteraction.NONE);
            ModSounds.boom(level, getX(), getY(), getZ());
            NpcEntity.scare(level, position(), 25.0, getOwner() instanceof LivingEntity l ? l : null);
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        Vec3 v = getDeltaMovement();
        Vec3 bounced = switch (result.getDirection().getAxis()) {
            case X -> new Vec3(-v.x, v.y, v.z);
            case Y -> new Vec3(v.x, -v.y, v.z);
            case Z -> new Vec3(v.x, v.y, -v.z);
        };
        setDeltaMovement(bounced.scale(0.35));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        setDeltaMovement(getDeltaMovement().scale(-0.2));
    }
}
