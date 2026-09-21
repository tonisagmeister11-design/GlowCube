package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.NumberSetting;
import net.minecraft.world.phys.Vec3;

/**
 * Sanft gleiten statt fallen.
 *
 * <p>Nachgebildet dem {@code Glide} aus Aoba (GPL-3.0): waehrend man in der
 * Luft ist und faellt, wird die Sinkgeschwindigkeit auf einen kleinen festen
 * Wert gedeckelt und die Vorwaertsbewegung leicht getragen. Man kommt weiter
 * und landet ohne Schaden.
 */
public final class Glide extends Module {
    private final NumberSetting sinken = register(new NumberSetting("Sinken",
            "Wie schnell man hoechstens faellt", 0.1, 0.02, 0.5, 0.01));
    private final NumberSetting trift = register(new NumberSetting("Trift",
            "Wie stark die Vorwaertsbewegung getragen wird", 1.03, 1.0, 1.2, 0.01));

    public Glide() {
        super("Glide", "Sanft gleiten statt fallen", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (player().onGround() || player().isInWater() || player().onClimbable()) {
            return;
        }
        Vec3 v = player().getDeltaMovement();
        if (v.y < -sinken.get()) {
            player().setDeltaMovement(v.x * trift.get(), -sinken.get(), v.z * trift.get());
            player().fallDistance = 0.0f;
        }
    }
}
