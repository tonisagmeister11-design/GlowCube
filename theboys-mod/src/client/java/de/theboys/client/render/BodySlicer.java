package de.theboys.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import de.theboys.client.mixin.AgeableMobRendererAccessor;
import de.theboys.client.mixin.LivingEntityRendererInvoker;
import de.theboys.client.mixin.ModelPartAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Cuts the real model of an entity in two along a vertical plane. Every cube of the model is clipped
 * against the plane; where a cube is cut, its cross-section is closed with torn flesh and a bone sticks
 * out. This works for every mob that is drawn with a {@link LivingEntityRenderer} (all vanilla mobs,
 * players and most modded mobs), whatever its shape.
 */
public final class BodySlicer {
	/** Vertex layout: x y z u v nx ny nz. Quads, four vertices each. */
	public static final int STRIDE = 8;

	/** One half of a body, positions relative to its own centre. */
	public static final class Mesh {
		public final Identifier texture;
		public final float[] skin;
		public final float[] gore;
		public final Vec3 centre;
		public final Vec3 wound;
		public final Vec3 woundNormal;
		public final float[] corners;
		public final int cuts;

		Mesh(Identifier texture, float[] skin, float[] gore, Vec3 centre, Vec3 wound, Vec3 woundNormal, float[] corners, int cuts) {
			this.texture = texture;
			this.skin = skin;
			this.gore = gore;
			this.centre = centre;
			this.wound = wound;
			this.woundNormal = woundNormal;
			this.corners = corners;
			this.cuts = cuts;
		}

		public int quads() {
			return skin.length / (STRIDE * 4);
		}
	}

	private BodySlicer() {
	}

	/**
	 * Slices the entity as it is drawn right now. The plane goes vertically through the entity's
	 * position with the given horizontal normal. Returns {negative side, positive side}, or null when the
	 * entity has no model that can be cut (then the caller falls back to plain halves).
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Mesh[] slice(Entity entity, Vec3 normal) {
		Minecraft mc = Minecraft.getInstance();
		EntityRenderer<?, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
		if (!(renderer instanceof LivingEntityRenderer living)) return null;
		LivingEntityRenderState state;
		try {
			state = (LivingEntityRenderState) ((EntityRenderer) renderer).createRenderState(entity, 1.0f);
		} catch (RuntimeException ex) {
			return null;
		}
		state.deathTime = 0;
		EntityModel model = living.getModel();
		if (renderer instanceof AgeableMobRenderer<?, ?, ?> ageable) {
			AgeableMobRendererAccessor acc = (AgeableMobRendererAccessor) (Object) ageable;
			EntityModel picked = state.isBaby ? acc.theboys$babyModel() : acc.theboys$adultModel();
			if (picked != null) model = picked;
		}
		Identifier texture = living.getTextureLocation(state);
		if (model == null || texture == null) return null;

		// the same transform LivingEntityRenderer.submit applies before drawing the model
		PoseStack ps = new PoseStack();
		float scale = state.scale;
		ps.scale(scale, scale, scale);
		LivingEntityRendererInvoker inv = (LivingEntityRendererInvoker) living;
		inv.theboys$setupRotations(state, ps, state.bodyRot, scale);
		ps.scale(-1.0f, -1.0f, 1.0f);
		inv.theboys$scale(state, ps);
		ps.translate(0.0f, -1.501f, 0.0f);
		model.setupAnim(state);

		float ax = (float) normal.x, az = (float) normal.z;
		Builder[] halves = {new Builder(-1, ax, az), new Builder(1, ax, az)};
		Collector col = new Collector();
		walk(model.root(), ps, (pose, cube) -> {
			col.verts.clear();
			cube.compile(pose, col, 0xF000F0, 0, -1);
			List<float[]> cut = new ArrayList<>();
			for (int i = 0; i + 3 < col.verts.size(); i += 4) {
				List<float[]> quad = col.verts.subList(i, i + 4);
				for (Builder h : halves) h.addPolygon(clip(quad, h.side, ax, az, h.side > 0 ? cut : null));
			}
			if (cut.size() >= 3) {
				for (Builder h : halves) h.addCap(cut);
			}
		});
		if (halves[0].skin.isEmpty() || halves[1].skin.isEmpty()) return null;
		return new Mesh[] {halves[0].build(texture), halves[1].build(texture)};
	}

	/** Like ModelPart.render: hidden parts and their children are skipped. */
	private static void walk(ModelPart part, PoseStack ps, java.util.function.BiConsumer<PoseStack.Pose, ModelPart.Cube> visitor) {
		if (!part.visible) return;
		ModelPartAccessor acc = (ModelPartAccessor) (Object) part;
		ps.pushPose();
		part.translateAndRotate(ps);
		if (!part.skipDraw) {
			for (ModelPart.Cube cube : acc.theboys$cubes()) visitor.accept(ps.last(), cube);
		}
		for (ModelPart child : acc.theboys$children().values()) walk(child, ps, visitor);
		ps.popPose();
	}

