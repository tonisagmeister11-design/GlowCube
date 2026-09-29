package de.gtacity.gameplay;

import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.registry.ModAttachments;
import de.gtacity.registry.ModEntities;
import de.gtacity.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
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

    /** The car the player drove last, owned or not - the B key brings this one. */
    private static final Map<UUID, UUID> LAST_DRIVEN = new HashMap<>();

    public static void drove(ServerPlayer player, CarEntity car) {
        LAST_DRIVEN.put(player.getUUID(), car.getUUID());
    }

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

    /** Key G in a car: this car is yours now - it goes into the garage and B brings it back any time. */
    public static void claim(ServerPlayer player) {
        if (!(player.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != player) {
            player.sendOverlayMessage(Component.literal("Setz dich ans Steuer des Autos, das du behalten willst.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (car.isOwnedBy(player)) {
            player.sendOverlayMessage(Component.literal("Das ist schon dein Auto. Mit B holst du es zu dir.")
                    .withStyle(ChatFormatting.GRAY));
            DELIVERED.put(player.getUUID(), car.getUUID());
            return;
        }
        add(player, car.getVariant());
        player.setAttached(ModAttachments.PERSONAL_CAR, cars(player).size() - 1);
        car.setOwner(player.getUUID());
        car.setPersistentCar(true);
        car.setAiDriving(false, null);
        car.setSiren(false);
        DELIVERED.put(player.getUUID(), car.getUUID());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CASH,
                SoundSource.PLAYERS, 0.8F, 1.2F);
        player.sendSystemMessage(Component.literal("Dieser " + car.getVariant().shape.label + " gehört jetzt dir! "
                + "Mit B holst du ihn jederzeit zu dir, in der Garage (M) steht er auch.").withStyle(ChatFormatting.GREEN));
    }

    /** Key B: your car comes to you - the one you drove last, or the last one from the garage. */
    public static void bring(ServerPlayer player) {
        if (player.getVehicle() instanceof CarEntity) {
            player.sendOverlayMessage(Component.literal("Du sitzt doch schon in einem Auto.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        for (UUID current : new UUID[]{LAST_DRIVEN.get(player.getUUID()), DELIVERED.get(player.getUUID())}) {
            if (current == null) {
                continue;
            }
            for (ServerLevel level : player.level().getServer().getAllLevels()) {
                if (level.getEntity(current) instanceof CarEntity car && car.isAlive() && level == player.level()) {
                    double[] spot = streetNextTo(level, player);
                    if (spot == null) {
                        player.sendOverlayMessage(Component.literal("Geh zu einer Straße, dann kommt dein Auto.")
                                .withStyle(ChatFormatting.RED));
                        return;
                    }
                    car.ejectPassengers();
                    car.speed = 0.0F;
                    car.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    car.snapTo(spot[0], CityLayout.GROUND + 1.0, spot[1], (float) spot[2], 0.0F);
                    player.sendOverlayMessage(Component.literal("Dein " + car.getVariant().shape.label
                            + " steht an der Straße bereit.").withStyle(ChatFormatting.GREEN));
                    return;
                }
            }
        }
        List<Integer> list = cars(player);
        if (list.isEmpty()) {
            player.sendOverlayMessage(Component.literal("Dein letztes Auto ist weg. Mit G im Auto behältst du "
                    + "es für immer - dann kommt es mit B immer wieder.").withStyle(ChatFormatting.RED));
            return;
        }
        Integer personal = player.getAttached(ModAttachments.PERSONAL_CAR);
        int index = personal != null && personal >= 0 && personal < list.size() ? personal : list.size() - 1;
        call(player, index);
    }

    /** An owned car that is no longer the one its owner uses (another was called meanwhile) goes back. */
    public static boolean isReplaced(CarEntity car, UUID owner) {
        UUID current = DELIVERED.get(owner);
        return current != null && !current.equals(car.getUUID());
    }

    public static void call(ServerPlayer player, int index) {
        List<Integer> list = cars(player);
        if (index < 0 || index >= list.size()) {
            return;
        }
        player.setAttached(ModAttachments.PERSONAL_CAR, index);
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
        LAST_DRIVEN.remove(player.getUUID());
    }
}
