package de.gtacity.gameplay;

import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.entity.PoliceEntity;
import de.gtacity.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

/** Sends officers, SWAT and patrol cars after wanted players. */
public final class PoliceDispatch {
    private PoliceDispatch() {
    }

    public static void dispatch(ServerLevel level, ServerPlayer player, int stars) {
        int wanted = CityRules.POLICE_PER_STAR[stars];
        int present = level.getEntitiesOfClass(PoliceEntity.class, player.getBoundingBox().inflate(70.0)).size();
        int missing = wanted - present;
        if (missing <= 0) {
            return;
        }
        var random = player.getRandom();
        if (stars >= 2 && random.nextInt(3) == 0) {
            CitySpawns.Lane lane = CitySpawns.findLane(level, player.blockPosition(), 35, 60, random);
            if (lane != null) {
                spawnPoliceCar(level, lane, player, stars >= 4);
                return;
            }
        }
        int batch = Math.min(missing, 2);
        for (int i = 0; i < batch; i++) {
            BlockPos pos = CitySpawns.findSidewalk(level, player.blockPosition(), 22, 45, random);
            if (pos == null) {
                continue;
            }
            PoliceEntity cop = spawnCop(level, pos, stars >= 4 && random.nextInt(2) == 0);
            if (cop != null) {
                cop.setTarget(player);
            }
        }
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
