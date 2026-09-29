package de.gtacity.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.gtacity.GtaCity;
import de.gtacity.client.screen.CityMapScreen;
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
    public static final KeyMapping MAP = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gtacity.map",
            InputConstants.KEY_M, CATEGORY));

    private static CameraType cameraBeforeCar;
    private static boolean wasInCar;

    /** Shift held this long while the car stands still gets the player out, like before. */
    private static final int SHIFT_EXIT_TICKS = 12;
    private static int shiftStillTicks;

    public static void init() {
        CarEntity.crashReporter = damage -> ClientPlayNetworking.send(new Payloads.CarCrash(damage));

        // F gets in and out of cars. It runs before vanilla handles its keys, so F next to a car does not also
        // swap the hands.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null || client.gui.screen() != null) {
                return;
            }
            boolean nearCar = player.getVehicle() instanceof CarEntity || !player.isPassenger()
                    && !player.level().getEntitiesOfClass(CarEntity.class, player.getBoundingBox().inflate(3.5))
                    .isEmpty();
            if (nearCar) {
                boolean pressed = false;
                while (client.options.keySwapOffhand.consumeClick()) {
                    pressed = true;
                }
                if (pressed) {
                    ClientPlayNetworking.send(new Payloads.CarDoor());
                }
            }
            if (player.getVehicle() instanceof CarEntity car && player.isShiftKeyDown()
                    && Math.abs(car.measuredSpeed) < 0.03F) {
                if (++shiftStillTicks == SHIFT_EXIT_TICKS) {
                    ClientPlayNetworking.send(new Payloads.CarDoor());
                }
            } else {
                shiftStillTicks = 0;
            }
        });

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
            while (MAP.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new CityMapScreen());
                }
            }
            updateCamera(client, player);
            VehicleSounds.tick(client);
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
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    "F: Aussteigen   Shift: Handbremse / Driften   H: Hupe"));
        } else if (!inCar && wasInCar && cameraBeforeCar != null) {
            client.options.setCameraType(cameraBeforeCar);
        }
        wasInCar = inCar;
    }
}
