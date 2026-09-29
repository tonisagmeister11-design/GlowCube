package de.gtacity.item;

import de.gtacity.block.GasPumpBlock;
import de.gtacity.entity.CarEntity;
import de.gtacity.entity.HelicopterEntity;
import de.gtacity.entity.NpcEntity;
import de.gtacity.entity.RocketEntity;
import de.gtacity.network.ModNetworking;
import de.gtacity.registry.ModComponents;
import de.gtacity.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server side shooting logic for players and NPCs. */
public final class Weapons {
    private Weapons() {
    }

    private static final Map<UUID, Long> LAST_SHOT = new HashMap<>();

    /**
     * Fire request from a client.
     *
     * @param held true if the trigger is held down (only automatic guns keep firing)
     */
    public static void tryFire(ServerPlayer player, boolean held) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun) || player.isSpectator() || !player.isAlive()) {
            return;
        }
        GunType type = gun.type;
        if (held && !type.automatic) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(stack)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Long last = LAST_SHOT.get(player.getUUID());
        if (last != null && now - last < type.fireDelay) {
            return;
        }
        int ammo = GunItem.ammo(stack);
        if (ammo <= 0) {
            if (!reload(player)) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DRY_FIRE,
                        SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            return;
        }
        LAST_SHOT.put(player.getUUID(), now);
        if (!player.getAbilities().instabuild) {
            stack.set(ModComponents.AMMO, ammo - 1);
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        if (type == GunType.RPG) {
            RocketEntity rocket = new RocketEntity(level, player);
            rocket.setPos(eye.add(look.scale(0.8)));
            rocket.shoot(look.x, look.y, look.z, 2.2F, 0.0F);
            level.addFreshEntity(rocket);
        } else {
            float spread = type.spread * (player.isShiftKeyDown() ? 0.5F : 1.0F);
            if (player.isSprinting()) {
                spread *= 2.0F;
            }
            for (int i = 0; i < type.pellets; i++) {
                Vec3 dir = spread(look, spread, player.getRandom());
                hitscan(level, player, eye, dir, type.damage, type.range, type == GunType.SNIPER);
            }
        }
        playShot(level, player, type);
        NpcEntity.scare(level, player.position(), 28.0, player);
        if (ammo - 1 <= 0 && !player.getAbilities().instabuild) {
            reload(player);
        }
    }

    /** Reloads the gun in the main hand from ammo in the inventory. */
    public static boolean reload(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun)) {
            return false;
        }
        GunType type = gun.type;
        int have = GunItem.ammo(stack);
        int need = type.magazine - have;
        if (need <= 0 || player.getCooldowns().isOnCooldown(stack)) {
            return false;
        }
        int taken = player.getAbilities().instabuild ? need : AmmoItem.take(player, type.ammo, need);
        if (taken <= 0) {
            player.sendOverlayMessage(Component.literal("Keine Munition! Kauf welche bei Ammu-Nation.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        stack.set(ModComponents.AMMO, have + taken);
        player.getCooldowns().addCooldown(stack, type.reloadTicks);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.RELOAD, SoundSource.PLAYERS, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        return true;
    }

    /** NPC (police, gangs) shooting at a target. */
    public static void npcShoot(LivingEntity shooter, LivingEntity target, float damage, float spreadDegrees) {
        if (!(shooter.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 eye = shooter.getEyePosition();
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 dir = spread(aim.subtract(eye).normalize(), spreadDegrees, shooter.getRandom());
        hitscan(level, shooter, eye, dir, damage, 60, false);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                ModSounds.GUN_PISTOL, SoundSource.HOSTILE, 3.0F, 0.9F + shooter.getRandom().nextFloat() * 0.2F);
        NpcEntity.scare(level, shooter.position(), 20.0, null);
    }

    private static Vec3 spread(Vec3 dir, float degrees, RandomSource random) {
        if (degrees <= 0) {
            return dir;
        }
        double r = Math.toRadians(degrees);
        Vec3 d = dir.add(random.nextGaussian() * r * 0.5, random.nextGaussian() * r * 0.5,
                random.nextGaussian() * r * 0.5);
        return d.normalize();
    }

    private static void hitscan(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir, float damage,
                                double range, boolean piercing) {
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, shooter));
        Vec3 end = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();
        Entity vehicle = shooter.getVehicle();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, shooter, from, end,
                new AABB(from, end).inflate(1.0),
                e -> e != shooter && e != vehicle && e.isPickable() && !e.isSpectator() && e.isAlive(), 0.25F);

        Vec3 impact = entityHit != null ? entityHit.getLocation() : end;
        ModNetworking.shotEffect(level, from.add(dir.scale(0.6)).add(0, -0.12, 0), impact);

        if (entityHit != null) {
            hitEntity(level, shooter, entityHit.getEntity(), entityHit.getLocation(), damage);
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            hitBlock(level, blockHit);
        }
    }

    private static void hitEntity(ServerLevel level, LivingEntity shooter, Entity target, Vec3 at, float damage) {
        DamageSource source = shooter instanceof Player p
                ? level.damageSources().playerAttack(p)
                : level.damageSources().mobAttack(shooter);
        float amount = damage;
        if (shooter instanceof net.minecraft.server.level.ServerPlayer a
                && target instanceof net.minecraft.server.level.ServerPlayer b
                && de.gtacity.gameplay.Crew.together(a, b)) {
            return; // no friendly fire in a crew
        }
        if (target instanceof HelicopterEntity heli) {
            heli.damage(level, amount);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
            return;
        }
        if (target instanceof CarEntity car) {
            car.damageCar(level, source, amount);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
            return;
        }
        if (target instanceof LivingEntity living) {
            if (at.y > living.getEyeY() - 0.25) {
                amount *= 2.0F; // headshot
            }
            living.setInvulnerableTime(0);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                    at.x, at.y, at.z, 10, 0.1, 0.1, 0.1, 0.15);
        }
        target.hurtServer(level, source, amount);
    }

    private static void hitBlock(ServerLevel level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Vec3 at = hit.getLocation();
        if (state.getBlock() instanceof GasPumpBlock) {
            GasPumpBlock.blowUp(level, pos);
            return;
        }
        if (state.getSoundType() == SoundType.GLASS && state.getDestroySpeed(level, pos) >= 0) {
            level.destroyBlock(pos, false);
            return;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 6, 0.05, 0.05,
                0.05, 0.1);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.02, 0.02, 0.02, 0.01);
    }

    private static void playShot(ServerLevel level, ServerPlayer player, GunType type) {
        // Loud enough to be heard a few streets away (every volume step above 1 adds 16 blocks of range).
        float volume = type == GunType.SNIPER || type == GunType.RPG ? 5.0F : type == GunType.SMG ? 2.5F : 3.5F;
        float pitch = 0.94F + player.getRandom().nextFloat() * 0.12F;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.shot(type), SoundSource.PLAYERS,
                volume, pitch);
        float recoil = switch (type) {
            case SNIPER -> 4.0F;
            case SHOTGUN -> 5.0F;
            case RPG -> 6.0F;
            case PISTOL -> 1.5F;
            default -> 0.6F;
        };
        ModNetworking.recoil(player, recoil);
    }
}
