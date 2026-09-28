package de.gtacity.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.gtacity.GtaCity;
import de.gtacity.entity.CarEntity;
import de.gtacity.item.GunItem;
import de.gtacity.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Gun trigger, reload, horn and the automatic third person camera in cars. */
public final class ClientInput {
    private ClientInput() {
    }

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(GtaCity.id("keys"));
    public static final KeyMapping RELOAD = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gtacity.reload",
            InputConstants.KEY_R, CATEGORY));
    public static final KeyMapping HORN = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gtacity.horn",
            InputConstants.KEY_H, CATEGORY));

    private static CameraType cameraBeforeCar;
    private static boolean wasInCar;

    public static void init() {
        // Left click with a gun shoots instead of punching / breaking blocks.
        ClientPreAttackCallback.EVENT.register((client, player, clicks) -> {
            if (!(player.getMainHandItem().getItem() instanceof GunItem)) {
                return false;
            }
            if (clicks > 0) {
                ClientPlayNetworking.send(new Payloads.Fire(false));
            }
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null) {
                return;
            }
            boolean gun = player.getMainHandItem().getItem() instanceof GunItem g && g.type.automatic;
            if (gun && client.gui.screen() == null && client.options.keyAttack.isDown()) {
                ClientPlayNetworking.send(new Payloads.Fire(true));
            }
            while (RELOAD.consumeClick()) {
                ClientPlayNetworking.send(new Payloads.Reload());
            }
            while (HORN.consumeClick()) {
                ClientPlayNetworking.send(new Payloads.Horn());
            }
            updateCamera(client, player);
        });
    }

    public static boolean isAiming() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.gui.screen() == null && mc.options.keyUse.isDown()
                && mc.player.getMainHandItem().getItem() instanceof GunItem;
    }

    private static void updateCamera(Minecraft client, LocalPlayer player) {
        boolean inCar = player.getVehicle() instanceof CarEntity;
        if (inCar && !wasInCar) {
            cameraBeforeCar = client.options.getCameraType();
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!inCar && wasInCar && cameraBeforeCar != null) {
            client.options.setCameraType(cameraBeforeCar);
        }
        wasInCar = inCar;
    }
}