	// ------------------------------------------------------------------ clipping

	private static float dist(float[] v, float ax, float az) {
		return v[0] * ax + v[2] * az;
	}

	/** Sutherland-Hodgman: keeps the part of the polygon on the given side of the plane. */
	private static List<float[]> clip(List<float[]> poly, int side, float ax, float az, List<float[]> cutPoints) {
		List<float[]> out = new ArrayList<>(6);
		int n = poly.size();
		for (int i = 0; i < n; i++) {
			float[] a = poly.get(i);
			float[] b = poly.get((i + 1) % n);
			float da = side * dist(a, ax, az);
			float db = side * dist(b, ax, az);
			if (da >= 0) {
				out.add(a);
				if (da == 0 && cutPoints != null) cutPoints.add(a);
			}
			if ((da > 0 && db < 0) || (da < 0 && db > 0)) {
				float t = da / (da - db);
				float[] p = new float[STRIDE];
				for (int k = 0; k < STRIDE; k++) p[k] = a[k] + (b[k] - a[k]) * t;
				out.add(p);
				if (cutPoints != null) cutPoints.add(p);
			}
		}
		return out;
	}

	private static final class Builder {
		final int side;
		final float ax;
		final float az;
		/** horizontal direction inside the cutting plane */
		final float fx;
		final float fz;
		final List<float[]> skin = new ArrayList<>();
		final List<float[]> gore = new ArrayList<>();
		final List<float[]> bones = new ArrayList<>();
		float bestArea;
		float[] bestCentre;
		int cuts;

		Builder(int side, float ax, float az) {
			this.side = side;
			this.ax = ax;
			this.az = az;
			this.fx = -az;
			this.fz = ax;
		}

		void addPolygon(List<float[]> poly) {
			fan(skin, poly);
		}

