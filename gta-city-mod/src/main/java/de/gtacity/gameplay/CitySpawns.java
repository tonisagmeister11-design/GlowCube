package de.gtacity.gameplay;

import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.entity.NpcEntity;
import de.gtacity.entity.PoliceEntity;
import de.gtacity.registry.ModEntities;
import de.gtacity.world.CityChunkGenerator;
import de.gtacity.world.CityLayout;
import de.gtacity.world.Lot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

/** Keeps the streets around every player busy with pedestrians and traffic, and parks cars during world generation. */
public final class CitySpawns {
    private CitySpawns() {
    }

    public record Lane(double x, double y, double z, Direction heading) {
    }

    private static final CarVariant[] TRAFFIC = {
            CarVariant.SEDAN_RED, CarVariant.SEDAN_BLUE, CarVariant.SEDAN_BLACK, CarVariant.SEDAN_WHITE,
            CarVariant.SEDAN_SILVER, CarVariant.SEDAN_GREEN, CarVariant.SEDAN_PURPLE, CarVariant.SUV_BLACK,
            CarVariant.SUV_WHITE, CarVariant.SUV_SILVER, CarVariant.SUV_NAVY, CarVariant.SUV_DARKGREEN,
            CarVariant.TAXI, CarVariant.TAXI, CarVariant.SPORTS_RED, CarVariant.SPORTS_YELLOW,
            CarVariant.SPORTS_BLACK, CarVariant.SPORTS_BLUE, CarVariant.SPORTS_ORANGE, CarVariant.SPORTS_LIME,
    };

    public static CarVariant randomCivilianCar(RandomSource random) {
        CarVariant v = TRAFFIC[random.nextInt(TRAFFIC.length)];
        return v == CarVariant.TAXI && random.nextBoolean() ? CarVariant.SEDAN_SILVER : v;
    }

    // ------------------------------------------------------------------ positions

