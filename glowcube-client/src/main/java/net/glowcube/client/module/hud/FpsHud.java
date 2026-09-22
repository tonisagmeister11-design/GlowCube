package net.glowcube.client.module.hud;

import net.glowcube.client.hud.TextHudModul;
import net.glowcube.client.mixin.MinecraftAccessor;

/** Bilder pro Sekunde - aus AxolotlClient ({@code FPSHud}, nach KronHUD). */
public final class FpsHud extends TextHudModul {
    public FpsHud() {
        super("FPS", "Zeigt die Bildrate");
    }

    @Override
    protected String text() {
        return MinecraftAccessor.glowcube$fps() + " FPS";
    }
}
