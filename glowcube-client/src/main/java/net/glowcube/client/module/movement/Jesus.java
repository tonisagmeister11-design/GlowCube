package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Ueber Wasser laufen.
 *
 * <p>Nachgebildet dem {@code Jesus} aus Aoba (GPL-3.0): steht Wasser direkt
 * unter oder an den Fuessen, wird das Absacken verhindert - der Spieler bleibt
 * an der Oberflaeche und gilt als am Boden, sodass man normal darueber laufen
 * und sogar springen kann. Beim Schleichen sinkt man absichtlich ein.
 */
public final class Jesus extends Module {
    public Jesus() {
        super("Jesus", "Ueber Wasser laufen", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (player().isShiftKeyDown()) {
            return;   // Schleichen laesst einen bewusst untertauchen.
        }
        BlockPos fuesse = player().blockPosition();
        boolean wasser = !level().getFluidState(fuesse).isEmpty()
                || !level().getFluidState(fuesse.below()).isEmpty();
        if (!wasser) {
            return;
        }
        Vec3 v = player().getDeltaMovement();
        if (v.y <= 0.0) {
            player().setDeltaMovement(v.x, 0.0, v.z);
            player().setOnGround(true);
            player().fallDistance = 0.0f;
        }
    }
}
