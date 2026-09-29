package de.gtacity.entity;

import de.gtacity.registry.ModSounds;
import de.gtacity.gameplay.PoliceDispatch;
import de.gtacity.gameplay.WantedSystem;
import de.gtacity.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import de.gtacity.world.CityLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * A drivable car. Players drive it with WASD (client authoritative, like boats). Without a player it can be
 * driven by the city traffic AI, which follows the right hand lane of the street grid.
 */
public class CarEntity extends Entity {
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEALTH =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SIREN =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DRIFT =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.BOOLEAN);

    /** Set by the client: reports crashes of the player's own car (the client drives it) to the server. */
    public static java.util.function.Consumer<Float> crashReporter = amount -> {
    };

    private static final float CRUISE_SPEED = 0.42F;
    private static final float TURN_SPEED = 0.24F;

    public float speed;
    public float steer;
    public float wheelRot;
    public float prevWheelRot;
    /** Speed measured from the actual movement - works on every side, also for cars someone else drives. */
    public float measuredSpeed;

    private Vec3 slide = Vec3.ZERO;
    private Vec3 lastMotion = Vec3.ZERO;
    private @Nullable Vec3 lastPos;

    private boolean aiDriving;
    private boolean persistentCar;
    private Direction heading;
    private final Deque<Vec3> waypoints = new ArrayDeque<>();
    private int blockedTicks;
    private int burnTicks = -1;
    private boolean exploded;

    public CarEntity(EntityType<? extends CarEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    // ------------------------------------------------------------------ data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VARIANT, 0);
        builder.define(HEALTH, 60.0F);
        builder.define(SIREN, false);
        builder.define(DRIFT, false);
    }

    public CarVariant getVariant() {
        return CarVariant.byId(entityData.get(VARIANT));
    }

    public void setVariant(CarVariant variant) {
        entityData.set(VARIANT, variant.ordinal());
        entityData.set(HEALTH, variant.shape.health);
    }

    public float getHealth() {
        return entityData.get(HEALTH);
    }

    public float healthFraction() {
        return getHealth() / getVariant().shape.health;
    }

    public boolean isSirenOn() {
        return entityData.get(SIREN);
    }

    public void setSiren(boolean on) {
        entityData.set(SIREN, on);
    }

    /** The driver pulls the handbrake at speed - tyres squeal and smoke. */
    public boolean isDrifting() {
        return entityData.get(DRIFT);
    }

    public void setAiDriving(boolean ai, Direction heading) {
        this.aiDriving = ai;
        this.heading = heading;
        this.waypoints.clear();
    }

    public boolean isAiDriving() {
        return aiDriving;
    }

    public void setPersistentCar(boolean persistent) {
        this.persistentCar = persistent;
    }

    /**
     * City traffic that drove out of the simulated area. It stops ticking there and would stand frozen in its lane,
     * blocking every car behind it, so it gets removed instead.
     */
    public boolean isStrandedTraffic(ServerLevel level) {
        return aiDriving && !persistentCar && !level.isPositionEntityTicking(blockPosition());
    }

    /** Removes the car together with its NPC passengers. */
    public void despawn() {
        discardWithPassengers();
    }

    /** Police cars chase this player and let the officers out once they are close. */
    public void setPursuit(@Nullable ServerPlayer target) {
        this.pursuit = target;
    }

    private @Nullable ServerPlayer pursuit;

    /** Bought cars belong to a player; the chop shop does not take them. */
    private @Nullable UUID owner;

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    public boolean isOwnedBy(Player player) {
        return player.getUUID().equals(owner);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(VARIANT, input.getIntOr("Variant", 0));
        entityData.set(HEALTH, input.getFloatOr("Health", getVariant().shape.health));
        persistentCar = input.getBooleanOr("PersistentCar", true);
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Variant", entityData.get(VARIANT));
        output.putFloat("Health", getHealth());
        output.putBoolean("PersistentCar", persistentCar);
        output.storeNullable("Owner", UUIDUtil.CODEC, owner);
    }

    @Override
    public boolean shouldBeSaved() {
        return persistentCar && super.shouldBeSaved();
    }

    // ------------------------------------------------------------------ physics basics

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
    public float maxUpStep() {
        return 0.6F;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.08;
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < 2;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int index = getPassengers().indexOf(passenger);
        float side = index <= 0 ? 0.42F : -0.42F;
        float seat = getVariant().shape == CarVariant.Shape.SUV ? 0.45F : 0.2F;
        return new Vec3(side, seat, -0.1).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        if (passenger instanceof LivingEntity living) {
            living.setYBodyRot(getYRot());
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 left = new Vec3(1.8, 0, 0).yRot(-getYRot() * Mth.DEG_TO_RAD);
        Vec3 spot = position().add(left);
        if (level().noCollision(passenger, passenger.getBoundingBox().move(spot.subtract(passenger.position())))) {
            return spot;
        }
        return position().add(left.scale(-1)).add(0, 0.5, 0);
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive() || player.getVehicle() == this) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (getControllingPassenger() != null) {
            return canAddPassenger(player) && player.startRiding(this) ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }
        Entity driver = getFirstPassenger();
        if (driver instanceof NpcEntity npc) {
            npc.stopRiding();
            npc.panic(player.position(), 200);
            WantedSystem.onCarJacked(player, getVariant() == CarVariant.POLICE);
            player.sendOverlayMessage(Component.literal("Auto geklaut!").withStyle(ChatFormatting.RED));
        } else if (getVariant() == CarVariant.POLICE) {
            WantedSystem.onCarJacked(player, true);
        }
        aiDriving = false;
        persistentCar = true;
        waypoints.clear();
        setSiren(false);
        return player.startRiding(this) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableToBase(source)) {
            return false;
        }
        if (source.getEntity() instanceof Player p && p.getAbilities().instabuild && p.isShiftKeyDown()) {
            discardWithPassengers();
            return true;
        }
        damageCar(level, source, amount);
        return true;
    }

    public void damageCar(ServerLevel level, @Nullable DamageSource source, float amount) {
        if (exploded) {
            return;
        }
        entityData.set(HEALTH, Math.max(0.0F, getHealth() - amount));
        if (getHealth() <= 0.0F) {
            explode(level);
        } else if (healthFraction() < 0.25F && burnTicks < 0) {
            burnTicks = 120;
        }
    }

    private void explode(ServerLevel level) {
        exploded = true;
        ejectPassengers();
        level.explode(this, getX(), getY() + 0.6, getZ(), 3.5F, true, Level.ExplosionInteraction.NONE);
        ModSounds.boom(level, getX(), getY() + 0.6, getZ());
        discardWithPassengers();
    }

    private void discardWithPassengers() {
        for (Entity passenger : List.copyOf(getPassengers())) {
            if (passenger instanceof NpcEntity npc) {
                npc.discard();
            }
        }
        ejectPassengers();
        discard();
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        prevWheelRot = wheelRot;
        boolean authoritative = isLocalInstanceAuthoritative();
        if (authoritative) {
            float throttle = 0.0F, turn = 0.0F;
            boolean handbrake = false;
            LivingEntity driver = getControllingPassenger();
            if (driver != null) {
                throttle = driver.zza;
                turn = driver.xxa;
                handbrake = driver.isShiftKeyDown();
            } else if (aiDriving && !level().isClientSide()) {
                float[] input = aiInput();
                throttle = input[0];
                turn = input[1];
            }
            drive(throttle, turn, handbrake);
        }
        measureSpeed();
        if (!authoritative) {
            // Someone else moves the car: keep our physics state in step, so taking over the wheel is seamless.
            speed = measuredSpeed;
            slide = lastMotion;
        }
        wheelRot += speed * 45.0F;

        if (level() instanceof ServerLevel server) {
            serverTick(server);
            return;
        }
        if (isDrifting()) {
            // Tyre smoke from both rear wheels.
            for (int side = -1; side <= 1; side += 2) {
                Vec3 wheel = new Vec3(side * 0.9, 0.15, -1.6).yRot(-getYRot() * Mth.DEG_TO_RAD);
                level().addParticle(ParticleTypes.CLOUD, getX() + wheel.x, getY() + wheel.y, getZ() + wheel.z,
                        (random.nextDouble() - 0.5) * 0.05, 0.03, (random.nextDouble() - 0.5) * 0.05);
            }
        }
        if (healthFraction() < 0.5F) {
            double f = healthFraction() < 0.25F ? 3 : 1;
            for (int i = 0; i < f; i++) {
                level().addParticle(healthFraction() < 0.25F ? ParticleTypes.FLAME : ParticleTypes.SMOKE,
                        getX() + (random.nextDouble() - 0.5), getY() + 1.0, getZ() + (random.nextDouble() - 0.5),
                        0, 0.05, 0);
            }
        }
    }

    private void measureSpeed() {
        Vec3 pos = position();
        lastMotion = lastPos == null ? Vec3.ZERO : pos.subtract(lastPos).multiply(1, 0, 1);
        lastPos = pos;
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        float moved = (float) lastMotion.horizontalDistance();
        measuredSpeed = lastMotion.dot(forward) < 0 ? -moved : moved;
    }

    private void drive(float throttle, float turn, boolean handbrake) {
        CarVariant.Shape shape = getVariant().shape;
        if (isInWater()) {
            throttle = 0.0F;
            speed *= 0.8F;
        }
        if (handbrake) {
            // Handbrake: the rear wheels lock, the car slides on and turns much sharper. Gas keeps a power slide going.
            speed *= Math.abs(speed) > 0.25F ? 0.994F : 0.88F;
            if (throttle > 0.01F && speed > 0.25F) {
                speed += shape.accel * 0.3F * throttle;
            }
        } else if (throttle > 0.01F) {
            // Pulls hard from standstill and gets slower towards top speed.
            float falloff = 1.0F - 0.65F * Math.max(0.0F, speed) / shape.maxSpeed;
            speed += speed < 0 ? 0.06F : shape.accel * throttle * falloff;
        } else if (throttle < -0.01F) {
            speed -= speed > 0 ? 0.06F : shape.accel * 0.6F;
        } else {
            speed *= 0.985F;
            if (Math.abs(speed) < 0.004F) {
                speed = 0.0F;
            }
        }
        speed = Mth.clamp(speed, -0.35F, shape.maxSpeed);
        // Never race into terrain that is not loaded yet (it would stop the car dead): brake smoothly instead.
        if (Math.abs(speed) > 0.6F) {
            Vec3 ahead = position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(speed * 12.0F));
            if (!level().hasChunkAt(net.minecraft.core.BlockPos.containing(ahead))) {
                speed *= 0.9F;
            }
        }

        steer += (Mth.clamp(turn, -1.0F, 1.0F) - steer) * 0.35F;
        float grip = Math.min(1.0F, Math.abs(speed) / 0.12F);
        boolean sliding = handbrake && Math.abs(speed) > 0.25F;
        float turnRate = shape.turn * (sliding ? 2.1F : 1.0F);
        float yawChange = steer * turnRate * grip * Math.signum(speed) * (1.0F - Math.abs(speed) / (shape.maxSpeed * 3));
        setYRot(getYRot() - yawChange);

        // The body turns right away, the actual motion follows it with some delay: almost at once with grip,
        // slowly while drifting - that is what makes the car slide sideways through the corner.
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 wanted = new Vec3(forward.x * speed, 0.0, forward.z * speed);
        slide = slide.lerp(wanted, sliding ? 0.07 : 0.55);
        double vy = getDeltaMovement().y;
        vy = onGround() ? -0.04 : vy - getGravity();
        setDeltaMovement(slide.x, vy, slide.z);
        double before = slide.horizontalDistance();
        move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision) {
            if (before > 0.45) {
                float damage = (float) before * 12.0F;
                if (level() instanceof ServerLevel server) {
                    damageCar(server, null, damage);
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL,
                            0.6F, 0.6F);
                } else {
                    crashReporter.accept(damage);
                }
            }
            speed *= -0.15F;
            slide = slide.scale(-0.15);
        }
    }

    /** A player's crash, reported by their client (the client drives the car). */
    public void onReportedCrash(ServerLevel level, float damage) {
        damageCar(level, null, Math.min(damage, 60.0F));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.6F, 0.6F);
    }

    private void serverTick(ServerLevel level) {
        if (burnTicks > 0 && --burnTicks == 0) {
            explode(level);
            return;
        }
        LivingEntity driver = getControllingPassenger();
        entityData.set(DRIFT, driver != null && driver.isShiftKeyDown() && Math.abs(measuredSpeed) > 0.3F);
        if (Math.abs(measuredSpeed) > 0.18F) {
            runOver(level);
        }
        if (!persistentCar && getPassengers().stream().noneMatch(p -> p instanceof Player) && tickCount % 40 == 0) {
            if (level.getNearestPlayer(this, 150.0) == null) {
                discardWithPassengers();
                return;
            }
        }
    }

    private void runOver(ServerLevel level) {
        float speed = Math.abs(measuredSpeed);
        Vec3 forward = lastMotion.normalize();
        // At high speed the car covers several blocks per tick - the hit box has to cover that distance.
        AABB box = getBoundingBox().expandTowards(lastMotion.scale(-1.0)).move(forward.scale(0.6)).inflate(0.1);
        LivingEntity driver = getControllingPassenger();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e.getVehicle() != this && !e.isSpectator())) {
            float damage = speed * 28.0F;
            DamageSource source = driver instanceof Player p ? level.damageSources().playerAttack(p)
                    : level.damageSources().generic();
            victim.hurtServer(level, source, damage);
            victim.push(forward.x * speed * 1.5, 0.35 + Math.abs(speed) * 0.3, forward.z * speed * 1.5);
            victim.syncVelocity = true;
            this.speed *= 0.8F;
        }
    }

    // ------------------------------------------------------------------ traffic AI

    private float[] aiInput() {
        if (heading == null) {
            heading = Direction.fromYRot(getYRot());
        }
        Vec3 pos = position();
        if (pursuit != null) {
            if (!pursuit.isAlive() || pursuit.isRemoved() || WantedSystem.level(pursuit) == 0) {
                pursuit = null;
                setSiren(false);
            } else if (distanceTo(pursuit) < 18.0) {
                PoliceDispatch.unload(this, pursuit);
                pursuit = null;
                aiDriving = false;
                return new float[]{-1.0F, 0.0F};
            }
        }
        if (waypoints.isEmpty()) {
            planIntersection(pos);
        }
        Vec3 target;
        float targetSpeed = CRUISE_SPEED;
        if (!waypoints.isEmpty()) {
            target = waypoints.peekFirst();
            if (horizontalDistance(pos, target) < 2.2) {
                waypoints.pollFirst();
                if (waypoints.isEmpty()) {
                    heading = pendingHeading;
                }
            }
            targetSpeed = TURN_SPEED;
        } else {
            target = laneAhead(pos, heading, 8.0);
        }
        if (target == null) {
            return new float[]{0, 0};
        }

        float wantYaw = (float) (Mth.atan2(target.z - pos.z, target.x - pos.x) * Mth.RAD_TO_DEG) - 90.0F;
        float diff = Mth.wrapDegrees(wantYaw - getYRot());
        float turn = Mth.clamp(-diff / 22.0F, -1.0F, 1.0F);

        if (obstacleAhead()) {
            targetSpeed = 0.0F;
            if (++blockedTicks % 60 == 20) {
                level().playSound(null, getX(), getY(), getZ(), ModSounds.HORN, SoundSource.NEUTRAL, 1.5F,
                        0.85F + random.nextFloat() * 0.3F);
            }
        } else {
            blockedTicks = 0;
        }
        float throttle;
        if (speed < targetSpeed - 0.02F) {
            throttle = 1.0F;
        } else if (speed > targetSpeed + 0.04F) {
            throttle = -1.0F;
        } else {
            throttle = 0.0F;
        }
        if (targetSpeed == 0.0F && speed < 0.03F) {
            throttle = 0.0F;
            speed = 0.0F;
        }
        return new float[]{throttle, turn};
    }

    private Direction pendingHeading;

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Centre line of the right hand lane for the given heading, {@code ahead} blocks in front. */
    private static Vec3 laneAhead(Vec3 pos, Direction heading, double ahead) {
        int gx = CityLayout.cell(Mth.floor(pos.x)), gz = CityLayout.cell(Mth.floor(pos.z));
        double y = pos.y;
        return switch (heading) {
            case SOUTH -> new Vec3(gx * CityLayout.PITCH + 5.5, y, pos.z + ahead);
            case NORTH -> new Vec3(gx * CityLayout.PITCH + 12.5, y, pos.z - ahead);
            case EAST -> new Vec3(pos.x + ahead, y, gz * CityLayout.PITCH + 12.5);
            default -> new Vec3(pos.x - ahead, y, gz * CityLayout.PITCH + 5.5);
        };
    }

    private static double laneCoord(Direction heading, int cellIndex) {
        return cellIndex * CityLayout.PITCH + (heading == Direction.SOUTH || heading == Direction.WEST ? 5.5 : 12.5);
    }

    private void planIntersection(Vec3 pos) {
        Vec3 probe = pos.add(heading.getStepX() * 9.0, 0, heading.getStepZ() * 9.0);
        int px = Mth.floor(probe.x), pz = Mth.floor(probe.z);
        if (!CityLayout.isIntersection(px, pz) || CityLayout.isIntersection(Mth.floor(pos.x), Mth.floor(pos.z))) {
            return;
        }
        int ix = CityLayout.cell(px), iz = CityLayout.cell(pz);
        Direction[] options = {heading, heading.getClockWise(), heading.getCounterClockWise()};
        int pick = random.nextInt(10);
        Direction next = pick < 6 ? options[0] : pick < 8 ? options[1] : options[2];
        if (pursuit != null) {
            double best = Double.MAX_VALUE;
            for (Direction option : options) {
                double tx = ix * CityLayout.PITCH + 9 + option.getStepX() * 60.0;
                double tz = iz * CityLayout.PITCH + 9 + option.getStepZ() * 60.0;
                double d = pursuit.distanceToSqr(tx, pursuit.getY(), tz);
                if (d < best && exitInsideCity(ix, iz, option)) {
                    best = d;
                    next = option;
                }
            }
        }
        for (int attempt = 0; attempt < 4 && !exitInsideCity(ix, iz, next); attempt++) {
            next = attempt < 2 ? options[attempt + 1] : heading.getOpposite();
        }
        int x0 = ix * CityLayout.PITCH, z0 = iz * CityLayout.PITCH;
        double y = pos.y;
        Vec3 exit = switch (next) {
            case SOUTH -> new Vec3(laneCoord(next, ix), y, z0 + CityLayout.CORRIDOR + 3);
            case NORTH -> new Vec3(laneCoord(next, ix), y, z0 - 3);
            case EAST -> new Vec3(x0 + CityLayout.CORRIDOR + 3, y, laneCoord(next, iz));
            default -> new Vec3(x0 - 3, y, laneCoord(next, iz));
        };
        if (next != heading) {
            boolean alongZ = heading.getAxis() == Direction.Axis.Z;
            double laneNow = alongZ ? laneCoord(heading, ix) : laneCoord(heading, iz);
            double laneNext = alongZ ? laneCoord(next, iz) : laneCoord(next, ix);
            waypoints.add(alongZ ? new Vec3(laneNow, y, laneNext) : new Vec3(laneNext, y, laneNow));
        }
        waypoints.add(exit);
        pendingHeading = next;
    }

    private static boolean exitInsideCity(int ix, int iz, Direction d) {
        int x = ix * CityLayout.PITCH + 9 + d.getStepX() * 40;
        int z = iz * CityLayout.PITCH + 9 + d.getStepZ() * 40;
        return CityLayout.insideCity(x, z);
    }

    private boolean obstacleAhead() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 center = position().add(forward.scale(3.2 + Math.abs(speed) * 6));
        AABB box = new AABB(center, center).inflate(1.2, 1.0, 1.2).move(0, 0.8, 0);
        return !level().getEntities(this, box,
                e -> (e instanceof LivingEntity || e instanceof CarEntity) && e.getVehicle() != this).isEmpty();
    }
}
