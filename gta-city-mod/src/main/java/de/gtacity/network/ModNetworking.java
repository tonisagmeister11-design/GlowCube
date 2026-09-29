package de.gtacity.network;

import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.registry.ModSounds;
import de.gtacity.gameplay.Garage;
import de.gtacity.gameplay.Jobs;
import de.gtacity.gameplay.Villas;
import de.gtacity.item.Weapons;
import de.gtacity.shop.ShopCatalog;
import de.gtacity.shop.ShopType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Fire.TYPE, Payloads.Fire.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Reload.TYPE, Payloads.Reload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Horn.TYPE, Payloads.Horn.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Buy.TYPE, Payloads.Buy.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.CarDoor.TYPE, Payloads.CarDoor.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.CarCrash.TYPE, Payloads.CarCrash.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Phone.TYPE, Payloads.Phone.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenShop.TYPE, Payloads.OpenShop.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.ShotFx.TYPE, Payloads.ShotFx.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.Recoil.TYPE, Payloads.Recoil.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Payloads.Fire.TYPE,
                (payload, context) -> Weapons.tryFire(context.player(), payload.held()));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Reload.TYPE,
                (payload, context) -> Weapons.reload(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Buy.TYPE, (payload, context) -> {
            ShopType[] types = ShopType.values();
            if (payload.shop() >= 0 && payload.shop() < types.length) {
                ShopCatalog.buy(context.player(), types[payload.shop()], payload.index());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Phone.TYPE,
                (payload, context) -> phone(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.CarDoor.TYPE,
                (payload, context) -> carDoor(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.CarCrash.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player.getVehicle() instanceof CarEntity car && car.getControllingPassenger() == player
                    && Float.isFinite(payload.damage()) && payload.damage() > 0) {
                car.onReportedCrash(player.level(), payload.damage());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Horn.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player.getVehicle() instanceof CarEntity car) {
                player.level().playSound(null, car.getX(), car.getY(), car.getZ(), ModSounds.HORN,
                        SoundSource.PLAYERS, 2.5F, car.getVariant().shape == CarVariant.Shape.SUPER ? 1.15F : 1.0F);
            }
        });
    }

    public static void phone(ServerPlayer player, Payloads.Phone payload) {
        switch (payload.action()) {
            case Payloads.Phone.BUY_VILLA -> Villas.buy(player, payload.arg());
            case Payloads.Phone.VILLA_TELEPORT -> Villas.teleport(player, payload.arg());
            case Payloads.Phone.CALL_CAR -> Garage.call(player, (int) payload.arg());
            case Payloads.Phone.START_JOB -> {
                Jobs.Type[] types = Jobs.Type.values();
                if (payload.arg() >= 0 && payload.arg() < types.length) {
                    Jobs.start(player, types[(int) payload.arg()]);
                }
            }
            case Payloads.Phone.CANCEL_JOB -> Jobs.cancel(player, "abgebrochen.");
            case Payloads.Phone.CLAIM_CAR -> Garage.claim(player);
            case Payloads.Phone.BRING_CAR -> Garage.bring(player);
            default -> {
            }
        }
    }

    /** F: out of the car, or into the car the player looks at (or the nearest one right next to them). */
    public static void carDoor(ServerPlayer player) {
        if (player.getVehicle() instanceof CarEntity) {
            player.stopRiding();
            return;
        }
        if (player.isPassenger()) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        CarEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (CarEntity car : player.level().getEntitiesOfClass(CarEntity.class, player.getBoundingBox().inflate(4.0))) {
            Vec3 to = car.position().add(0, 0.6, 0).subtract(eye);
            double distance = to.length();
            double score = distance - to.normalize().dot(look) * 3.0;
            if (distance < 4.5 && score < bestScore) {
                best = car;
                bestScore = score;
            }
        }
        if (best != null) {
            best.interact(player, InteractionHand.MAIN_HAND, best.position());
        }
    }

    public static void openShop(ServerPlayer player, ShopType type, BlockPos counter) {
        ServerPlayNetworking.send(player, new Payloads.OpenShop(type.ordinal()));
    }

    public static void shotEffect(ServerLevel level, Vec3 from, Vec3 to) {
        Payloads.ShotFx fx = new Payloads.ShotFx(from.x, from.y, from.z, to.x, to.y, to.z);
        for (ServerPlayer player : PlayerLookup.around(level, from, 96.0)) {
            ServerPlayNetworking.send(player, fx);
        }
    }

    public static void recoil(ServerPlayer player, float amount) {
        ServerPlayNetworking.send(player, new Payloads.Recoil(amount));
    }
}
