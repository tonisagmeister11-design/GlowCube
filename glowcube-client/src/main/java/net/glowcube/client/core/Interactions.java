package net.glowcube.client.core;

import net.glowcube.client.GlowCubeClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Derselbe Torwaechter wie {@link Packets}, nur fuer die vier Wege, ueber die
 * der Client meldet, was der Spieler mit der Welt vorhat. Ein Fehler hier
 * wuerde beim Klicken das Spiel anhalten - deshalb faengt jeder Aufruf ab und
 * laesst im Zweifel durch.
 */
public final class Interactions {
    private Interactions() {
    }

    public static boolean blockBreak(BlockPos pos) {
        ModuleManager modules = GlowCubeClient.modules();
        if (modules == null) {
            return false;
        }
        try {
            return modules.onBlockBreak(pos);
        } catch (Throwable fehler) {
            GlowCubeClient.LOGGER.error("Fehler beim Abbau-Haken", fehler);
            return false;
        }
    }

    public static boolean blockUse(BlockHitResult treffer, InteractionHand hand) {
        ModuleManager modules = GlowCubeClient.modules();
        if (modules == null) {
            return false;
        }
        try {
            return modules.onBlockUse(treffer, hand);
        } catch (Throwable fehler) {
            GlowCubeClient.LOGGER.error("Fehler beim Block-Haken", fehler);
            return false;
        }
    }

    public static boolean entityAttack(Entity ziel) {
        ModuleManager modules = GlowCubeClient.modules();
        if (modules == null) {
            return false;
        }
        try {
            return modules.onEntityAttack(ziel);
        } catch (Throwable fehler) {
            GlowCubeClient.LOGGER.error("Fehler beim Schlag-Haken", fehler);
            return false;
        }
    }

    public static boolean entityUse(Entity ziel, InteractionHand hand) {
        ModuleManager modules = GlowCubeClient.modules();
        if (modules == null) {
            return false;
        }
        try {
            return modules.onEntityUse(ziel, hand);
        } catch (Throwable fehler) {
            GlowCubeClient.LOGGER.error("Fehler beim Wesen-Haken", fehler);
            return false;
        }
    }
}
