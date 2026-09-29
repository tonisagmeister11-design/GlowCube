package de.gtacity.gameplay;

import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.registry.ModAttachments;
import de.gtacity.registry.ModEntities;
import de.gtacity.world.CityLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bought cars. They stay yours: from the map's garage tab a mechanic delivers them to the next street. */
public final class Garage {
    private Garage() {
    }

    /** The car delivered last per player - calling another one takes the old one back to the garage. */
    private static final Map<UUID, UUID> DELIVERED = new HashMap<>();

    public static List<Integer> cars(Player player) {
        List<Integer> list = player.getAttached(ModAttachments.GARAGE);
        return list == null ? List.of() : list;
    }

    public static void add(ServerPlayer player, CarVariant variant) {
        List<Integer> list = new ArrayList<>(cars(player));
        list.add(variant.ordinal());
        player.setAttached(ModAttachments.GARAGE, List.copyOf(list));
    }

    /** Remembers a freshly bought car, so calling another one later puts it back into the garage. */
    public static void delivered(ServerPlayer player, CarEntity car) {
        DELIVERED.put(player.getUUID(), car.getUUID());
    }

    public static void call(ServerPlayer player, int index) {
        List<Integer> list = cars(player);
        if (index < 0 || index >= list.size()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        UUID old = DELIVERED.remove(player.getUUID());
        if (old != null) {
            Entity previous = level.getEntity(old);
            if (previous instanceof CarEntity car && !car.hasPassenger(player)) {
                car.despawn();
            }
        }
        double[] spot = streetNextTo(level, player);
        if (spot == null) {
            player.sendOverlayMessage(Component.literal("Hier kann dir niemand ein Auto bringen - geh zu einer Straße.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        CarEntity car = ModEntities.CAR.create(level, EntitySpawnReason.EVENT);
        if (car == null) {
            return;
        }
        CarVariant variant = CarVariant.byId(list.get(index));
        car.setVariant(variant);
        car.setPersistentCar(true);
        car.setOwner(player.getUUID());
        car.snapTo(spot[0], CityLayout.GROUND + 1.0, spot[1], (float) spot[2], 0.0F);
        level.addFreshEntity(car);
        DELIVERED.put(player.getUUID(), car.getUUID());
        player.sendOverlayMessage(Component.literal("Dein " + variant.shape.label + " steht an der Straße bereit.")
                .withStyle(ChatFormatting.GREEN));
    }

    /** Free spot on the closest street (x, z, yaw), or null outside the city. */
    private static double[] streetNextTo(ServerLevel level, ServerPlayer player) {
        int px = Mth.floor(player.getX()), pz = Mth.floor(player.getZ());
        for (int r = 3; r <= 40; r++) {
            for (int i = 0; i < 16; i++) {
                double angle = Math.PI * 2 * i / 16 + r;
                int x = px + Mth.floor(Math.cos(angle) * r);
                int z = pz + Mth.floor(Math.sin(angle) * r);
                if (!CityLayout.isRoad(x, z) || CityLayout.isIntersection(x, z)) {
                    continue;
                }
                AABB box = new AABB(x - 2.5, CityLayout.GROUND, z - 2.5, x + 3.5, CityLayout.GROUND + 3, z + 3.5);
                if (!level.getEntitiesOfClass(CarEntity.class, box).isEmpty()) {
                    continue;
                }
                boolean northSouth = CityLayout.local(x) < CityLayout.CORRIDOR;
                return new double[]{x + 0.5, z + 0.5, northSouth ? 0.0 : 90.0};
            }
        }
        return null;
    }

    public static void forget(ServerPlayer player) {
        DELIVERED.remove(player.getUUID());
    }
}
