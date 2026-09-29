package de.gtacity.entity;

import de.gtacity.registry.ModSounds;
import de.gtacity.gameplay.WantedSystem;
import de.gtacity.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Police helicopter for five star chases. It flies in from far away, circles above the wanted player and carries
 * two SWAT marksmen in the open side doors. It can be shot down; when the stars drop it flies off again.
 */
public class HelicopterEntity extends Entity {
    private static final EntityDataAccessor<Float> HEALTH =
            SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.FLOAT);
    public static final float MAX_HEALTH = 160.0F;
    private static final double CRUISE = 0.9;
    private static final double HOVER_HEIGHT = 22.0;
    private static final double ORBIT = 16.0;

    public float rotor;
    public float prevRotor;

    private @Nullable UUID target;
    private boolean leaving;
    private int leaveTicks;
    private boolean destroyed;

    public HelicopterEntity(EntityType<? extends HelicopterEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        setNoGravity(true);
    }

    // ------------------------------------------------------------------ spawning

    /** Makes sure one helicopter is chasing the player (called by the dispatcher at five stars). */
    public static void ensureFor(ServerLevel level, ServerPlayer player) {
        for (HelicopterEntity heli : level.getEntitiesOfClass(HelicopterEntity.class,
                player.getBoundingBox().inflate(160.0))) {
            if (player.getUUID().equals(heli.target) && !heli.leaving) {
                return;
            }
        }
        HelicopterEntity heli = ModEntities.HELICOPTER.create(level, EntitySpawnReason.EVENT);
        if (heli == null) {
            return;
        }
        double angle = player.getRandom().nextDouble() * Math.PI * 2;
        double x = player.getX() + Math.cos(angle) * 90.0;
        double z = player.getZ() + Math.sin(angle) * 90.0;
        double y = Math.max(player.getY() + 45.0,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + 10.0);
        heli.snapTo(x, y, z, (float) Math.toDegrees(-angle) + 90.0F, 0.0F);
        heli.target = player.getUUID();
        level.addFreshEntity(heli);
        for (int i = 0; i < 2; i++) {
            PoliceEntity gunner = ModEntities.POLICE.create(level, EntitySpawnReason.EVENT);
            if (gunner == null) {
                continue;
            }
            gunner.snapTo(x, heli.getY(), z, 0.0F, 0.0F);
            gunner.finalizeSpawn(level, level.getCurrentDifficultyAt(heli.blockPosition()), EntitySpawnReason.EVENT,
                    null);
            gunner.setSwat(true);
            level.addFreshEntity(gunner);
            gunner.startRiding(heli);
            gunner.setTarget(player);
        }
    }

    /** True if a helicopter chasing this player has it in sight - keeps the stars from fading. */
    public static boolean seesPlayer(ServerLevel level, ServerPlayer player) {
        for (HelicopterEntity heli : level.getEntitiesOfClass(HelicopterEntity.class,
                player.getBoundingBox().inflate(90.0))) {
            if (heli.leaving || heli.destroyed) {
                continue;
            }
            HitResult hit = level.clip(new ClipContext(heli.position().add(0, -1.0, 0), player.getEyePosition(),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, heli));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HEALTH, MAX_HEALTH);
    }

    public float getHealth() {
        return entityData.get(HEALTH);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity entity) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < 2;
    }

    /** The two marksmen sit in the open side doors. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int index = getPassengers().indexOf(passenger);
        float side = index <= 0 ? 1.15F : -1.15F;
        return new Vec3(side, 0.2, 0.3).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    // ------------------------------------------------------------------ damage

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableToBase(source) || destroyed) {
            return false;
        }
        if (source.getEntity() instanceof Player p && p.getAbilities().instabuild && p.isShiftKeyDown()) {
            discardAll();
            return true;
        }
        damage(level, amount);
        return true;
    }

    public void damage(ServerLevel level, float amount) {
        entityData.set(HEALTH, Math.max(0.0F, getHealth() - amount));
        if (getHealth() <= 0.0F) {
            destroyed = true;
            level.explode(this, getX(), getY(), getZ(), 5.0F, true, Level.ExplosionInteraction.NONE);
            ModSounds.boom(level, getX(), getY(), getZ());
            discardAll();
        }
    }

    private void discardAll() {
        for (Entity passenger : List.copyOf(getPassengers())) {
            passenger.discard();
        }
        discard();
    }

    // ------------------------------------------------------------------ flight

    @Override
    public void tick() {
        super.tick();
        prevRotor = rotor;
        rotor += 55.0F;
        if (level() instanceof ServerLevel level) {
            fly(level);
        } else if (getHealth() < MAX_HEALTH * 0.4F && random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.2, getZ(), 0, 0.08, 0);
        }
    }

    /** Lowest height that clears every roof between here and the goal (skyscrapers reach almost 300). */
    private double safeAltitude(ServerLevel level, Vec3 goal) {
        Vec3 flat = new Vec3(goal.x - getX(), 0, goal.z - getZ());
        double length = flat.length();
        Vec3 dir = length < 1.0E-3 ? Vec3.ZERO : flat.scale(1.0 / length);
        int highest = level.getMinY();
        for (double d = 0; d <= Math.min(length, 40.0) + 8.0; d += 4.0) {
            for (int side = -1; side <= 1; side++) {
                double x = getX() + dir.x * d - dir.z * side * 3.0, z = getZ() + dir.z * d + dir.x * side * 3.0;
                highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x),
                        Mth.floor(z)));
            }
        }
        highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(goal.x),
                Mth.floor(goal.z)));
        return highest + 7.0;
    }

    private void fly(ServerLevel level) {
        ServerPlayer player = target == null ? null : level.getServer().getPlayerList().getPlayer(target);
        if (!leaving && (player == null || !player.isAlive() || player.level() != level
                || WantedSystem.level(player) < 5)) {
            leaving = true;
        }
        Vec3 goal;
        if (leaving) {
            Vec3 away = player == null ? Vec3.directionFromRotation(0, getYRot())
                    : position().subtract(player.position()).multiply(1, 0, 1).normalize();
            goal = position().add(away.scale(20.0)).add(0, 3.0, 0);
            if (++leaveTicks > 20 * 15) {
                discardAll();
                return;
            }
        } else {
            // Circle slowly around the player at a safe height.
            double t = tickCount * 0.012;
            goal = player.position().add(Math.cos(t) * ORBIT, HOVER_HEIGHT, Math.sin(t) * ORBIT);
            for (Entity passenger : getPassengers()) {
                if (passenger instanceof PoliceEntity cop && cop.getTarget() != player) {
                    cop.setTarget(player);
                }
            }
        }
        goal = new Vec3(goal.x, Math.max(goal.y, safeAltitude(level, goal)), goal.z);
        Vec3 to = goal.subtract(position());
        double dist = to.length();
        Vec3 wantVelocity = dist < 0.5 ? Vec3.ZERO : to.scale(Math.min(CRUISE, dist * 0.08) / dist);
        Vec3 velocity = getDeltaMovement().lerp(wantVelocity, 0.08);
        setDeltaMovement(velocity);
        move(MoverType.SELF, velocity);
        if (horizontalCollision) {
            setDeltaMovement(velocity.add(0, 0.3, 0));
        }
        double speed = velocity.horizontalDistance();
        if (speed > 0.05) {
            float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 3.0F));
        } else if (player != null) {
            Vec3 look = player.position().subtract(position());
            float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 2.0F));
        }
        setXRot((float) Mth.clamp(speed * 18.0, 0.0, 15.0)); // nose down when flying forward
    }
}
