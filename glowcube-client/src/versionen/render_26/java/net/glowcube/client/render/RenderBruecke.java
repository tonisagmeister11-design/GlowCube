package net.glowcube.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.glowcube.client.core.ModuleManager;
import net.glowcube.client.hud.HudRenderer;
import net.minecraft.resources.Identifier;

/**
 * Fassung fuer <b>26.3 und neuer</b>: das HUD haengt ab 26.x an Fabrics
 * {@code HudElementRegistry} statt am alten {@code HudRenderCallback}. Das
 * uebergebene Grafikobjekt ist der neue {@code GuiGraphicsExtractor} - genau
 * das, worauf {@code HudRenderer} und {@code Render2D} hier gebaut sind.
 *
 * <p>Das Welt-Rendern (3D-ESP) haengt an {@code LevelRenderEvents.COLLECT_SUBMITS}
 * und zeichnet ueber Gizmos ({@link WeltRender26}).
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
 *   <li>Fabric bietet {@code LevelRenderEvents} (in {@code rendering.v1.level}).</li>
 *   <li>Der Blockrahmen laeuft ueber {@code BlockOutlineRenderState}.</li>
 * </ul>
 * Statt eigene Submit-Nodes zu bauen, nutzt das ESP Minecrafts Gizmos - die
 * kuemmern sich um Vertex-Format und Linienbreite selbst.
 */
public final class RenderBruecke {
    private RenderBruecke() {
    }

    public static void registriere(ModuleManager modules, HudRenderer hud) {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("glowcube", "hud"),
                (gfx, deltaTracker) -> hud.render(gfx));
        // Welt-ESP: waehrend das Bild seine Zeichenbefehle sammelt, landen
        // unsere Kaesten und Linien als Gizmos in der Sammlung dieses Bildes.
        LevelRenderEvents.COLLECT_SUBMITS.register(kontext -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.levelRenderer == null || mc.player == null) {
                return;
            }
            try (Gizmos.TemporaryCollection sammlung = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
                modules.onWorldRender(new WeltRender26());
            }
        });
        net.glowcube.client.GlowCubeClient.LOGGER.info("GlowCube: HUD und Welt-ESP auf 26.3 angebunden.");
    }
}
