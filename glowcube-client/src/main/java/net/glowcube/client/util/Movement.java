package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Bewegungseingabe direkt von den Tastenbelegungen gelesen. Absichtlich nicht
 * ueber das Input-Objekt des Spielers - dessen Aufbau aendert sich staendig,
 * die KeyMappings in den Options nicht.
 */
public final class Movement {
    private Movement() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static double forward() {
        return (mc().options.keyUp.isDown() ? 1.0 : 0.0) - (mc().options.keyDown.isDown() ? 1.0 : 0.0);
    }

    public static double strafe() {
        return (mc().options.keyLeft.isDown() ? 1.0 : 0.0) - (mc().options.keyRight.isDown() ? 1.0 : 0.0);
    }

    public static boolean jumping() {
        return mc().options.keyJump.isDown();
    }

    public static boolean sneaking() {
        return mc().options.keyShift.isDown();
    }

    public static boolean moving() {
        return forward() != 0.0 || strafe() != 0.0;
    }

    /** Die Eingabe in Weltkoordinaten, auf {@code speed} normiert. Y bleibt 0. */
    public static Vec3 direction(Player player, double speed) {
        double forward = forward();
        double strafe = strafe();
        if (forward == 0.0 && strafe == 0.0) {
            return Vec3.ZERO;
        }
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw);
        double cos = Mth.cos(yaw);
        double length = Math.sqrt(forward * forward + strafe * strafe);
        double x = (strafe * cos - forward * sin) / length * speed;
        double z = (forward * cos + strafe * sin) / length * speed;
        return new Vec3(x, 0.0, z);
    }
}
