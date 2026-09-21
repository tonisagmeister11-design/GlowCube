package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Movement;
import net.minecraft.world.phys.Vec3;

/**
 * Hoeher springen.
 *
 * <p>Nachgebildet dem {@code HighJump} aus Aoba (GPL-3.0): im Moment des
 * Absprungs vom Boden wird die senkrechte Geschwindigkeit erhoeht. 0,42
 * entspricht dem normalen Sprung, alles darueber springt weiter hinauf.
 */
public final class HighJump extends Module {
    private final NumberSetting hoehe = register(new NumberSetting("Hoehe",
            "Absprunggeschwindigkeit (0,42 = normal)", 0.7, 0.42, 2.0, 0.01));

    public HighJump() {
        super("HighJump", "Hoeher springen", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        // Nur genau beim Absprung: am Boden und die Sprungtaste gedrueckt.
        if (!player().onGround() || !Movement.jumping()) {
            return;
        }
        Vec3 v = player().getDeltaMovement();
        player().setDeltaMovement(v.x, hoehe.get(), v.z);
    }
}
