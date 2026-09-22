package net.glowcube.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.glowcube.client.core.ModuleManager;
import net.glowcube.client.hud.HudRenderer;
import net.minecraft.resources.Identifier;

/**
 * Fassung fuer <b>26.3 und neuer</b>: das HUD haengt ab 26.x an Fabrics
 * {@code HudElementRegistry} statt am alten {@code HudRenderCallback}. Das
 * uebergebene Grafikobjekt ist der neue {@code GuiGraphicsExtractor} - genau
 * das, worauf {@code HudRenderer} und {@code Render2D} hier gebaut sind.
 *
 * <p>Das Welt-Rendern (3D-ESP) laeuft ueber Mojangs neues Submit-Node-System
 * und wird als naechstes angebunden; bis dahin haengt hier nur das HUD ein.
 * Alle Nicht-Optik-Funktionen laufen davon unabhaengig.
 */
public final class RenderBruecke {
    private RenderBruecke() {
    }

    public static void registriere(ModuleManager modules, HudRenderer hud) {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("glowcube", "hud"),
                (gfx, deltaTracker) -> hud.render(gfx));
        net.glowcube.client.GlowCubeClient.LOGGER.info(
                "GlowCube: HUD auf 26.3 angebunden; Welt-ESP folgt.");
    }
}
