package de.gtacity.gameplay;

import de.gtacity.entity.NpcEntity;
import de.gtacity.network.ModNetworking;
import de.gtacity.registry.ModEntities;
import de.gtacity.shop.ShopType;
import de.gtacity.world.CityLayout;
import de.gtacity.world.CityMap;
import de.gtacity.world.Lot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The people behind the counters: a clerk in every 24/7, Ammu-Nation, car dealer and gas station shop, and the
 * staff of the job centres and harbour offices. They stand at the counter, wear a name tag and open the shop or
 * the job board when you talk to them (right click). Spawned on demand near players, so they also appear in worlds
 * that were created before this feature.
 */
public final class Clerks {
    private Clerks() {
    }

    /** Where a clerk stands: block coordinates of the feet, facing (yaw), role and name. */
    public record Spot(double x, double y, double z, float yaw, String role, String name) {
    }

    private static final String[] JOB_NAMES = {"Marco", "Rita", "Jonas", "Lena", "Ahmed", "Sofia"};
    private static final String[] SHADY_NAMES = {"Tony", "Vera", "Diego", "Nadia"};
    private static final long RESPAWN = 20L * 60 * 3;
    private static final Map<Long, Long> LAST_SPAWN = new HashMap<>();

    public static Spot spot(CityMap.Place place) {
        Lot lot = place.lot();
        Lot.Frame f = lot.frame();
        int width = f.width, depth = f.depth;
        int u0 = 3, u1 = width - 4, v0 = 4, uc = (u0 + u1) / 2;
        int u, v;
        String role, name;
        long h = lot.seed;
        switch (place.kind()) {
            case STORE -> {
                u = u0 + 3;
                v = v0 + 3;
                role = "store";
                name = "Verkäufer (24/7)";
            }
            case GAS_STATION -> {
                boolean big = lot.size >= 39;
                int sv0 = big ? depth - 13 : depth - 10;
                u = u0 + 3;
                v = sv0 + 3;
                role = "store";
                name = "Verkäufer (Tankstelle)";
            }
            case AMMU_NATION -> {
                int v1 = Math.min(depth - 4, v0 + 17);
                u = uc;
                v = v1 - 3;
                role = "weapons";
                name = "Waffenhändler";
            }
            case CAR_DEALER -> {
                int v1 = Math.min(depth - 4, v0 + 18);
                u = uc;
                v = v1 - 1;
                role = "cars";
                name = "Autoverkäufer";
            }
            case JOB -> {
                int v1 = Math.min(depth - 4, v0 + 15);
                u = uc;
                v = v1 - 3;
                role = "jobs";
                name = JOB_NAMES[(int) Math.floorMod(h, JOB_NAMES.length)] + " (Jobcenter)";
            }
            case DOCKS -> {
                int v1 = Math.min(depth - 4, v0 + 15);
                u = uc;
                v = v1 - 3;
                role = "shady";
                name = SHADY_NAMES[(int) Math.floorMod(h, SHADY_NAMES.length)] + " (Hafenbüro)";
            }
            default -> {
                return null;
            }
        }
        var out = f.out();
        return new Spot(f.x(u, v) + 0.5, CityLayout.GROUND + 2.0, f.z(u, v) + 0.5, out.toYRot(), role, name);
    }

