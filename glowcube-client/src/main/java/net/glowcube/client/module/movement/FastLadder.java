package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Movement;
import net.minecraft.world.phys.Vec3;

/**
 * Leitern schneller hinauf- und hinuntersteigen.
 *
 * <p>Nachgebildet dem {@code FastLadder} aus Aoba (GPL-3.0): haengt man an
 * einer Leiter oder Ranke und drueckt vor/zurueck, wird die senkrechte
 * Geschwindigkeit erhoeht.
 */
public final class FastLadder extends Module {
    private final NumberSetting tempo = register(new NumberSetting("Tempo",
            "Steiggeschwindigkeit (0,12 = normal)", 0.35, 0.12, 0.8, 0.01));

    public FastLadder() {
        super("FastLadder", "Leitern schneller steigen", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (!player().onClimbable()) {
            return;
        }
        Vec3 v = player().getDeltaMovement();
        if (Movement.forward() > 0) {
            player().setDeltaMovement(v.x, tempo.get(), v.z);
        } else if (Movement.forward() < 0 || player().isShiftKeyDown()) {
            player().setDeltaMovement(v.x, -tempo.get(), v.z);
        }
    }
}
