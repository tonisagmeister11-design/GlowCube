package net.glowcube.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.glowcube.client.core.ModuleManager;
import net.glowcube.client.hud.HudRenderer;

/**
 * Fassung fuer <b>1.21.x</b>: haengt das Welt- und HUD-Rendern an Fabrics
 * Ereignisse. Diese Kennungen ({@code WorldRenderEvents}, {@code HudRenderCallback})
 * gibt es ab 26.x nicht mehr - deshalb liegt die Anbindung hier je Fassung
 * getrennt, statt fest in {@code GlowCubeClient}.
 */
public final class RenderBruecke {
    private RenderBruecke() {
    }

    public static void registriere(ModuleManager modules, HudRenderer hud) {
        WorldRenderEvents.AFTER_ENTITIES.register(
                context -> modules.onWorldRender(new WeltRender1_21(context)));
        HudRenderCallback.EVENT.register((gfx, tickCounter) -> hud.render(gfx));
    }
}
