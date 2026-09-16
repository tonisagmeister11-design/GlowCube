package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.minecraft.world.phys.HitResult;

/**
 * Sorgt dafuer, dass Treffer kritisch sind.
 *
 * Kritisch ist ein Schlag, wenn der Spieler im Fallen ist. Dieses Modul gibt
 * ihm deshalb kurz vor dem Zuschlagen einen winzigen Schubs nach oben - vom
 * Spiel aus betrachtet faellt man danach, und der Treffer zaehlt doppelt.
 * Nachempfunden Criticals aus BleachHack (GPL-3.0).
 */
public final class Criticals extends Module {
    private boolean warGedrueckt;

    public Criticals() {
        super("Criticals", "Treffer zaehlen als kritisch", Category.COMBAT);
    }

    @Override
    public void onTick() {
        boolean gedrueckt = mc.options.keyAttack.isDown();
        boolean flanke = gedrueckt && !warGedrueckt;
        warGedrueckt = gedrueckt;

        if (!flanke || !player().onGround()) {
            return;
        }
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) {
            return;
        }
        // Gerade genug, um vom Boden zu kommen - ein richtiger Sprung waere
        // auffaellig und wuerde die Reichweite verziehen.
        player().setDeltaMovement(player().getDeltaMovement().x, 0.1,
                player().getDeltaMovement().z);
        player().setOnGround(false);
    }
}
