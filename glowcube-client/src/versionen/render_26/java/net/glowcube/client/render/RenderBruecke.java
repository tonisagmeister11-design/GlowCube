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
 * <p>Das Welt-Rendern (3D-ESP) haengt hier noch nicht ein - bis dahin nur das
 * HUD. Alle Nicht-Optik-Funktionen laufen davon unabhaengig.
 *
 * <p><b>Stand der 26.3-Welt-Render-Pipeline</b> (aus den Jars ausgeleuchtet):
 * Fabrics alte Haken sind weg - {@code WorldRenderEvents} und
 * {@code WorldRenderContext} gibt es nicht mehr. An ihre Stelle tritt Mojangs
 * Framegraph mit "Submit-Node"-System:
 * <ul>
 *   <li>{@code net.minecraft.client.renderer.SubmitNodeCollector} /
 *       {@code OrderedSubmitNodeCollector} - dort werden Zeichenbefehle
 *       eingereiht, statt wie frueher Vertices sofort in einen VertexConsumer
 *       zu schreiben.</li>
 *   <li>{@code LevelRenderer} haelt ein {@code submitNodeStorage} und ruft
 *       {@code submitFeatures(LevelRenderState, SubmitNodeCollector, boolean)}.</li>
 *   <li>Fabric bietet {@code FabricOrderedSubmitNodeCollector} (in
 *       {@code rendering.v1}) - der voraussichtliche Zugang fuers eigene ESP.</li>
 *   <li>Der Blockrahmen laeuft ueber {@code BlockOutlineRenderState}.</li>
 * </ul>
 * Das ESP muss also eigene Linien/Boxen als Submit-Node einreihen (nicht mehr
 * Immediate-Mode) - ein eigenes Folgeprojekt.
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
