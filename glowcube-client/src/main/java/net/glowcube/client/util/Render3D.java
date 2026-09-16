package net.glowcube.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Linien in der Welt. Die Kanten werden von Hand geschrieben statt ueber einen
 * Vanilla-Helfer - der ist zwischen den Fassungen schon mehrfach umgezogen,
 * VertexConsumer nicht.
 */
public final class Render3D {
    private Render3D() {
    }

    /** Kasten um eine Box. Zeichnet zusaetzlich eine blasse, groessere Box als Schimmer. */
    public static void box(WorldRenderContext context, AABB box, int color, boolean glow) {
        MultiBufferSource consumers = context.consumers();
        PoseStack matrices = context.matrixStack();
        if (consumers == null || matrices == null) {
            return;
        }
        Vec3 camera = context.camera().getPosition();
        VertexConsumer lines = consumers.getBuffer(RenderType.lines());

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = matrices.last();

        if (glow) {
            AABB wide = box.inflate(0.03);
            edges(lines, pose, wide, ColorUtil.fade(color, 0.35f));
        }
        edges(lines, pose, box, color);

        matrices.popPose();
    }

    /** Eine Linie von der Blickmitte zu einem Punkt in der Welt. */
    public static void tracer(WorldRenderContext context, Vec3 target, int color) {
        MultiBufferSource consumers = context.consumers();
        PoseStack matrices = context.matrixStack();
        if (consumers == null || matrices == null) {
            return;
        }
        Vec3 camera = context.camera().getPosition();
        Vector3f look = context.camera().getLookVector();
        // Startpunkt knapp vor der Kamera, sonst verschwindet die Linie in der Near-Plane.
        Vec3 start = camera.add(look.x() * 0.6, look.y() * 0.6, look.z() * 0.6);

        VertexConsumer lines = consumers.getBuffer(RenderType.lines());
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        line(lines, matrices.last(),
                (float) start.x, (float) start.y, (float) start.z,
                (float) target.x, (float) target.y, (float) target.z, color);
        matrices.popPose();
    }

    private static void edges(VertexConsumer buffer, PoseStack.Pose pose, AABB box, int color) {
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        // Boden
        line(buffer, pose, x1, y1, z1, x2, y1, z1, color);
        line(buffer, pose, x2, y1, z1, x2, y1, z2, color);
        line(buffer, pose, x2, y1, z2, x1, y1, z2, color);
        line(buffer, pose, x1, y1, z2, x1, y1, z1, color);
        // Decke
        line(buffer, pose, x1, y2, z1, x2, y2, z1, color);
        line(buffer, pose, x2, y2, z1, x2, y2, z2, color);
        line(buffer, pose, x2, y2, z2, x1, y2, z2, color);
        line(buffer, pose, x1, y2, z2, x1, y2, z1, color);
        // Pfosten
        line(buffer, pose, x1, y1, z1, x1, y2, z1, color);
        line(buffer, pose, x2, y1, z1, x2, y2, z1, color);
        line(buffer, pose, x2, y1, z2, x2, y2, z2, color);
        line(buffer, pose, x1, y1, z2, x1, y2, z2, color);
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length == 0.0f) {
            return;
        }
        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;

        int a = ColorUtil.alpha(color);
        int r = ColorUtil.red(color);
        int g = ColorUtil.green(color);
        int b = ColorUtil.blue(color);

        buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }
}
