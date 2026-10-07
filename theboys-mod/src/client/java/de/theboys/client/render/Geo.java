package de.theboys.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Geometry helpers. All positions are relative to the camera. */
public final class Geo {
	private static final Vec3 UP = new Vec3(0, 1, 0);

	private Geo() {
	}

	/** Two perpendicular unit vectors orthogonal to dir. */
	public static Vec3[] basis(Vec3 dir) {
		Vec3 d = dir.normalize();
		Vec3 ref = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : UP;
		Vec3 u = d.cross(ref).normalize();
		Vec3 v = d.cross(u).normalize();
		return new Vec3[] {u, v};
	}

	/** Axis aligned box, single-sided faces exactly like Fabric's test utility. */
	public static void box(VertexConsumer b, PoseStack.Pose pose, net.minecraft.world.phys.AABB box, int color) {
		float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ, x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
		float[][] f = {
				{x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0},
				{x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1},
				{x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1},
				{x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0},
				{x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1},
				{x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0}};
		for (float[] q : f) {
			for (int i = 0; i < 12; i += 3) {
				b.addVertex(pose, q[i], q[i + 1], q[i + 2]).setColor(color);
			}
		}
	}

	public static void quad(VertexConsumer b, PoseStack.Pose pose, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, int argb) {
		v(b, pose, p1, argb);
		v(b, pose, p2, argb);
		v(b, pose, p3, argb);
		v(b, pose, p4, argb);
		// back side
		v(b, pose, p4, argb);
		v(b, pose, p3, argb);
		v(b, pose, p2, argb);
		v(b, pose, p1, argb);
	}

	private static void v(VertexConsumer b, PoseStack.Pose pose, Vec3 p, int argb) {
		b.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(argb);
	}

	/** A glowing square prism from a to b (laser beams, lightning segments). */
	public static void beam(VertexConsumer b, PoseStack.Pose pose, Vec3 from, Vec3 to, double radius, int argb) {
		Vec3 dir = to.subtract(from);
		if (dir.lengthSqr() < 1.0E-6) return;
		Vec3[] uv = basis(dir);
		Vec3 u = uv[0].scale(radius);
		Vec3 w = uv[1].scale(radius);
		Vec3 a1 = from.add(u).add(w), a2 = from.add(u).subtract(w), a3 = from.subtract(u).subtract(w), a4 = from.subtract(u).add(w);
		Vec3 b1 = to.add(u).add(w), b2 = to.add(u).subtract(w), b3 = to.subtract(u).subtract(w), b4 = to.subtract(u).add(w);
		quad(b, pose, a1, a2, b2, b1, argb);
		quad(b, pose, a2, a3, b3, b2, argb);
		quad(b, pose, a3, a4, b4, b3, argb);
		quad(b, pose, a4, a1, b1, b4, argb);
	}

	public static void sphere(VertexConsumer b, PoseStack.Pose pose, Vec3 c, double r, int argb, int seg) {
		int rings = seg / 2;
		for (int i = 0; i < rings; i++) {
			double t0 = Math.PI * i / rings, t1 = Math.PI * (i + 1) / rings;
			for (int j = 0; j < seg; j++) {
				double p0 = 2 * Math.PI * j / seg, p1 = 2 * Math.PI * (j + 1) / seg;
				quad(b, pose, sp(c, r, t0, p0), sp(c, r, t0, p1), sp(c, r, t1, p1), sp(c, r, t1, p0), argb);
			}
		}
	}

	private static Vec3 sp(Vec3 c, double r, double theta, double phi) {
		return new Vec3(c.x + r * Math.sin(theta) * Math.cos(phi), c.y + r * Math.cos(theta), c.z + r * Math.sin(theta) * Math.sin(phi));
	}

	/** Flat ring (annulus) around normal n. */
	public static void ring(VertexConsumer b, PoseStack.Pose pose, Vec3 c, Vec3 n, double r0, double r1, int argb, int seg) {
		Vec3[] uv = basis(n);
		for (int j = 0; j < seg; j++) {
			double a0 = 2 * Math.PI * j / seg, a1 = 2 * Math.PI * (j + 1) / seg;
			Vec3 d0 = uv[0].scale(Math.cos(a0)).add(uv[1].scale(Math.sin(a0)));
			Vec3 d1 = uv[0].scale(Math.cos(a1)).add(uv[1].scale(Math.sin(a1)));
			quad(b, pose, c.add(d0.scale(r0)), c.add(d1.scale(r0)), c.add(d1.scale(r1)), c.add(d0.scale(r1)), argb);
		}
	}

	/** A polyline of glowing segments. */
	public static void bolt(VertexConsumer b, PoseStack.Pose pose, List<Vec3> pts, double width, int argb) {
		for (int i = 0; i + 1 < pts.size(); i++) {
			beam(b, pose, pts.get(i), pts.get(i + 1), width, argb);
		}
	}

	/** A textured, lit tube through the given points (tentacles). */
	public static void tube(VertexConsumer b, PoseStack.Pose pose, List<Vec3> pts, double baseRadius, double tipRadius, int light, int sides) {
		int n = pts.size();
		if (n < 2) return;
		Vec3[][] rings = new Vec3[n][sides];
		Vec3[][] normals = new Vec3[n][sides];
		for (int i = 0; i < n; i++) {
			Vec3 dir = i + 1 < n ? pts.get(i + 1).subtract(pts.get(i)) : pts.get(i).subtract(pts.get(i - 1));
			Vec3[] uv = basis(dir);
			double t = i / (double) (n - 1);
			double r = baseRadius + (tipRadius - baseRadius) * t;
			// bulges along the tendril
			r *= 1.0 + 0.18 * Math.sin(t * 22);
			for (int s = 0; s < sides; s++) {
				double a = 2 * Math.PI * s / sides;
				Vec3 nrm = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
				normals[i][s] = nrm;
				rings[i][s] = pts.get(i).add(nrm.scale(r));
			}
		}
		for (int i = 0; i + 1 < n; i++) {
			// the texture tiles: alternate halves so v stays within 0..1 and stays continuous
			float v0 = (i % 2) * 0.5f, v1 = v0 + 0.5f;
			for (int s = 0; s < sides; s++) {
				int s2 = (s + 1) % sides;
				float u0 = s / (float) sides, u1 = (s + 1) / (float) sides;
				tv(b, pose, rings[i][s], normals[i][s], u0, v0, light);
				tv(b, pose, rings[i][s2], normals[i][s2], u1, v0, light);
				tv(b, pose, rings[i + 1][s2], normals[i + 1][s2], u1, v1, light);
				tv(b, pose, rings[i + 1][s], normals[i + 1][s], u0, v1, light);
			}
		}
		// spiked head
		Vec3 tip = pts.get(n - 1);
		Vec3 dir = tip.subtract(pts.get(n - 2)).normalize();
		Vec3 point = tip.add(dir.scale(Math.max(0.25, tipRadius * 6)));
		for (int s = 0; s < sides; s++) {
			int s2 = (s + 1) % sides;
			tv(b, pose, rings[n - 1][s], normals[n - 1][s], 0, 0, light);
			tv(b, pose, rings[n - 1][s2], normals[n - 1][s2], 0.2f, 0, light);
			tv(b, pose, point, dir, 0.1f, 0.3f, light);
			tv(b, pose, point, dir, 0.1f, 0.3f, light);
		}
	}

	private static void tv(VertexConsumer b, PoseStack.Pose pose, Vec3 p, Vec3 n, float u, float v, int light) {
		b.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
	}
}
