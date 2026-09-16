package net.glowcube.client.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Movement;
import net.minecraft.world.phys.Vec3;

/**
 * Zwei Wege: ABILITIES setzt das Kreativ-Flugrecht clientseitig (weich, aber
 * fuer jeden Server offensichtlich), MOTION schiebt den Spieler jeden Tick
 * selbst - das sieht von aussen nach normalem Laufen in der Luft aus.
 */
public final class Flight extends Module {
    private final ModeSetting mode = register(new ModeSetting("Mode",
            "Wie geflogen wird", "Motion", "Motion", "Abilities"));
    private final NumberSetting speed = register(new NumberSetting("Speed",
            "Bloecke pro Tick", 0.6, 0.05, 3.0, 0.05));
    private final NumberSetting vertical = register(new NumberSetting("Vertical",
            "Steigen und Sinken", 0.5, 0.05, 3.0, 0.05));

    public Flight() {
        super("Flight", "Fliegen ohne Kreativmodus", Category.MOVEMENT, InputConstants.KEY_F);
    }

    @Override
    public void onTick() {
        if (mode.is("Abilities")) {
            player().getAbilities().mayfly = true;
            player().getAbilities().flying = true;
            player().getAbilities().setFlyingSpeed(speed.getFloat() * 0.5f);
            player().resetFallDistance();
            return;
        }

        Vec3 horizontal = Movement.direction(player(), speed.get());
        double y = 0.0;
        if (Movement.jumping()) {
            y += vertical.get();
        }
        if (Movement.sneaking()) {
            y -= vertical.get();
        }
        player().setDeltaMovement(horizontal.x, y, horizontal.z);
        player().setOnGround(false);
        player().resetFallDistance();
    }

    @Override
    public void onDisable() {
        if (!inGame()) {
            return;
        }
        // Im Kreativmodus darf das Flugrecht bleiben, sonst muss es weg.
        if (!player().isCreative() && !player().isSpectator()) {
            player().getAbilities().mayfly = false;
            player().getAbilities().flying = false;
            player().getAbilities().setFlyingSpeed(0.05f);
        }
    }

    @Override
    public String hudSuffix() {
        return mode.get();
    }
}