    /** Keeps a clerk at every counter near a player. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 40) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!CityEvents.isCity(player.level())) {
                continue;
            }
            ServerLevel level = (ServerLevel) player.level();
            long now = level.getGameTime();
            for (CityMap.Place place : CityMap.places()) {
                if (!hasClerk(place.kind())) {
                    continue;
                }
                double dx = place.x() - player.getX(), dz = place.z() - player.getZ();
                if (dx * dx + dz * dz > 70.0 * 70.0) {
                    continue;
                }
                Spot spot = spot(place);
                if (spot == null || !level.hasChunkAt(BlockPos.containing(spot.x(), spot.y(), spot.z()))
                        || !level.isPositionEntityTicking(BlockPos.containing(spot.x(), spot.y(), spot.z()))) {
                    continue;
                }
                AABB box = AABB.ofSize(new net.minecraft.world.phys.Vec3(spot.x(), spot.y() + 1, spot.z()), 4, 4, 4);
                boolean present = !level.getEntitiesOfClass(NpcEntity.class, box,
                        n -> n.isAlive() && spot.role().equals(n.role())).isEmpty();
                if (present) {
                    continue;
                }
                Long last = LAST_SPAWN.get(place.id());
                if (last != null && now - last < RESPAWN) {
                    continue;
                }
                LAST_SPAWN.put(place.id(), now);
                if (!counterNear(level, spot)) {
                    continue; // world from an older version: a different building stands here
                }
                lightUp(level, place);
                spawn(level, spot);
            }
        }
    }

    /** The counter / desk the clerk stands behind. */
    public static boolean counterNear(ServerLevel level, Spot spot) {
        BlockPos c = BlockPos.containing(spot.x(), spot.y(), spot.z());
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
            var block = level.getBlockState(q).getBlock();
            if (block instanceof de.gtacity.block.ShopCounterBlock || block instanceof de.gtacity.block.JobDeskBlock) {
                return true;
            }
        }
        return false;
    }

    /**
     * Shops generated by older versions of the mod had no ceiling lamps. Invisible light blocks under the ceiling
     * make them bright without changing how they look.
     */
    public static void lightUp(ServerLevel level, CityMap.Place place) {
        Lot lot = place.lot();
        Lot.Frame f = lot.frame();
        int u0 = 4, u1 = f.width - 5, v0 = 5;
        int v1 = place.kind() == CityMap.Kind.GAS_STATION ? f.depth - 4 : Math.min(f.depth - 5, v0 + 16);
        if (place.kind() == CityMap.Kind.GAS_STATION) {
            v0 = lot.size >= 39 ? f.depth - 12 : f.depth - 9;
        }
        int y = CityLayout.GROUND + 4;
        var light = net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15);
        for (int u = u0; u <= u1; u += 4) {
            for (int v = v0; v <= v1; v += 4) {
                BlockPos pos = new BlockPos(f.x(u, v), y, f.z(u, v));
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isSolid()) {
                    level.setBlock(pos, light, 2);
                }
            }
        }
    }

    private static boolean hasClerk(CityMap.Kind kind) {
        return switch (kind) {
            case STORE, GAS_STATION, AMMU_NATION, CAR_DEALER, JOB, DOCKS -> true;
            default -> false;
        };
    }

    public static NpcEntity spawn(ServerLevel level, Spot spot) {
        NpcEntity npc = ModEntities.PEDESTRIAN.create(level, EntitySpawnReason.EVENT);
        if (npc == null) {
            return null;
        }
        npc.snapTo(spot.x(), spot.y(), spot.z(), spot.yaw(), 0.0F);
        npc.randomizeLook(false);
        npc.setRole(spot.role(), spot.name());
        npc.setNoAi(true);
        npc.setYHeadRot(spot.yaw());
        npc.setYBodyRot(spot.yaw());
        level.addFreshEntity(npc);
        return npc;
    }

    /** Right click on a clerk. Returns false if the figure has no task. */
    public static boolean talk(ServerPlayer player, NpcEntity npc) {
        switch (npc.role()) {
            case "store" -> ModNetworking.openShop(player, ShopType.STORE, npc.blockPosition());
            case "weapons" -> ModNetworking.openShop(player, ShopType.WEAPONS, npc.blockPosition());
            case "cars" -> ModNetworking.openShop(player, ShopType.CARS, npc.blockPosition());
            case "jobs" -> Jobs.openBoard(player, Jobs.Station.JOBCENTER);
            case "shady" -> Jobs.openBoard(player, Jobs.Station.SHADY);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static List<CityMap.Place> placesWithClerks() {
        return CityMap.places().stream().filter(p -> hasClerk(p.kind())).toList();
    }
}
