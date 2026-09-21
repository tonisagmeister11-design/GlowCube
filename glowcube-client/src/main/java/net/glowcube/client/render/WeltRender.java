package net.glowcube.client.render;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Die Zeichen-Schnittstelle fuer die Welt (3D), gebuendelt an einem Ort.
 *
 * <p>Warum eine eigene Schnittstelle: das Welt-Rendern hat sich zwischen den
 * Spielfassungen grundlegend geaendert. Bis 1.21.x liefert Fabric einen
 * {@code WorldRenderContext} mit {@code MultiBufferSource} und
 * {@code PoseStack}; ab 26.x ist beides gestrichen und durch ein neues
 * "Submit-Node"-System ersetzt. Die ESP-Module sollen davon nichts wissen -
 * sie sagen nur "zeichne eine Box hier" und diese Schnittstelle kuemmert sich
 * fassungsabhaengig um das Wie. So bleibt {@link net.glowcube.client.core.Module}
 * und jedes Render-Modul versionsneutral; nur die Umsetzung
 * ({@code render_1_21} bzw. {@code render_26}) unterscheidet sich.
 */
public interface WeltRender {
    /** Kasten um eine Box; mit Schimmer eine blasse groessere Box dahinter. */
    void box(AABB box, int farbe, boolean schimmer);

    /** Linie von der Blickmitte zu einem Punkt in der Welt. */
    void tracer(Vec3 ziel, int farbe);

    /** Einzelne Linie zwischen zwei Weltpunkten. */
    void linie(Vec3 von, Vec3 bis, int farbe);
}
