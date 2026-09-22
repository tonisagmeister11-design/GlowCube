package net.glowcube.client.module.hud;

import net.glowcube.client.hud.TextHudModul;

/**
 * Ob du gerade schleichst oder sprintest - nach AxolotlClients
 * {@code ToggleSprintHud}. Zeigt nichts, solange du normal laeufst.
 */
public final class BewegungHud extends TextHudModul {
    public BewegungHud() {
        super("Bewegung", "Zeigt, ob du schleichst oder sprintest");
    }

    @Override
    protected String text() {
        if (mc.player == null) {
            return "";
        }
        if (mc.options.keyShift.isDown()) {
            return "Schleichen (Taste)";
        }
        if (mc.player.isShiftKeyDown()) {
            return "Schleichen";
        }
        if (mc.options.keySprint.isDown()) {
            return "Sprinten (Taste)";
        }
        if (mc.player.isSprinting()) {
            return "Sprinten";
        }
        return "";
    }
}
