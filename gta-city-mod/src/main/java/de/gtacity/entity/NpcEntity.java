package de.gtacity.entity;

import de.gtacity.entity.ai.NpcPanicGoal;
import de.gtacity.entity.ai.SidewalkWalkGoal;
import de.gtacity.gameplay.WantedSystem;
import de.gtacity.item.CashItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A pedestrian of the city. Walks on sidewalks, panics when there is shooting, some fight back. */
public class NpcEntity extends PathfinderMob {
    public static final int CIVILIAN_SKINS = 24;
    public static final int GANG_SKINS = 6;

    private static final EntityDataAccessor<Integer> SKIN =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GANG =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> GESTURE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GESTURE_TIME =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> GESTURE_YAW =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);

    /** Gestures (drawn by the client model): none, point somewhere, wave, talk with the hands. */
    public static final int NONE = 0;
    public static final int POINT = 1;
    public static final int WAVE = 2;
    public static final int TALK = 3;
    /** How long a gesture lasts (ticks), by kind. */
    public static final int[] GESTURE_TICKS = {0, 70, 40, 60};

    private long nextGreeting;
    /** Walking to a car door to get in (taxi guests). */
    private CarEntity boarding;
    private int boardingTicks;
    /** Guides disappear a while after pointing the way (game time, 0 = never). */
    private long despawnAt;

    private int panicTicks;
    private Vec3 threat;
    private boolean brave;
    private boolean persistent;
    /** What this figure is for: "store", "weapons", "cars", "jobs", "shady", "passenger", "bounty" - or empty. */
    private String role = "";

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, 0);
        builder.define(GANG, false);
        builder.define(GESTURE, NONE);
        builder.define(GESTURE_TIME, 0);
        builder.define(GESTURE_YAW, 0.0F);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new NpcPanicGoal(this, 1.7));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.4, false));
        goalSelector.addGoal(5, new SidewalkWalkGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return NpcEntity.this.brave && super.canUse();
            }
        });
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        randomizeLook(false);
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    /** Picks a random skin and personality. */
    public void randomizeLook(boolean gangMember) {
        entityData.set(GANG, gangMember);
        entityData.set(SKIN, getRandom().nextInt(gangMember ? GANG_SKINS : CIVILIAN_SKINS));
        brave = gangMember || getRandom().nextInt(4) == 0;
    }

    public int getSkin() {
        return entityData.get(SKIN);
    }

    public boolean isGang() {
        return entityData.get(GANG);
    }

    public boolean isPanicking() {
        return panicTicks > 0;
    }

    public Vec3 threat() {
        return threat;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public String role() {
        return role;
    }

    /** Gives this figure a task (and a name tag): shop clerks, job clerks, passengers, bounty targets. */
    public void setRole(String role, String name) {
        this.role = role;
        setPersistent(true);
        setCustomName(net.minecraft.network.chat.Component.literal(name));
        setCustomNameVisible(true);
    }

    /** Makes every pedestrian around {@code center} run away. */
    public static void scare(ServerLevel level, Vec3 center, double radius, @Nullable LivingEntity source) {
        AABB box = new AABB(center, center).inflate(radius);
        for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, box)) {
            npc.panic(center, 160 + npc.getRandom().nextInt(120));
            if (npc.brave && source != null && npc.isGang() && npc.distanceToSqr(source) < 256) {
                npc.setTarget(source);
            }
        }
    }

    // ------------------------------------------------------------------ gestures

    /** Starts a gesture; {@code yaw} is the world direction for pointing. */
    public void gesture(int kind, float yaw) {
        entityData.set(GESTURE_YAW, yaw);
        entityData.set(GESTURE, kind);
        entityData.set(GESTURE_TIME, (int) level().getGameTime());
    }

    /** Points at a spot in the world (turning the head that way). */
    public void pointAt(double x, double z) {
        float yaw = (float) (Math.toDegrees(Math.atan2(z - getZ(), x - getX())) - 90.0);
        setYHeadRot(yaw);
        if (!isPassenger()) {
            setYRot(yaw);
            setYBodyRot(yaw);
        }
        gesture(POINT, yaw);
    }

    public int gestureKind() {
        return entityData.get(GESTURE);
    }

    /** Ticks since the gesture started (with the partial tick), or -1 when there is none. */
    public float gestureAge(float partialTick) {
        int kind = gestureKind();
        if (kind == NONE) {
            return -1;
        }
        float age = (float) (level().getGameTime() - entityData.get(GESTURE_TIME)) + partialTick;
        return age >= 0 && age < GESTURE_TICKS[kind] ? age : -1;
    }

    public float gestureYaw() {
        return entityData.get(GESTURE_YAW);
    }

    public boolean gesturing() {
        return gestureAge(0) >= 0;
    }

    /** Walks to the passenger door (which opens), then gets in. */
    public void boardCar(CarEntity car) {
        if (boarding == car || getVehicle() == car) {
            return;
        }
        boarding = car;
        boardingTicks = 24;
        car.openDoor(false);
    }

    public boolean isBoarding() {
        return boarding != null;
    }

    private void tickBoarding() {
        CarEntity car = boarding;
        if (!car.isAlive() || boardingTicks-- <= 0) {
            boarding = null;
            return;
        }
        Vec3 door = car.doorSpot(false);
        Vec3 to = door.subtract(position()).multiply(1, 0, 1);
        if (to.length() < 0.35 || boardingTicks < 6) {
            boarding = null;
            startRiding(car, true, false);
            return;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        Vec3 step = to.normalize().scale(Math.min(0.22, to.length()));
        move(net.minecraft.world.entity.MoverType.SELF, new Vec3(step.x, -0.08, step.z));
    }

    public void despawnIn(int ticks) {
        despawnAt = level().getGameTime() + ticks;
    }

    /** Clerks greet players who come in, passengers and guides wave the player over. */
    private void greet() {
        boolean clerk = switch (role) {
            case "store", "weapons", "cars", "jobs", "shady" -> true;
            default -> false;
        };
        boolean waiting = ("passenger".equals(role) || "guide".equals(role)) && !isPassenger();
        if (!clerk && !waiting) {
            return;
        }
        Player near = level().getNearestPlayer(this, clerk ? 7.0 : 35.0);
        if (near == null) {
            return;
        }
        // Face the player.
        float yaw = (float) (Math.toDegrees(Math.atan2(near.getZ() - getZ(), near.getX() - getX())) - 90.0);
        setYHeadRot(yaw);
        long now = level().getGameTime();
        if (!gesturing() && now >= nextGreeting) {
            gesture(WAVE, yaw);
            nextGreeting = now + (clerk ? 20 * 30 : 20 * 3);
        }
    }

    public void panic(Vec3 from, int ticks) {
        if (this instanceof PoliceEntity || !role.isEmpty()) {
            return;
        }
        this.threat = from;
        this.panicTicks = Math.max(panicTicks, ticks);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (panicTicks > 0) {
            panicTicks--;
        }
        if (boarding != null) {
            tickBoarding();
        } else if (!role.isEmpty() && tickCount % 10 == 0) {
            greet();
        }
        if (despawnAt > 0 && level().getGameTime() >= despawnAt) {
            discard();
            return;
        }
        if (!persistent && tickCount % 40 == 0) {
            Player near = level().getNearestPlayer(this, 110.0);
            if (near == null) {
                discard();
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            Entity attacker = source.getEntity();
            if (attacker != null && !brave) {
                panic(attacker.position(), 200);
            }
            scare(level, position(), 12.0, attacker instanceof LivingEntity l ? l : null);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            // Killing a bounty target is legal work, it does not count as murder.
            if (source.getEntity() instanceof Player player && !"bounty".equals(role)) {
                WantedSystem.onNpcKilled(player, this);
            }
            int value = 10 + getRandom().nextInt(isGang() ? 300 : 120);
            ItemEntity cash = new ItemEntity(level, getX(), getY() + 0.5, getZ(), CashItem.of(value));
            cash.setDefaultPickUpDelay();
            level.addFreshEntity(cash);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return persistent && super.shouldBeSaved();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !persistent;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Skin", getSkin());
        output.putBoolean("Gang", isGang());
        output.putBoolean("Brave", brave);
        output.putBoolean("CityPersistent", persistent);
        output.putString("Role", role);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(SKIN, input.getIntOr("Skin", 0));
        entityData.set(GANG, input.getBooleanOr("Gang", false));
        brave = input.getBooleanOr("Brave", false);
        persistent = input.getBooleanOr("CityPersistent", false);
        role = input.getStringOr("Role", "");
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public boolean checkSpawnObstruction(net.minecraft.world.level.LevelReader level) {
        return true;
    }

    /** Is this block column a good place to stand? */
    public static boolean standable(Level level, BlockPos feet) {
        return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                && !level.getBlockState(feet.below()).isAir();
    }
}
