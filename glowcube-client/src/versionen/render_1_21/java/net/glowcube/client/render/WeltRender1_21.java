package net.glowcube.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.glowcube.client.util.ColorUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fassung fuer <b>1.21.x</b> von {@link WeltRender}. Zeichnet ueber Fabrics
 * {@code WorldRenderContext} mit {@code MultiBufferSource}/{@code PoseStack} -
 * genau der Code, der frueher in {@code util/Render3D} stand.
 */
public final class WeltRender1_21 implements WeltRender {
    private final WorldRenderContext context;

    public WeltRender1_21(WorldRenderContext context) {
        this.context = context;
    }

    @Override
    public void box(AABB box, int farbe, boolean schimmer) {
        MultiBufferSource consumers = context.consumers();
        PoseStack matrices = context.matrices();
        if (consumers == null || matrices == null) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        VertexConsumer lines = consumers.getBuffer(RenderTypes.lines());

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = matrices.last();

        if (schimmer) {
            AABB wide = box.inflate(0.03);
            kanten(lines, pose, wide, ColorUtil.fade(farbe, 0.35f));
        }
        kanten(lines, pose, box, farbe);

        matrices.popPose();
    }

    @Override
    public void tracer(Vec3 ziel, int farbe) {
        MultiBufferSource consumers = context.consumers();
        PoseStack matrices = context.matrices();
        if (consumers == null || matrices == null) {
            return;
        }
        Camera kamera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camera = kamera.position();
        Vec3 look = Minecraft.getInstance().player.getViewVector(1.0f);
        Vec3 start = camera.add(look.x * 0.6, look.y * 0.6, look.z * 0.6);

        VertexConsumer lines = consumers.getBuffer(RenderTypes.lines());
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        linie(lines, matrices.last(),
                (float) start.x, (float) start.y, (float) start.z,
                (float) ziel.x, (float) ziel.y, (float) ziel.z, farbe);
        matrices.popPose();
    }

    @Override
    public void linie(Vec3 von, Vec3 bis, int farbe) {
        MultiBufferSource consumers = context.consumers();
        PoseStack matrices = context.matrices();
        if (consumers == null || matrices == null) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        VertexConsumer lines = consumers.getBuffer(RenderTypes.lines());

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        linie(lines, matrices.last(),
                (float) von.x, (float) von.y, (float) von.z,
                (float) bis.x, (float) bis.y, (float) bis.z, farbe);
        matrices.popPose();
    }

    private static void kanten(VertexConsumer buffer, PoseStack.Pose pose, AABB box, int farbe) {
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        linie(buffer, pose, x1, y1, z1, x2, y1, z1, farbe);
        linie(buffer, pose, x2, y1, z1, x2, y1, z2, farbe);
        linie(buffer, pose, x2, y1, z2, x1, y1, z2, farbe);
        linie(buffer, pose, x1, y1, z2, x1, y1, z1, farbe);
        linie(buffer, pose, x1, y2, z1, x2, y2, z1, farbe);
        linie(buffer, pose, x2, y2, z1, x2, y2, z2, farbe);
        linie(buffer, pose, x2, y2, z2, x1, y2, z2, farbe);
        linie(buffer, pose, x1, y2, z2, x1, y2, z1, farbe);
        linie(buffer, pose, x1, y1, z1, x1, y2, z1, farbe);
        linie(buffer, pose, x2, y1, z1, x2, y2, z1, farbe);
        linie(buffer, pose, x2, y1, z2, x2, y2, z2, farbe);
        linie(buffer, pose, x1, y1, z2, x1, y2, z2, farbe);
    }

    private static void linie(VertexConsumer buffer, PoseStack.Pose pose,
                              float x1, float y1, float z1, float x2, float y2, float z2, int farbe) {
        int a = ColorUtil.alpha(farbe);
        int r = ColorUtil.red(farbe);
        int g = ColorUtil.green(farbe);
        int b = ColorUtil.blue(farbe);

        // Der Linientyp in 1.21.11 will je Punkt Farbe, Normale (die Richtung
        // der Linie) und Linienbreite - fehlt eins davon, bricht der Puffer beim
        // Zeichnen ab ("Missing elements in vertex: Normal, LineWidth"). Das hat
        // der Spieltest aufgedeckt.
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float laenge = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (laenge < 1.0e-6f) {
            return;
        }
        nx /= laenge;
        ny /= laenge;
        nz /= laenge;
        buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(2.0f);
        buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(2.0f);
    }
}