    /** A free sidewalk spot between minDist and maxDist blocks away from center, or null. */
    public static BlockPos findSidewalk(ServerLevel level, BlockPos center, int minDist, int maxDist,
                                       RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = minDist + random.nextInt(Math.max(1, maxDist - minDist));
            int x = center.getX() + Mth.floor(Math.cos(angle) * dist);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * dist);
            if (!CityLayout.isSidewalk(x, z) || !level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            BlockPos feet = new BlockPos(x, CityLayout.GROUND + 2, z);
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos p = feet.above(dy);
                if (NpcEntity.standable(level, p)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** A spot in the middle of a driving lane, with the direction traffic flows there. */
    public static Lane findLane(ServerLevel level, BlockPos center, int minDist, int maxDist, RandomSource random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = minDist + random.nextInt(Math.max(1, maxDist - minDist));
            int x = center.getX() + Mth.floor(Math.cos(angle) * dist);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * dist);
            if (!CityLayout.insideCity(x, z) || !level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            int lx = CityLayout.local(x), lz = CityLayout.local(z);
            boolean ns = lx < CityLayout.CORRIDOR, ew = lz < CityLayout.CORRIDOR;
            if (ns == ew || (ns && lz < CityLayout.CORRIDOR + 6) || (ew && lx < CityLayout.CORRIDOR + 6)) {
                continue; // not a plain road segment
            }
            int gx = CityLayout.cell(x), gz = CityLayout.cell(z);
            double y = CityLayout.GROUND + 1.0;
            Lane lane;
            if (ns) {
                Direction h = random.nextBoolean() ? Direction.SOUTH : Direction.NORTH;
                lane = new Lane(gx * CityLayout.PITCH + (h == Direction.SOUTH ? 5.5 : 12.5), y, z + 0.5, h);
            } else {
                Direction h = random.nextBoolean() ? Direction.EAST : Direction.WEST;
                lane = new Lane(x + 0.5, y, gz * CityLayout.PITCH + (h == Direction.EAST ? 12.5 : 5.5), h);
            }
            AABB box = new AABB(lane.x - 3, y, lane.z - 3, lane.x + 3, y + 2, lane.z + 3);
            if (level.getEntitiesOfClass(CarEntity.class, box).isEmpty()) {
                return lane;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ tick

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 5) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)
                    || !(level.getChunkSource().getGenerator() instanceof CityChunkGenerator)
                    || player.isSpectator()) {
                continue;
            }
            if (!CityLayout.insideCity(player.getBlockX(), player.getBlockZ())) {
                continue;
            }
            spawnPedestrians(level, player);
            spawnTraffic(level, player);
        }
    }

    private static void spawnPedestrians(ServerLevel level, ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(64.0, 32.0, 64.0);
        int count = level.getEntitiesOfClass(NpcEntity.class, area, n -> !(n instanceof PoliceEntity)).size();
        CityLayout.District district = CityLayout.districtAt(player.getBlockX(), player.getBlockZ());
        int target = switch (district) {
            case DOWNTOWN -> CityRules.PEDESTRIANS_NEAR_PLAYER + 6;
            case MIDTOWN -> CityRules.PEDESTRIANS_NEAR_PLAYER;
            case RESIDENTIAL, HILLS -> CityRules.PEDESTRIANS_NEAR_PLAYER - 8;
            case INDUSTRIAL -> CityRules.PEDESTRIANS_NEAR_PLAYER - 12;
        };
        RandomSource random = player.getRandom();
        for (int i = 0; i < 3 && count < target; i++) {
            BlockPos pos = findSidewalk(level, player.blockPosition(), 20, 56, random);
            if (pos == null) {
                continue;
            }
            if (random.nextInt(25) == 0 && WantedSystem.level(player) == 0) {
                PoliceDispatch.spawnCop(level, pos, false);
            } else {
                NpcEntity npc = ModEntities.PEDESTRIAN.create(level, EntitySpawnReason.NATURAL);
                if (npc == null) {
                    continue;
                }
                npc.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
                npc.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
                boolean gangTurf = district == CityLayout.District.INDUSTRIAL
                        || (district == CityLayout.District.RESIDENTIAL && random.nextInt(6) == 0);
                npc.randomizeLook(gangTurf && random.nextInt(3) == 0);
                level.addFreshEntity(npc);
            }
            count++;
        }
    }

    private static void spawnTraffic(ServerLevel level, ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(96.0, 32.0, 96.0);
        int count = level.getEntitiesOfClass(CarEntity.class, area, CarEntity::isAiDriving).size();
        if (count >= CityRules.TRAFFIC_NEAR_PLAYER) {
            return;
        }
        RandomSource random = player.getRandom();
        Lane lane = findLane(level, player.blockPosition(), 40, 85, random);
        if (lane == null) {
            return;
        }
        CarEntity car = ModEntities.CAR.create(level, EntitySpawnReason.NATURAL);
        NpcEntity driver = ModEntities.PEDESTRIAN.create(level, EntitySpawnReason.NATURAL);
        if (car == null || driver == null) {
            return;
        }
        car.setVariant(randomCivilianCar(random));
        car.snapTo(lane.x(), lane.y(), lane.z(), lane.heading().toYRot(), 0.0F);
        car.setAiDriving(true, lane.heading());
        level.addFreshEntity(car);
        driver.snapTo(lane.x(), lane.y(), lane.z(), lane.heading().toYRot(), 0.0F);
        driver.randomizeLook(false);
        level.addFreshEntity(driver);
        driver.startRiding(car);
    }

    // ------------------------------------------------------------------ world generation

    /** Parks cars on parking lots, driveways, the dealership and in front of police stations. */
    public static void onChunkGenerated(WorldGenRegion region) {
        ChunkPos chunk = region.getCenter();
        int minX = chunk.getMinBlockX(), minZ = chunk.getMinBlockZ();
        Set<Long> seen = new HashSet<>();
        RandomSource random = RandomSource.create(chunk.pack() * 31L + 7);
        for (int dx = 0; dx < 16; dx += 4) {
            for (int dz = 0; dz < 16; dz += 4) {
                Lot lot = CityLayout.lotAt(minX + dx, minZ + dz);
                if (lot == null || !seen.add(((long) lot.x0 << 32) ^ (lot.z0 & 0xffffffffL))) {
                    continue;
                }
                parkCars(region, lot, minX, minZ, random);
            }
        }
    }

    private static void parkCars(WorldGenRegion region, Lot lot, int minX, int minZ, RandomSource random) {
        Lot.Frame f = lot.frame();
        switch (lot.type) {
            case PARKING -> {
                for (int u = 3; u < f.width - 2; u += 4) {
                    tryPark(region, f, u, 5.5, CityLayout.GROUND + 1.0, minX, minZ, random, 55, null);
                    tryPark(region, f, u, f.depth - 6.5, CityLayout.GROUND + 1.0, minX, minZ, random, 55, null);
                }
            }
            case HOUSE -> tryPark(region, f, 20.5, 9.0, CityLayout.GROUND + 2.0, minX, minZ, random, 60, null);
            case VILLA -> tryPark(region, f, f.width - 4.5, 8.0, CityLayout.GROUND + 2.0, minX, minZ, random, 80,
                    CarVariant.Shape.SPORTS);
            case CAR_DEALER -> {
                tryPark(region, f, f.width / 2.0 - 7, 12.0, CityLayout.GROUND + 2.0, minX, minZ, random, 100,
                        CarVariant.Shape.SPORTS);
                tryPark(region, f, f.width / 2.0 + 7, 12.0, CityLayout.GROUND + 2.0, minX, minZ, random, 100,
                        CarVariant.Shape.SUV);
            }
            case POLICE -> {
                if (lot.size >= 39) {
                    for (int u = 4; u < f.width - 4; u += 6) {
                        tryParkVariant(region, f, u, 5.0, CityLayout.GROUND + 2.0, minX, minZ, random, 70,
                                CarVariant.POLICE);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void tryPark(WorldGenRegion region, Lot.Frame f, double u, double v, double y, int minX, int minZ,
                                RandomSource random, int chance, CarVariant.Shape shape) {
        CarVariant variant = randomCivilianCar(random);
        if (shape != null) {
            for (int i = 0; i < 20 && variant.shape != shape; i++) {
                variant = randomCivilianCar(random);
            }
        }
        tryParkVariant(region, f, u, v, y, minX, minZ, random, chance, variant);
    }

    private static void tryParkVariant(WorldGenRegion region, Lot.Frame f, double u, double v, double y, int minX,
                                       int minZ, RandomSource random, int chance, CarVariant variant) {
        int iu = Mth.floor(u), iv = Mth.floor(v);
        double x = f.x(iu, iv) + 0.5, z = f.z(iu, iv) + 0.5;
        if (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16 || random.nextInt(100) >= chance) {
            return;
        }
        CarEntity car = ModEntities.CAR.create(region.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
        if (car == null) {
            return;
        }
        car.setVariant(variant);
        car.setPersistentCar(true);
        car.snapTo(x, y, z, f.out().getOpposite().toYRot(), 0.0F);
        region.addFreshEntity(car);
    }
}
