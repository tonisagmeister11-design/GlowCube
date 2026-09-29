package de.gtacity.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.gtacity.GtaCity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Draws a gun model in the Bedrock / GeckoLib geometry format (.geo.json, as made with Blockbench) as an item, without
 * GeckoLib: the bones and cubes are baked once into a flat list of textured quads, following GeckoLib's conventions
 * (mirrored x axis, rotations Z-Y-X around the pivots, per-face UVs), and placed like GeckoLib's item renderer does.
 * The models come from "Greenboy's Legendary Guns" (MIT License), see CREDITS-greenboys-legendary-guns.txt.
 */
public class GeoGunRenderer implements NoDataSpecialModelRenderer {
    private final Identifier texture;
    /** Quads: 4 vertices x (x, y, z, u, v) + normal (x, y, z). */
    private final float[][] quads;

    private GeoGunRenderer(Identifier texture, float[][] quads) {
        this.texture = texture;
        this.quads = quads;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean hasFoil,
                       int outlineColor) {
        poseStack.pushPose();
        // GeckoLib's item renderer: model origin in the middle of the item block, just above its floor.
        poseStack.translate(0.5F, 0.51F, 0.5F);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture, true), (pose, buffer) -> {
            Matrix4f matrix = pose.pose();
            Vector4f pos = new Vector4f();
            Vector3f normal = new Vector3f();
            for (float[] q : quads) {
                normal.set(q[20], q[21], q[22]);
                pose.transformNormal(normal, normal);
                for (int i = 0; i < 4; i++) {
                    int o = i * 5;
                    pos.set(q[o], q[o + 1], q[o + 2], 1.0F);
                    matrix.transform(pos);
                    buffer.addVertex(pos.x(), pos.y(), pos.z(), 0xFFFFFFFF, q[o + 3], q[o + 4], overlay, light,
                            normal.x(), normal.y(), normal.z());
                }
            }
        });
        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (float[] q : quads) {
            for (int i = 0; i < 4; i++) {
                output.accept(new Vector3f(q[i * 5] + 0.5F, q[i * 5 + 1] + 0.51F, q[i * 5 + 2] + 0.5F));
            }
        }
    }

    // ------------------------------------------------------------------ loading

    public record Unbaked(Identifier geometry, Identifier texture) implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("geometry").forGetter(Unbaked::geometry),
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture)).apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            try {
                return new GeoGunRenderer(texture, load(geometry));
            } catch (Exception e) {
                GtaCity.LOG.error("Waffenmodell {} konnte nicht geladen werden", geometry, e);
                return null;
            }
        }
    }

    private static float[][] load(Identifier id) throws Exception {
        var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id);
        JsonObject root;
        try (Reader reader = resource.openAsReader()) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        float texW = description.get("texture_width").getAsFloat();
        float texH = description.get("texture_height").getAsFloat();
        List<JsonObject> bones = new ArrayList<>();
        for (JsonElement e : geometry.getAsJsonArray("bones")) {
            bones.add(e.getAsJsonObject());
        }
        List<float[]> quads = new ArrayList<>();
        for (JsonObject bone : bones) {
            if (bone.has("parent")) {
                continue;
            }
            bake(bone, bones, new PoseStack(), texW, texH, quads);
        }
        return quads.toArray(new float[0][]);
    }

    private static float[] vec(JsonObject o, String key, float fallback) {
        if (!o.has(key)) {
            return new float[]{fallback, fallback, fallback};
        }
        JsonArray a = o.getAsJsonArray(key);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    /** Moves the pose into a bone / cube: to the (mirrored) pivot, rotate Z, Y, X, and back. */
    private static void rotateAround(PoseStack pose, float[] pivot, float[] rotation) {
        float px = -pivot[0] / 16.0F, py = pivot[1] / 16.0F, pz = pivot[2] / 16.0F;
        pose.translate(px, py, pz);
        if (rotation[2] != 0) {
            pose.rotate(Axis.ZP.rotationDegrees(rotation[2]));
        }
        if (rotation[1] != 0) {
            pose.rotate(Axis.YP.rotationDegrees(-rotation[1]));
        }
        if (rotation[0] != 0) {
            pose.rotate(Axis.XP.rotationDegrees(-rotation[0]));
        }
        pose.translate(-px, -py, -pz);
    }

    private static void bake(JsonObject bone, List<JsonObject> bones, PoseStack pose, float texW, float texH,
                             List<float[]> out) {
        pose.pushPose();
        rotateAround(pose, vec(bone, "pivot", 0), vec(bone, "rotation", 0));
        if (bone.has("cubes")) {
            for (JsonElement e : bone.getAsJsonArray("cubes")) {
                JsonObject cube = e.getAsJsonObject();
                pose.pushPose();
                if (cube.has("rotation")) {
                    rotateAround(pose, vec(cube, "pivot", 0), vec(cube, "rotation", 0));
                }
                cube(cube, pose.last().pose(), texW, texH, out);
                pose.popPose();
            }
        }
        String name = bone.get("name").getAsString();
        for (JsonObject child : bones) {
            if (child.has("parent") && child.get("parent").getAsString().equals(name)) {
                bake(child, bones, pose, texW, texH, out);
            }
        }
        pose.popPose();
    }

    private static void cube(JsonObject cube, Matrix4f m, float texW, float texH, List<float[]> out) {
        float[] origin = vec(cube, "origin", 0), size = vec(cube, "size", 0);
        float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() / 16.0F : 0.0F;
        float x0 = -(origin[0] + size[0]) / 16.0F - inflate, y0 = origin[1] / 16.0F - inflate,
                z0 = origin[2] / 16.0F - inflate;
        float x1 = x0 + size[0] / 16.0F + 2 * inflate, y1 = y0 + size[1] / 16.0F + 2 * inflate,
                z1 = z0 + size[2] / 16.0F + 2 * inflate;
        float[] p1 = {x0, y0, z0}, p2 = {x0, y0, z1}, p3 = {x0, y1, z0}, p4 = {x0, y1, z1};
        float[] p5 = {x1, y0, z0}, p6 = {x1, y0, z1}, p7 = {x1, y1, z0}, p8 = {x1, y1, z1};
        if (!cube.has("uv") || !cube.get("uv").isJsonObject()) {
            return; // box UV is not used by these models
        }
        JsonObject uv = cube.getAsJsonObject("uv");
        face(uv, "west", new float[][]{p4, p3, p1, p2}, -1, 0, 0, m, texW, texH, out);
        face(uv, "east", new float[][]{p7, p8, p6, p5}, 1, 0, 0, m, texW, texH, out);
        face(uv, "north", new float[][]{p3, p7, p5, p1}, 0, 0, -1, m, texW, texH, out);
        face(uv, "south", new float[][]{p8, p4, p2, p6}, 0, 0, 1, m, texW, texH, out);
        face(uv, "up", new float[][]{p4, p8, p7, p3}, 0, 1, 0, m, texW, texH, out);
        face(uv, "down", new float[][]{p1, p5, p6, p2}, 0, -1, 0, m, texW, texH, out);
    }

    private static void face(JsonObject uvs, String name, float[][] v, float nx, float ny, float nz, Matrix4f m,
                             float texW, float texH, List<float[]> out) {
        if (!uvs.has(name)) {
            return;
        }
        JsonObject f = uvs.getAsJsonObject(name);
        JsonArray uv = f.getAsJsonArray("uv");
        JsonArray size = f.has("uv_size") ? f.getAsJsonArray("uv_size") : null;
        float u0 = uv.get(0).getAsFloat() / texW, v0 = uv.get(1).getAsFloat() / texH;
        float u1 = u0 + (size == null ? 0 : size.get(0).getAsFloat()) / texW;
        float v1 = v0 + (size == null ? 0 : size.get(1).getAsFloat()) / texH;
        float[][] uvOrder = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        float[] q = new float[23];
        Vector4f p = new Vector4f();
        for (int i = 0; i < 4; i++) {
            p.set(v[i][0], v[i][1], v[i][2], 1.0F);
            m.transform(p);
            q[i * 5] = p.x();
            q[i * 5 + 1] = p.y();
            q[i * 5 + 2] = p.z();
            q[i * 5 + 3] = uvOrder[i][0];
            q[i * 5 + 4] = uvOrder[i][1];
        }
        Vector3f n = new Vector3f(nx, ny, nz);
        m.transformDirection(n).normalize();
        q[20] = n.x();
        q[21] = n.y();
        q[22] = n.z();
        out.add(q);
    }
}
