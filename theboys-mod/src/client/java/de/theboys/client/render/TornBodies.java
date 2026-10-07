package de.theboys.client.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The two halves of a body Butcher tore apart. Each half flies off, tumbles, lands and keeps
 * spraying blood out of the torn side, where the spine and ribs stick out.
 */
public final class TornBodies {
	private static final int LIFE = 240;
	private static final List<Half> HALVES = new ArrayList<>();

	public static final class Half {
		Vec3 pos;
		Vec3 prevPos;
		Vec3 vel;
		float yaw;
		float roll;
		float prevRoll;
		float spin;
		final float w;
		final float h;
		final float d;
		final int side;
		int age;
		boolean landed;
		/** the real model of the victim cut in half; null = plain box half */
		BodySlicer.Mesh mesh;
		float targetRoll;

		Half(Vec3 pos, Vec3 vel, float yaw, float w, float h, int side, float spin) {
			this.pos = pos;
			this.prevPos = pos;
			this.vel = vel;
			this.yaw = yaw;
			this.w = w;
			this.h = h;
			this.d = Math.max(0.25f, w * 0.75f);
			this.side = side;
			this.spin = spin;
		}
	}

	private TornBodies() {
	}

	/** Number of bodies whose real model was cut (for tests): {cut models, plain fallbacks}. */
	public static final int[] STATS = new int[2];
	/** Description of the last cut bodies (for tests). */
	public static final List<String> LOG = new ArrayList<>();
	private static final java.util.Map<Integer, Integer> HIDDEN = new java.util.HashMap<>();

	/** A torn player keeps existing until respawn; its whole body must not be drawn next to its halves. */
	public static boolean isHidden(net.minecraft.world.entity.Entity e) {
		return HIDDEN.containsKey(e.getId()) && e instanceof net.minecraft.world.entity.LivingEntity l && l.isDeadOrDying();
	}

	/**
	 * Tears the given entity in two: its own model is cut along the plane given by yaw, so every mob
	 * splits into halves of itself. Falls back to plain halves when the entity is unknown here.
	 */
	public static void spawn(net.minecraft.world.entity.Entity victim, Vec3 feet, float width, float height, float axisYaw) {
		double rad = Math.toRadians(axisYaw);
		Vec3 axis = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
		BodySlicer.Mesh[] meshes = null;
		if (victim != null) {
			try {
				meshes = BodySlicer.slice(victim, axis);
			} catch (RuntimeException ex) {
				org.slf4j.LoggerFactory.getLogger("theboys").warn("could not cut {}", victim, ex);
			}
		}
		if (meshes == null) {
			STATS[1]++;
			LOG.add((victim == null ? "?" : victim.getType().toShortString()) + " fallback");
			spawn(feet, width, height, axisYaw);
			return;
		}
		STATS[0]++;
		LOG.add(victim.getType().toShortString() + " cut " + meshes[0].quads() + "/" + meshes[1].quads() + " quads, " + meshes[1].cuts + " cuts");
		while (LOG.size() > 40) LOG.remove(0);
		if (victim instanceof net.minecraft.world.entity.player.Player) HIDDEN.put(victim.getId(), 0);
		Vec3 origin = victim.position();
		for (int i = 0; i < 2; i++) {
			int side = i == 0 ? -1 : 1;
			BodySlicer.Mesh m = meshes[i];
			Vec3 vel = axis.scale(side * 0.3).add(0, 0.36, 0);
			Half h = new Half(origin.add(m.centre), vel, axisYaw, width / 2, height, side, side * 11f);
			h.mesh = m;
			HALVES.add(h);
		}
		while (HALVES.size() > 60) HALVES.remove(0);
	}

