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
import de.gtacity.registry.ModComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Draws a gun model in the Bedrock / GeckoLib geometry format (.geo.json, as made with Blockbench) as an item, without
 * GeckoLib, and plays its "shoot" and "reload" animations: slide and bolt, muzzle flash, ejected shell, the magazine
 * dropping out and the new one going in. Bones, cubes and keyframes follow GeckoLib's conventions (mirrored x axis,
 * rotations Z-Y-X around the pivots, per-face UVs, linear keyframes). When the gun last fired / started reloading is
 * on the item stack ({@link ModComponents#GUN_ANIM}), so the animations play for every gun anybody holds.
 * The models and animations come from "Greenboy's Legendary Guns" (MIT License), see
 * CREDITS-greenboys-legendary-guns.txt.
 */
public class GeoGunRenderer implements SpecialModelRenderer<GeoGunRenderer.Frame> {
    /** Which animation to show and how far into it (seconds). */
    public record Frame(boolean reload, float time) {
    }

    private final Identifier texture;
    private final List<Bone> bones;
    private final List<Bone> roots;
    /** Quads of the whole gun at rest: 4 vertices x (x, y, z, u, v) + normal (x, y, z). */
    private final float[][] restQuads;
    private final @Nullable Animation shoot;
    private final @Nullable Animation reload;

    private GeoGunRenderer(Identifier texture, List<Bone> bones, @Nullable Animation shoot,
                           @Nullable Animation reload) {
        this.texture = texture;
        this.bones = bones;
        this.roots = bones.stream().filter(b -> b.parent == null).toList();
        this.shoot = shoot;
        this.reload = reload;
        List<float[]> rest = new ArrayList<>();
        for (Bone root : roots) {
            collect(root, new Matrix4f(), null, 0, rest);
        }
        this.restQuads = rest.toArray(new float[0][]);
    }

    // ------------------------------------------------------------------ animation state

    @Override
    public @Nullable Frame extractArgument(ItemStack stack) {
        ModComponents.GunAnim anim = stack.get(ModComponents.GUN_ANIM);
        Minecraft mc = Minecraft.getInstance();
        if (anim == null || mc.level == null) {
            return null;
        }
        float now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float sinceReload = now - anim.reloadStart();
        if (reload != null && anim.reloadTicks() > 0 && sinceReload >= 0 && sinceReload < anim.reloadTicks()) {
            // The reload animation is stretched to the gun's reload time.
            return new Frame(true, sinceReload / anim.reloadTicks() * reload.length);
        }
        float sinceShot = (now - anim.shot()) / 20.0F;
        if (shoot != null && sinceShot >= 0 && sinceShot < shoot.length) {
            return new Frame(false, sinceShot);
        }
        return null;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void submit(@Nullable Frame frame, PoseStack poseStack, SubmitNodeCollector collector, int light,
                       int overlay, boolean hasFoil, int outlineColor) {
        Animation animation = frame == null ? null : frame.reload() ? reload : shoot;
        List<float[]> quads = null;
        if (animation != null) {
            quads = new ArrayList<>();
            for (Bone root : roots) {
                collect(root, new Matrix4f(), animation, frame.time(), quads);
            }
        }
        float[][] draw = quads == null ? restQuads : quads.toArray(new float[0][]);
        poseStack.pushPose();
        // GeckoLib's item renderer: model origin in the middle of the item block, just above its floor.
        poseStack.translate(0.5F, 0.51F, 0.5F);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture, true), (pose, buffer) -> {
            Matrix4f matrix = pose.pose();
            Vector4f pos = new Vector4f();
            Vector3f normal = new Vector3f();
            for (float[] q : draw) {
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

    /**
     * Bone transform, GeckoLib style: animated offset, then around the pivot rotate Z, Y, X (rest + animation) and
     * scale. The quads of the bone and its children are added in model space.
     */
    private void collect(Bone bone, Matrix4f parent, @Nullable Animation animation, float time, List<float[]> out) {
        BoneAnimation anim = animation == null ? null : animation.bones.get(bone.name);
        float[] rot = bone.rotation.clone();
        float[] offset = {0, 0, 0};
        float[] scale = bone.scale;
        if (anim != null) {
            // The whole gun kicking / turning is toned down: the original moves its arms and camera along.
            float damp = bone.carriesGun ? (animation == reload ? 0.6F : 0.35F) : 1.0F;
            if (anim.rotation != null) {
                float[] r = anim.rotation.sample(time);
                for (int i = 0; i < 3; i++) {
                    rot[i] += r[i] * damp;
                }
            }
            if (anim.position != null) {
                float[] p = anim.position.sample(time);
                for (int i = 0; i < 3; i++) {
                    offset[i] = p[i] * damp;
                }
            }
            if (anim.scale != null) {
                scale = anim.scale.sample(time);
            }
        } else if (bone.hidden) {
            return; // muzzle flash, shell: only while an animation shows them
        }
        if (scale != null && scale[0] == 0 && scale[1] == 0 && scale[2] == 0) {
            return;
        }
        Matrix4f m = new Matrix4f(parent);
        m.translate(-offset[0] / 16.0F, offset[1] / 16.0F, offset[2] / 16.0F);
        m.translate(bone.pivot[0], bone.pivot[1], bone.pivot[2]);
        if (rot[2] != 0) {
            m.rotate(Axis.ZP.rotationDegrees(rot[2]));
        }
        if (rot[1] != 0) {
            m.rotate(Axis.YP.rotationDegrees(-rot[1]));
        }
        if (rot[0] != 0) {
            m.rotate(Axis.XP.rotationDegrees(-rot[0]));
        }
        if (scale != null) {
            m.scale(scale[0], scale[1], scale[2]);
        }
        m.translate(-bone.pivot[0], -bone.pivot[1], -bone.pivot[2]);
        Vector4f p = new Vector4f();
        Vector3f n = new Vector3f();
        for (float[] local : bone.quads) {
            float[] q = new float[23];
            for (int i = 0; i < 4; i++) {
                p.set(local[i * 5], local[i * 5 + 1], local[i * 5 + 2], 1.0F);
                m.transform(p);
                q[i * 5] = p.x();
                q[i * 5 + 1] = p.y();
                q[i * 5 + 2] = p.z();
                q[i * 5 + 3] = local[i * 5 + 3];
                q[i * 5 + 4] = local[i * 5 + 4];
            }
            n.set(local[20], local[21], local[22]);
            m.transformDirection(n);
            if (n.lengthSquared() > 1.0E-8F) {
                n.normalize();
            }
            q[20] = n.x();
            q[21] = n.y();
            q[22] = n.z();
            out.add(q);
        }
        for (Bone child : bone.children) {
            collect(child, m, animation, time, out);
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (float[] q : restQuads) {
            for (int i = 0; i < 4; i++) {
                output.accept(new Vector3f(q[i * 5] + 0.5F, q[i * 5 + 1] + 0.51F, q[i * 5 + 2] + 0.5F));
            }
        }
    }

    // ------------------------------------------------------------------ model data

    private static final class Bone {
        final String name;
        final float[] pivot;
        final float[] rotation;
        final float @Nullable [] scale;
        final boolean hidden;
        /** Quads in model space with the cube's own rotation, but without the bone transforms. */
        final List<float[]> quads = new ArrayList<>();
        final List<Bone> children = new ArrayList<>();
        @Nullable Bone parent;
        int cubes;
        /** The bone moves (almost) the whole gun - its animation is toned down. */
        boolean carriesGun;

        Bone(JsonObject json) {
            name = json.get("name").getAsString();
            float[] p = vec(json, "pivot", 0);
            pivot = new float[]{-p[0] / 16.0F, p[1] / 16.0F, p[2] / 16.0F};
            rotation = vec(json, "rotation", 0);
            scale = json.has("gtacity_scale") ? vec(json, "gtacity_scale", 1) : null;
            hidden = json.has("gtacity_hidden") && json.get("gtacity_hidden").getAsBoolean();
        }

        int subtreeCubes() {
            int n = cubes;
            for (Bone child : children) {
                n += child.subtreeCubes();
            }
            return n;
        }
    }

    /** Linear keyframes: times (seconds) and xyz values. */
    private record Channel(float[] times, float[][] values) {
        float[] sample(float t) {
            if (t <= times[0]) {
                return values[0];
            }
            int last = times.length - 1;
            if (t >= times[last]) {
                return values[last];
            }
            int i = 1;
            while (times[i] < t) {
                i++;
            }
            float f = (t - times[i - 1]) / Math.max(1.0E-6F, times[i] - times[i - 1]);
            float[] a = values[i - 1], b = values[i];
            return new float[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
        }

        static @Nullable Channel of(JsonObject bone, String key) {
            if (!bone.has(key)) {
                return null;
            }
            JsonArray frames = bone.getAsJsonArray(key);
            float[] times = new float[frames.size()];
            float[][] values = new float[frames.size()][];
            for (int i = 0; i < frames.size(); i++) {
                JsonArray f = frames.get(i).getAsJsonArray();
                times[i] = f.get(0).getAsFloat();
                values[i] = new float[]{f.get(1).getAsFloat(), f.get(2).getAsFloat(), f.get(3).getAsFloat()};
            }
            return times.length == 0 ? null : new Channel(times, values);
        }
    }

    private record BoneAnimation(@Nullable Channel rotation, @Nullable Channel position, @Nullable Channel scale) {
    }

    private record Animation(float length, Map<String, BoneAnimation> bones) {
        static @Nullable Animation of(JsonObject animations, String kind) {
            if (animations == null || !animations.has(kind)) {
                return null;
            }
            JsonObject json = animations.getAsJsonObject(kind);
            Map<String, BoneAnimation> bones = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("bones").entrySet()) {
                JsonObject b = e.getValue().getAsJsonObject();
                bones.put(e.getKey(), new BoneAnimation(Channel.of(b, "rotation"), Channel.of(b, "position"),
                        Channel.of(b, "scale")));
            }
            return new Animation(json.get("length").getAsFloat(), bones);
        }
    }

    // ------------------------------------------------------------------ loading

    public record Unbaked(Identifier geometry, Identifier texture) implements SpecialModelRenderer.Unbaked<Frame> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("geometry").forGetter(Unbaked::geometry),
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture)).apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public @Nullable SpecialModelRenderer<Frame> bake(SpecialModelRenderer.BakingContext context) {
            try {
                return load(geometry, texture);
            } catch (Exception e) {
                GtaCity.LOG.error("Waffenmodell {} konnte nicht geladen werden", geometry, e);
                return null;
            }
        }
    }

    private static GeoGunRenderer load(Identifier id, Identifier texture) throws Exception {
        var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id);
        JsonObject root;
        try (Reader reader = resource.openAsReader()) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        float texW = description.get("texture_width").getAsFloat();
        float texH = description.get("texture_height").getAsFloat();
        List<Bone> bones = new ArrayList<>();
        Map<String, Bone> byName = new HashMap<>();
        Map<Bone, String> parents = new HashMap<>();
        for (JsonElement e : geometry.getAsJsonArray("bones")) {
            JsonObject json = e.getAsJsonObject();
            Bone bone = new Bone(json);
            bones.add(bone);
            byName.put(bone.name, bone);
            if (json.has("parent")) {
                parents.put(bone, json.get("parent").getAsString());
            }
            if (json.has("cubes")) {
                for (JsonElement c : json.getAsJsonArray("cubes")) {
                    JsonObject cube = c.getAsJsonObject();
                    PoseStack pose = new PoseStack();
                    if (cube.has("rotation")) {
                        rotateAround(pose, vec(cube, "pivot", 0), vec(cube, "rotation", 0));
                    }
                    cube(cube, pose.last().pose(), texW, texH, bone.quads);
                    bone.cubes++;
                }
            }
        }
        for (Map.Entry<Bone, String> e : parents.entrySet()) {
            Bone parent = byName.get(e.getValue());
            if (parent != null) {
                e.getKey().parent = parent;
                parent.children.add(e.getKey());
            }
        }
        // Bones that move the whole gun (their subtree holds most of the cubes).
        int total = bones.stream().mapToInt(b -> b.cubes).sum();
        for (Bone bone : bones) {
            bone.carriesGun = bone.subtreeCubes() * 2 > total;
        }
        JsonObject animations = geometry.has("gtacity_animations") ? geometry.getAsJsonObject("gtacity_animations")
                : null;
        return new GeoGunRenderer(texture, bones, Animation.of(animations, "shoot"),
                Animation.of(animations, "reload"));
    }

    private static float[] vec(JsonObject o, String key, float fallback) {
        if (!o.has(key)) {
            return new float[]{fallback, fallback, fallback};
        }
        JsonArray a = o.getAsJsonArray(key);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    /** Cube rotation: to the (mirrored) pivot, rotate Z, Y, X, and back. */
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
