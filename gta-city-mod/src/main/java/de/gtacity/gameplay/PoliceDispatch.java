package de.gtacity.gameplay;

import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.entity.HelicopterEntity;
import de.gtacity.entity.PoliceEntity;
import de.gtacity.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sends officers, SWAT and patrol cars after wanted players. */
public final class PoliceDispatch {
    private PoliceDispatch() {
    }

    private static final Map<UUID, Long> LAST_CAR = new HashMap<>();

    /**
     * Sends backup after a wanted player. Nobody appears out of thin air next to the player: officers arrive in
     * patrol cars that start out of sight, some distance away, and drive over. At five stars a helicopter joins.
     */
    public static void dispatch(ServerLevel level, ServerPlayer player, int stars) {
        if (stars >= 5) {
            HelicopterEntity.ensureFor(level, player);
        }
        int wanted = CityRules.POLICE_PER_STAR[stars];
        int present = level.getEntitiesOfClass(PoliceEntity.class, player.getBoundingBox().inflate(120.0)).size();
        if (present >= wanted) {
            return;
        }
        long now = level.getGameTime();
        Long last = LAST_CAR.get(player.getUUID());
        if (last != null && now - last < CityRules.PATROL_CAR_INTERVAL) {
            return;
        }
        CitySpawns.Lane lane = hiddenLane(level, player);
        if (lane != null) {
            spawnPoliceCar(level, lane, player, stars >= 4);
            LAST_CAR.put(player.getUUID(), now);
        }
    }

    /**
     * A driving lane far enough away that the player cannot see the car appear. At a crossing every street is in
     * view, so after a few tries the car starts further away - and if that is outside the simulated area, simply
     * out at the normal distance: far enough that it just looks like a patrol car turning into the street.
     */
    private static CitySpawns.Lane hiddenLane(ServerLevel level, ServerPlayer player) {
        var random = player.getRandom();
        for (int attempt = 0; attempt < 8; attempt++) {
            CitySpawns.Lane lane = CitySpawns.findLane(level, player.blockPosition(),
                    CityRules.PATROL_CAR_MIN_DISTANCE, CityRules.PATROL_CAR_MAX_DISTANCE, random);
            if (lane == null) {
                continue;
            }
            Vec3 spot = new Vec3(lane.x(), lane.y() + 1.0, lane.z());
            boolean visible = level.clip(new ClipContext(player.getEyePosition(), spot, ClipContext.Block.VISUAL,
                    ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
            if (!visible) {
                return lane;
            }
        }
        CitySpawns.Lane far = CitySpawns.findLane(level, player.blockPosition(), CityRules.PATROL_CAR_MAX_DISTANCE,
                CityRules.PATROL_CAR_MAX_DISTANCE + 40, random);
        if (far != null) {
            return far;
        }
        return CitySpawns.findLane(level, player.blockPosition(), CityRules.PATROL_CAR_MIN_DISTANCE,
                CityRules.PATROL_CAR_MAX_DISTANCE, random);
    }

    public static void forget(ServerPlayer player) {
        LAST_CAR.remove(player.getUUID());
    }

    public static PoliceEntity spawnCop(ServerLevel level, BlockPos pos, boolean swat) {
        PoliceEntity cop = ModEntities.POLICE.create(level, EntitySpawnReason.EVENT);
        if (cop == null) {
            return null;
        }
        cop.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        cop.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        cop.setSwat(swat);
        level.addFreshEntity(cop);
        return cop;
    }

    public static void spawnPoliceCar(ServerLevel level, CitySpawns.Lane lane, ServerPlayer target, boolean swat) {
        CarEntity car = ModEntities.CAR.create(level, EntitySpawnReason.EVENT);
        if (car == null) {
            return;
        }
        car.setVariant(CarVariant.POLICE);
        car.snapTo(lane.x(), lane.y(), lane.z(), lane.heading().toYRot(), 0.0F);
        car.setAiDriving(true, lane.heading());
        car.setSiren(true);
        car.setPursuit(target);
        level.addFreshEntity(car);
        for (int i = 0; i < 2; i++) {
            PoliceEntity cop = spawnCop(level, BlockPos.containing(lane.x(), lane.y(), lane.z()), swat);
            if (cop != null) {
                cop.startRiding(car);
            }
        }
    }

    /** Called by police cars that reached their target: the officers jump out. */
    public static void unload(CarEntity car, ServerPlayer target) {
        for (var passenger : java.util.List.copyOf(car.getPassengers())) {
            if (passenger instanceof PoliceEntity cop) {
                cop.stopRiding();
                cop.setTarget(target);
            }
        }
    }
}