		/** Closes a cut cube: the convex hull of its cut points, filled with flesh, plus a bone sticking out. */
		void addCap(List<float[]> pts) {
			List<float[]> hull = hull(pts, fx, fz);
			if (hull.size() < 3) return;
			float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
			float cx = 0, cy = 0, cz = 0;
			for (float[] p : hull) {
				float u = p[0] * fx + p[2] * fz;
				minU = Math.min(minU, u);
				maxU = Math.max(maxU, u);
				minV = Math.min(minV, p[1]);
				maxV = Math.max(maxV, p[1]);
				cx += p[0];
				cy += p[1];
				cz += p[2];
			}
			cx /= hull.size();
			cy /= hull.size();
			cz /= hull.size();
			float area = 0;
			for (int i = 0; i < hull.size(); i++) {
				float[] a = hull.get(i), b = hull.get((i + 1) % hull.size());
				area += (a[0] * fx + a[2] * fz) * b[1] - (b[0] * fx + b[2] * fz) * a[1];
			}
			area = Math.abs(area) / 2;
			// the cut face points towards the other half
			float nx = -side * ax, nz = -side * az;
			// layered models (player jacket over body) cut twice in the same place: lift later caps a hair
			float lift = 0.0006f * (cuts % 6);
			cuts++;
			float du = Math.max(1e-4f, maxU - minU), dv = Math.max(1e-4f, maxV - minV);
			List<float[]> cap = new ArrayList<>();
			for (float[] p : hull) {
				float u = p[0] * fx + p[2] * fz;
				float[] v = new float[STRIDE];
				v[0] = p[0] + nx * lift;
				v[1] = p[1];
				v[2] = p[2] + nz * lift;
				// flesh column of gore.png (x 16..20)
				v[3] = (16.5f + (u - minU) / du * 4.0f) / 32.0f;
				v[4] = (p[1] - minV) / dv;
				v[5] = nx;
				v[6] = 0;
				v[7] = nz;
				cap.add(v);
			}
			fanBothSides(gore, cap);
			if (area > bestArea) {
				bestArea = area;
				bestCentre = new float[] {cx, cy, cz};
			}
			if (area > 0.0035f) {
				for (float[] b : bones) {
					float ddx = b[0] - cx, ddy = b[1] - cy, ddz = b[2] - cz;
					if (ddx * ddx + ddy * ddy + ddz * ddz < 0.012f) return;
				}
				bones.add(new float[] {cx, cy, cz});
				float size = (float) Math.sqrt(area);
				float thick = Math.max(0.035f, Math.min(0.14f, size * 0.32f));
				float len = Math.max(0.07f, Math.min(0.3f, size * 0.55f));
				bone(cx, cy, cz, nx, nz, thick, len);
			}
		}

		/** A white bone box from just inside the cut out to len in front of it. */
		private void bone(float cx, float cy, float cz, float nx, float nz, float thick, float len) {
			float h = thick / 2;
			float x0 = -0.02f, x1 = len;
			// local frame: n (out of the wound), f (sideways in the plane), up
			float[][] c = new float[8][];
			int k = 0;
			for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) for (int d = 0; d < 2; d++) {
				float along = a == 0 ? x0 : x1;
				float sideways = b == 0 ? -h : h;
				float up = d == 0 ? -h : h;
				c[k++] = new float[] {cx + nx * along + fx * sideways, cy + up, cz + nz * along + fz * sideways};
			}
			// bone column of gore.png (x 21..26)
			float u0 = 21.5f / 32, u1 = 22.8f / 32;
			int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
			for (int[] f : faces) {
				List<float[]> q = new ArrayList<>();
				float[][] uv = {{u0, 0}, {u0, 0.4f}, {u1, 0.4f}, {u1, 0}};
				for (int i = 0; i < 4; i++) {
					float[] p = c[f[i]];
					q.add(new float[] {p[0], p[1], p[2], uv[i][0], uv[i][1], nx, 1, nz});
				}
				fanBothSides(gore, q);
			}
		}

