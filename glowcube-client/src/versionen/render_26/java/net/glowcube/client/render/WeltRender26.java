package net.glowcube.client.render;

import net.glowcube.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fassung fuer <b>26.3 und neuer</b> von {@link WeltRender}. Ab 26.x gibt es
 * kein sofortiges Zeichnen in einen Puffer mehr; Linien und Kaesten laufen
 * ueber Minecrafts Gizmos (dieselben, mit denen das Spiel seine Debug-Kaesten
 * zeichnet). Gesammelt wird in der Sammlung des aktuellen Bildes - siehe
 * {@link RenderBruecke}.
 */
public final class WeltRender26 implements WeltRender {
    @Override
    public void box(AABB box, int farbe, boolean schimmer) {
        if (schimmer) {
            Gizmos.cuboid(box.inflate(0.03), GizmoStyle.stroke(ColorUtil.fade(farbe, 0.35f))).setAlwaysOnTop();
        }
        Gizmos.cuboid(box, GizmoStyle.stroke(farbe)).setAlwaysOnTop();
    }

    @Override
    public void tracer(Vec3 ziel, int farbe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3 auge = mc.player.getEyePosition();
        Vec3 blick = mc.player.getViewVector(1.0f);
        Vec3 start = auge.add(blick.x * 0.6, blick.y * 0.6, blick.z * 0.6);
        Gizmos.line(start, ziel, farbe).setAlwaysOnTop();
    }

    @Override
    public void linie(Vec3 von, Vec3 bis, int farbe) {
        Gizmos.line(von, bis, farbe).setAlwaysOnTop();
    }
}
