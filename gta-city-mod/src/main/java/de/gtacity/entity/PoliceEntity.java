package de.gtacity.entity;

import de.gtacity.entity.ai.PoliceGunGoal;
import de.gtacity.entity.ai.SidewalkWalkGoal;
import de.gtacity.gameplay.WantedSystem;
import de.gtacity.registry.ModItems;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** LSPD officer (or SWAT at 4+ stars). Arrests at one star, shoots from two stars on. */
public class PoliceEntity extends NpcEntity {
    public static final int POLICE_SKINS = 4;

    private static final EntityDataAccessor<Boolean> SWAT =
            SynchedEntityData.defineId(PoliceEntity.class, EntityDataSerializers.BOOLEAN);

    public PoliceEntity(EntityType<? extends PoliceEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createPoliceAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SWAT, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PoliceGunGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true) {
            @Override
            public boolean canUse() {
                return super.canUse() && WantedSystem.level(getTarget()) <= 1;
            }
        });
        goalSelector.addGoal(5, new SidewalkWalkGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 5, false, false,
                (target, level) -> WantedSystem.level(target) > 0));
    }

    @Override
    public void randomizeLook(boolean unused) {
        super.randomizeLook(false);
        setSwat(false);
    }

    public void setSwat(boolean swat) {
        entityData.set(SWAT, swat);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(swat ? ModItems.CARBINE : ModItems.PISTOL));
        if (swat) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
            setHealth(40.0F);
        }
    }

    public boolean isSwat() {
        return entityData.get(SWAT);
    }

    @Override
    public int getSkin() {
        return super.getSkin() % POLICE_SKINS;
    }

    /** When a player was last told to give up (game time), so only the first officer says it. */
    private static final java.util.Map<java.util.UUID, Long> WARNED = new java.util.HashMap<>();
    private static final long WARNING_TICKS = 60;

    /** True once the "give yourself up" call is a few seconds old (or was never needed). */
    public static boolean warningOver(Entity target) {
        Long at = WARNED.get(target.getUUID());
        return at != null && target.level().getGameTime() - at >= WARNING_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && getTarget() instanceof Player p && WantedSystem.level(p) == 0) {
            setTarget(null);
        }
        if (!level().isClientSide() && tickCount % 5 == 0 && getTarget() instanceof ServerPlayer p) {
            int stars = WantedSystem.level(p);
            long now = level().getGameTime();
            Long at = WARNED.get(p.getUUID());
            // One call per chase: again only after the player was free for a while.
            if (stars >= 1 && stars <= 2 && (at == null || now - at > 20 * 90) && distanceTo(p) < 24
                    && hasLineOfSight(p)) {
                WARNED.put(p.getUUID(), now);
                level().playSound(null, getX(), getEyeY(), getZ(), de.gtacity.registry.ModSounds.VOICE_POLICE_SURRENDER,
                        net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 1.0F);
                gesture(POINT, (float) (Math.toDegrees(Math.atan2(p.getZ() - getZ(), p.getX() - getX())) - 90.0));
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Polizei: Stehen bleiben! Ergib "
                        + "dich - oder wir schießen!").withStyle(net.minecraft.ChatFormatting.BLUE));
            } else if (stars > 2 && at == null) {
                WARNED.put(p.getUUID(), now - WARNING_TICKS);
            }
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof ServerPlayer player && WantedSystem.level(player) == 1) {
            WantedSystem.arrestProgress(player);
        }
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && source.getEntity() instanceof Player player) {
            WantedSystem.onCopHurt(player);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (source.getEntity() instanceof Player player) {
            WantedSystem.onCopKilled(player);
        }
        super.die(source);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Swat", isSwat());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(SWAT, input.getBooleanOr("Swat", false));
    }
}