		Mesh build(Identifier texture) {
			double sx = 0, sy = 0, sz = 0;
			for (float[] v : skin) {
				sx += v[0];
				sy += v[1];
				sz += v[2];
			}
			int n = Math.max(1, skin.size());
			float cx = (float) (sx / n), cy = (float) (sy / n), cz = (float) (sz / n);
			float[] minMax = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
			float[] s = pack(skin, cx, cy, cz, minMax);
			float[] g = pack(gore, cx, cy, cz, null);
			float[] corners = new float[24];
			int k = 0;
			for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) for (int d = 0; d < 2; d++) {
				corners[k++] = a == 0 ? minMax[0] : minMax[3];
				corners[k++] = b == 0 ? minMax[1] : minMax[4];
				corners[k++] = d == 0 ? minMax[2] : minMax[5];
			}
			float[] w = bestCentre != null ? bestCentre : new float[] {cx, cy, cz};
			return new Mesh(texture, s, g, new Vec3(cx, cy, cz), new Vec3(w[0] - cx, w[1] - cy, w[2] - cz),
					new Vec3(-side * ax, 0, -side * az), corners, cuts);
		}
	}

	private static float[] pack(List<float[]> verts, float cx, float cy, float cz, float[] minMax) {
		float[] out = new float[verts.size() * STRIDE];
		int i = 0;
		for (float[] v : verts) {
			out[i] = v[0] - cx;
			out[i + 1] = v[1] - cy;
			out[i + 2] = v[2] - cz;
			System.arraycopy(v, 3, out, i + 3, STRIDE - 3);
			if (minMax != null) {
				for (int k = 0; k < 3; k++) {
					minMax[k] = Math.min(minMax[k], out[i + k]);
					minMax[k + 3] = Math.max(minMax[k + 3], out[i + k]);
				}
			}
			i += STRIDE;
		}
		return out;
	}

	/** Polygon -> quads (a triangle is a quad with its last vertex doubled). */
	private static void fan(List<float[]> out, List<float[]> poly) {
		int n = poly.size();
		if (n < 3) return;
		for (int i = 1; i + 1 < n; i += 2) {
			out.add(poly.get(0));
			out.add(poly.get(i));
			out.add(poly.get(i + 1));
			out.add(poly.get(Math.min(i + 2, n - 1)));
		}
	}

	private static void fanBothSides(List<float[]> out, List<float[]> poly) {
		fan(out, poly);
		List<float[]> back = new ArrayList<>(poly.size());
		for (int i = poly.size() - 1; i >= 0; i--) {
			float[] v = poly.get(i).clone();
			v[5] = -v[5];
			v[6] = -v[6];
			v[7] = -v[7];
			back.add(v);
		}
		fan(out, back);
	}

	/** Convex hull of points lying in the cutting plane (monotone chain in plane coordinates). */
	private static List<float[]> hull(List<float[]> pts, float fx, float fz) {
		// plane coordinates: (x*fx + z*fz, y)
		List<float[]> p = new ArrayList<>(pts);
		float[] n0 = {fx, fz};
		p.sort((a, b) -> {
			float ua = a[0] * n0[0] + a[2] * n0[1], ub = b[0] * n0[0] + b[2] * n0[1];
			if (Math.abs(ua - ub) > 1e-6f) return Float.compare(ua, ub);
			return Float.compare(a[1], b[1]);
		});
		List<float[]> h = new ArrayList<>();
		for (int pass = 0; pass < 2; pass++) {
			int start = h.size();
			for (int i = 0; i < p.size(); i++) {
				float[] q = pass == 0 ? p.get(i) : p.get(p.size() - 1 - i);
				while (h.size() >= start + 2 && cross(h.get(h.size() - 2), h.get(h.size() - 1), q, n0) <= 1e-9f) {
					h.remove(h.size() - 1);
				}
				h.add(q);
			}
			h.remove(h.size() - 1);
		}
		return h;
	}

	private static float cross(float[] o, float[] a, float[] b, float[] ax) {
		float ou = o[0] * ax[0] + o[2] * ax[1], au = a[0] * ax[0] + a[2] * ax[1], bu = b[0] * ax[0] + b[2] * ax[1];
		return (au - ou) * (b[1] - o[1]) - (a[1] - o[1]) * (bu - ou);
	}

	/** Records the vertices a cube writes. */
	private static final class Collector implements VertexConsumer {
		final List<float[]> verts = new ArrayList<>();
		private float[] cur = new float[STRIDE];

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			cur = new float[STRIDE];
			cur[0] = x;
			cur[1] = y;
			cur[2] = z;
			verts.add(cur);
			return this;
		}

		@Override
		public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
			addVertex(x, y, z);
			setUv(u, v);
			setNormal(nx, ny, nz);
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			return this;
		}

		@Override
		public VertexConsumer setColor(int argb) {
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			cur[3] = u;
			cur[4] = v;
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv3(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			cur[5] = x;
			cur[6] = y;
			cur[7] = z;
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}
	}
}
