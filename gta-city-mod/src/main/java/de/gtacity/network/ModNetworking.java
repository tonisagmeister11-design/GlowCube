package de.gtacity.network;

import de.gtacity.entity.CarEntity;
import de.gtacity.item.Weapons;
import de.gtacity.shop.ShopCatalog;
import de.gtacity.shop.ShopType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Fire.TYPE, Payloads.Fire.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Reload.TYPE, Payloads.Reload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Horn.TYPE, Payloads.Horn.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Buy.TYPE, Payloads.Buy.CODEC);
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
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Horn.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player.getVehicle() instanceof CarEntity car) {
                player.level().playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(),
                        SoundSource.PLAYERS, 2.0F, 0.75F);
                player.level().playSound(null, car.getX(), car.getY(), car.getZ(),
                        SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
            }
        });
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