	/** Splits a body of the given size at its feet position along the axis given by yaw (degrees). */
	public static void spawn(Vec3 feet, float width, float height, float axisYaw) {
		double rad = Math.toRadians(axisYaw);
		Vec3 axis = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
		float half = Math.max(0.15f, width / 2);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 start = feet.add(axis.scale(side * half / 2)).add(0, 0.6, 0);
			Vec3 vel = axis.scale(side * 0.32).add(0, 0.38, 0);
			HALVES.add(new Half(start, vel, axisYaw, half, height, side, side * 9f));
		}
		while (HALVES.size() > 40) HALVES.remove(0);
	}

	public static void tick(ClientLevel level) {
		RandomSource rnd = level.getRandom();
		Iterator<Half> it = HALVES.iterator();
		while (it.hasNext()) {
			Half b = it.next();
			b.age++;
			if (b.age > LIFE) {
				it.remove();
				continue;
			}
			b.prevPos = b.pos;
			b.prevRoll = b.roll;
			if (b.mesh != null) {
				tickMesh(level, b, rnd);
				continue;
			}
			if (!b.landed) {
				b.vel = b.vel.add(0, -0.05, 0).scale(0.98);
				Vec3 next = b.pos.add(b.vel);
				BlockPos below = BlockPos.containing(next.x, next.y - 0.01, next.z);
				if (b.vel.y < 0 && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
					next = new Vec3(next.x, below.getY() + 1, next.z);
					b.landed = true;
					b.vel = Vec3.ZERO;
					// the half flops over onto its side
					b.roll = b.side * 90f;
					for (int i = 0; i < 25; i++) {
						level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
								next.x, next.y + 0.1, next.z, (rnd.nextDouble() - 0.5) * 0.4, 0.15, (rnd.nextDouble() - 0.5) * 0.4);
					}
				} else {
					b.roll += b.spin;
				}
				b.pos = next;
			}
			// blood keeps pumping out of the torn side for a while
			if (b.age < 70 && (b.age < 30 || b.age % 2 == 0)) {
				double rad = Math.toRadians(b.yaw);
				Vec3 axis = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
				Vec3 wound = b.pos.add(0, b.h * 0.5, 0).subtract(axis.scale(b.side * b.w * 0.5));
				Vec3 spray = axis.scale(-b.side * 0.25).add(0, 0.25 + rnd.nextDouble() * 0.2, 0);
				int n = b.age < 15 ? 6 : 2;
				for (int i = 0; i < n; i++) {
					level.addParticle(new DustParticleOptions(i % 3 == 0 ? 0x5A0000 : 0x9A0606, 1.3f),
							wound.x + (rnd.nextDouble() - 0.5) * 0.2, wound.y + (rnd.nextDouble() - 0.5) * b.h * 0.6, wound.z + (rnd.nextDouble() - 0.5) * 0.2,
							spray.x + (rnd.nextDouble() - 0.5) * 0.15, spray.y, spray.z + (rnd.nextDouble() - 0.5) * 0.15);
				}
				if (b.age % 4 == 0) {
					level.addParticle(ParticleTypes.DRIPPING_LAVA, wound.x, wound.y, wound.z, 0, 0, 0);
				}
			}
		}
	}

	/** Rotation of a mesh half: it tumbles around the horizontal axis lying in the cutting plane. */
	private static org.joml.Matrix4f rotation(Half b, float roll) {
		double rad = Math.toRadians(b.yaw);
		return new org.joml.Matrix4f().rotation((float) Math.toRadians(roll), (float) Math.cos(rad), 0, (float) Math.sin(rad));
	}

	private static Vec3 rotate(org.joml.Matrix4f m, Vec3 v) {
		org.joml.Vector3f r = m.transformDirection(new org.joml.Vector3f((float) v.x, (float) v.y, (float) v.z));
		return new Vec3(r.x, r.y, r.z);
	}

	private static float lowest(Half b, float roll) {
		org.joml.Matrix4f m = rotation(b, roll);
		float min = Float.MAX_VALUE;
		float[] c = b.mesh.corners;
		for (int i = 0; i < c.length; i += 3) {
			org.joml.Vector3f r = m.transformDirection(new org.joml.Vector3f(c[i], c[i + 1], c[i + 2]));
			min = Math.min(min, r.y);
		}
		return min;
	}

	private static void tickMesh(ClientLevel level, Half b, RandomSource rnd) {
		if (!b.landed) {
			b.vel = b.vel.add(0, -0.05, 0).scale(0.98);
			b.roll += b.spin;
			Vec3 next = b.pos.add(b.vel);
			float low = lowest(b, b.roll);
			BlockPos below = BlockPos.containing(next.x, next.y + low - 0.01, next.z);
			if (b.vel.y < 0 && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				b.landed = true;
				b.vel = Vec3.ZERO;
				// settle with the torn side facing up
				float target = b.side * 90f;
				while (target - b.roll > 180f) target -= 360f;
				while (target - b.roll < -180f) target += 360f;
				b.targetRoll = target;
				for (int i = 0; i < 25; i++) {
					level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
							next.x, below.getY() + 1.1, next.z, (rnd.nextDouble() - 0.5) * 0.4, 0.15, (rnd.nextDouble() - 0.5) * 0.4);
				}
				b.pos = new Vec3(next.x, below.getY() + 1 - low, next.z);
			} else {
				b.pos = next;
			}
		} else {
			b.roll += (b.targetRoll - b.roll) * 0.35f;
			BlockPos below = BlockPos.containing(b.pos.x, b.pos.y + lowest(b, b.roll) - 0.3, b.pos.z);
			double ground = level.getBlockState(below).getCollisionShape(level, below).isEmpty() ? below.getY() : below.getY() + 1;
			b.pos = new Vec3(b.pos.x, ground - lowest(b, b.roll), b.pos.z);
		}
		// blood pumps out of the cut
		if (b.age < 80 && (b.age < 30 || b.age % 2 == 0)) {
			org.joml.Matrix4f m = rotation(b, b.roll);
			Vec3 wound = b.pos.add(rotate(m, b.mesh.wound));
			Vec3 out = rotate(m, b.mesh.woundNormal);
			int n = b.age < 15 ? 7 : 2;
			double reach = Math.max(0.1, b.h * 0.25);
			for (int i = 0; i < n; i++) {
				Vec3 spray = out.scale(0.18 + rnd.nextDouble() * 0.12).add(0, 0.12 + rnd.nextDouble() * 0.2, 0);
				level.addParticle(new DustParticleOptions(i % 3 == 0 ? 0x5A0000 : 0x9A0606, 1.3f),
						wound.x + (rnd.nextDouble() - 0.5) * reach, wound.y + (rnd.nextDouble() - 0.5) * reach, wound.z + (rnd.nextDouble() - 0.5) * reach,
						spray.x + (rnd.nextDouble() - 0.5) * 0.12, spray.y, spray.z + (rnd.nextDouble() - 0.5) * 0.12);
			}
			if (b.age % 4 == 0) {
				level.addParticle(ParticleTypes.DRIPPING_LAVA, wound.x, wound.y, wound.z, 0, 0, 0);
			}
		}
	}

	/** The halves cut from real models; each one is drawn with its mob's own texture. */
	public static List<Half> meshHalves() {
		List<Half> out = new ArrayList<>();
		for (Half h : HALVES) if (h.mesh != null) out.add(h);
		return out;
	}

	public static Identifier texture(Half h) {
		return h.mesh.texture;
	}

	private static PoseStack.Pose placed(Half b, PoseStack.Pose pose, Vec3 cam, float pt) {
		Vec3 p = b.prevPos.add(b.pos.subtract(b.prevPos).scale(pt));
		float roll = b.prevRoll + (b.roll - b.prevRoll) * pt;
		float sink = b.age > LIFE - 40 ? (b.age - (LIFE - 40) + pt) / 40f * 0.5f : 0f;
		PoseStack ps = new PoseStack();
		ps.last().pose().set(pose.pose());
		ps.last().normal().set(pose.normal());
		ps.translate((float) (p.x - cam.x), (float) (p.y - cam.y - sink), (float) (p.z - cam.z));
		ps.mulPose(rotation(b, roll));
		return ps.last();
	}

	private static int light(Half b, ClientLevel level) {
		return LightCoordsUtil.getLightCoords(level, BlockPos.containing(b.pos.x, b.pos.y + 0.2, b.pos.z));
	}

	/** The skin of a cut half, with the mob's own texture. */
	public static void drawSkin(Half b, VertexConsumer buf, PoseStack.Pose pose, Vec3 cam, float pt, ClientLevel level) {
		quads(buf, placed(b, pose, cam, pt), b.mesh.skin, light(b, level), 0xFFE0D0D0);
	}

	private static void quads(VertexConsumer buf, PoseStack.Pose pose, float[] d, int light, int color) {
		for (int i = 0; i + BodySlicer.STRIDE <= d.length; i += BodySlicer.STRIDE) {
			buf.addVertex(pose, d[i], d[i + 1], d[i + 2]).setColor(color).setUv(d[i + 3], d[i + 4])
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, d[i + 5], d[i + 6], d[i + 7]);
		}
	}

	public static boolean isEmpty() {
		return HALVES.isEmpty();
	}

	/** Draws all halves with the gore texture: bloody outside, torn flesh and bone on the inner face. */
	public static void draw(VertexConsumer buf, PoseStack.Pose pose, Vec3 cam, float pt, ClientLevel level) {
		for (Half b : HALVES) {
			if (b.mesh != null) {
				quads(buf, placed(b, pose, cam, pt), b.mesh.gore, light(b, level), 0xFFFFFFFF);
				continue;
			}
			Vec3 p = b.prevPos.add(b.pos.subtract(b.prevPos).scale(pt));
			float roll = b.prevRoll + (b.roll - b.prevRoll) * pt;
			float sink = b.age > LIFE - 40 ? (b.age - (LIFE - 40) + pt) / 40f * 0.4f : 0f;
			int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(p.x, p.y + 0.3, p.z));
			PoseStack ps = new PoseStack();
			ps.last().pose().set(pose.pose());
			ps.last().normal().set(pose.normal());
			ps.translate((float) (p.x - cam.x), (float) (p.y - cam.y - sink), (float) (p.z - cam.z));
			ps.mulPose(new org.joml.Matrix4f().rotationY((float) Math.toRadians(-b.yaw)));
			ps.mulPose(new org.joml.Matrix4f().rotationZ((float) Math.toRadians(roll)));
			box(buf, ps.last(), b, light);
		}
	}

	/** A box w (along the tearing axis, x) * h * d; the face towards the other half shows the torn side. */
	private static void box(VertexConsumer b, PoseStack.Pose pose, Half half, int light) {
		float x0 = half.side < 0 ? -half.w : 0, x1 = half.side < 0 ? 0 : half.w;
		float y0 = 0, y1 = half.h;
		float z0 = -half.d / 2, z1 = half.d / 2;
		// outside: left half of the texture; torn side: right half
		float ou0 = 0, ou1 = 0.5f, iu0 = 0.5f, iu1 = 1f;
		boolean innerIsMinX = half.side > 0;
		face(b, pose, light, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0, innerIsMinX ? iu0 : ou0, innerIsMinX ? iu1 : ou1);
		face(b, pose, light, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1, 0, 0, innerIsMinX ? ou0 : iu0, innerIsMinX ? ou1 : iu1);
		face(b, pose, light, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0, 0, 1, ou0, ou1);
		face(b, pose, light, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0, 0, -1, ou0, ou1);
		face(b, pose, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, ou0, ou1);
		face(b, pose, light, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, ou0, ou1);
	}

	private static void face(VertexConsumer b, PoseStack.Pose pose, int light,
			float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
			float nx, float ny, float nz, float u0, float u1) {
		v(b, pose, ax, ay, az, u0, 1, nx, ny, nz, light);
		v(b, pose, bx, by, bz, u0, 0, nx, ny, nz, light);
		v(b, pose, cx, cy, cz, u1, 0, nx, ny, nz, light);
		v(b, pose, dx, dy, dz, u1, 1, nx, ny, nz, light);
		// back side so it is never culled
		v(b, pose, dx, dy, dz, u1, 1, -nx, -ny, -nz, light);
		v(b, pose, cx, cy, cz, u1, 0, -nx, -ny, -nz, light);
		v(b, pose, bx, by, bz, u0, 0, -nx, -ny, -nz, light);
		v(b, pose, ax, ay, az, u0, 1, -nx, -ny, -nz, light);
	}

	private static void v(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float vv, float nx, float ny, float nz, int light) {
		b.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}
}
