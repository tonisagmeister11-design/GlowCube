package net.glowcube.client.render;

import net.glowcube.client.core.ModuleManager;
import net.glowcube.client.hud.HudRenderer;

/**
 * Fassung fuer <b>26.3 und neuer</b>: die Welt-/HUD-Anbindung wird gerade auf
 * das neue Submit-Node-Rendersystem umgebaut. Bis dahin haengt hier noch
 * nichts ein - alle Nicht-Optik-Funktionen (Exploits, Bewegung, Kampf,
 * Play-for-me) laufen unabhaengig davon.
 */
public final class RenderBruecke {
    private RenderBruecke() {
    }

    public static void registriere(ModuleManager modules, HudRenderer hud) {
        net.glowcube.client.GlowCubeClient.LOGGER.info(
                "GlowCube: Welt- und HUD-Rendern auf 26.3 noch im Aufbau.");
    }
}
