package net.glowcube.client.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.util.Movement;

/**
 * Haelt beim Laufen den Sprung gedrueckt: sobald der Spieler wieder Boden hat
 * und sich waagerecht bewegt, springt er sofort erneut. Zusammen mit Sprint
 * gibt das den durchgehenden "Bunny-Hop", ohne dass man die Leertaste
 * haemmern muss. Reine Bewegungslogik - laeuft auf 1.21.11 wie auf 26.3.
 */
public final class BunnyHop extends Module {
    private final BooleanSetting sprint = register(new BooleanSetting("Sprint",
            "Beim Hoppeln automatisch sprinten", true));

    public BunnyHop() {
        super("BunnyHop", "Springt beim Laufen von selbst weiter", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (!inGame()) {
            return;
        }
        // Nur am Boden und in Bewegung - in der Luft oder im Stand nicht, sonst
        // klebt der Sprung fest und man kommt nicht mehr sauber runter.
        boolean bewegt = Movement.moving();
        if (bewegt && player().onGround() && !Movement.sneaking()) {
            if (sprint.get() && !player().isUsingItem()) {
                player().setSprinting(true);
            }
            player().setJumping(true);
        }
    }

    @Override
    public void onDisable() {
        if (inGame()) {
            player().setJumping(false);
        }
    }
}
